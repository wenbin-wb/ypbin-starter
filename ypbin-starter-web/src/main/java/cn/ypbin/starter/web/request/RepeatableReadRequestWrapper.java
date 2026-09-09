/*
 * Copyright (c) 2024-present ypbin-starter authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.ypbin.starter.web.request;

import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.exception.GlobalErrorCode;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 可重复读取请求体的包装器。
 *
 * <p>Servlet 的请求 InputStream 只能读一次。本包装器在构造时缓存请求体字节，使签名校验、
 * 日志采集、Controller 等多方都能各自完整读取，避免"body 被上游读走后下游读为空"。作为
 * 通用基础能力放在 web 模块，供 XSS、签名等下游复用（见 {@code RepeatableReadRequestFilter}）。</p>
 *
 * <p>缓存受字节上限约束（默认 {@link #DEFAULT_MAX_BODY_BYTES}，10MB，可配
 * {@code ypbin.web.repeatable-read.max-body-bytes}）：读取时只探测上限 +1 字节，未超限的请求
 * 全程不受影响；超限则记录 error 并抛 {@link BusinessException}（413 语义）拒绝继续读取，
 * 防止超大请求体被整段读入内存造成资源耗尽。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class RepeatableReadRequestWrapper extends HttpServletRequestWrapper {

    private static final Logger log = LoggerFactory.getLogger(RepeatableReadRequestWrapper.class);

    /** 可缓存请求体的默认最大字节数：10MB */
    public static final long DEFAULT_MAX_BODY_BYTES = 10L * 1024 * 1024;

    private final byte[] cachedBody;

    /**
     * 以默认上限（{@link #DEFAULT_MAX_BODY_BYTES}）缓存请求体。
     *
     * @param request 原始请求
     * @throws IOException 读取请求体失败时抛出
     */
    public RepeatableReadRequestWrapper(HttpServletRequest request) throws IOException {
        this(request, DEFAULT_MAX_BODY_BYTES);
    }

    /**
     * 以指定上限缓存请求体。
     *
     * @param request      原始请求
     * @param maxBodyBytes 允许缓存的最大字节数（须为正数）；超限时拒绝读取并抛异常
     * @throws IOException 读取请求体失败时抛出
     */
    public RepeatableReadRequestWrapper(HttpServletRequest request, long maxBodyBytes) throws IOException {
        super(request);
        this.cachedBody = readBodyBounded(request, maxBodyBytes);
    }

    private static byte[] readBodyBounded(HttpServletRequest request, long maxBodyBytes)
        throws IOException {
        if (maxBodyBytes <= 0) {
            throw new IllegalArgumentException("可重复读缓存上限必须为正数：" + maxBodyBytes);
        }
        ServletInputStream input = request.getInputStream();
        // 只多读 1 字节探测是否超限：未超限时行为与原 readAllBytes 完全一致，请求解析流程不受影响
        long probeLength = maxBodyBytes + 1L;
        byte[] body;
        if (probeLength > Integer.MAX_VALUE) {
            // 配置上限超过 int 表示范围时退化为全量读取（实际配置不会达到该值，此处仅为防御）
            body = input.readAllBytes();
        } else {
            body = input.readNBytes((int) probeLength);
        }
        if (body.length > maxBodyBytes) {
            log.error("[ypbin-starter] 请求体超过可重复读缓存上限（{} 字节），拒绝缓存并中止读取：{} {}",
                maxBodyBytes, request.getMethod(), request.getRequestURI());
            throw new BusinessException(GlobalErrorCode.PAYLOAD_TOO_LARGE,
                "请求体超过可重复读缓存上限（" + maxBodyBytes + " 字节）");
        }
        return body;
    }

    /**
     * 获取缓存的请求体字符串（UTF-8）。
     *
     * @return 请求体内容
     */
    public String getBodyAsString() {
        return new String(cachedBody, StandardCharsets.UTF_8);
    }

    /**
     * 获取缓存的请求体字节。
     *
     * @return 请求体字节数组
     */
    public byte[] getBodyAsBytes() {
        return cachedBody;
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream bais = new ByteArrayInputStream(cachedBody);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return bais.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                // 同步读取，无需异步监听
            }

            @Override
            public int read() {
                return bais.read();
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(cachedBody), StandardCharsets.UTF_8));
    }
}

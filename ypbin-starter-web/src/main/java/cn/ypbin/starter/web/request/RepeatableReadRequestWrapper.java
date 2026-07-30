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

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 可重复读取请求体的包装器。
 *
 * <p>Servlet 的请求 InputStream 只能读一次。本包装器在构造时缓存请求体字节，使签名校验、
 * 日志采集、Controller 等多方都能各自完整读取，避免"body 被上游读走后下游读为空"。作为
 * 通用基础能力放在 web 模块，供 XSS、签名等下游复用（见 {@code RepeatableReadRequestFilter}）。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class RepeatableReadRequestWrapper extends HttpServletRequestWrapper {

    private final byte[] cachedBody;

    public RepeatableReadRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        this.cachedBody = request.getInputStream().readAllBytes();
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

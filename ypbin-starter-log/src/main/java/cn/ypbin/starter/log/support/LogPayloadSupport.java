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
package cn.ypbin.starter.log.support;

import cn.ypbin.starter.core.blocklist.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartRequest;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * 操作日志（{@link LogCollector}）与访问日志（{@code AccessLogAspect}）共用的请求载荷判定。
 *
 * <p><strong>为什么单独成类</strong>：这两块逻辑原先在两个类里各写一份，且已经分叉——访问日志只认
 * 2 个代理头、排除 8 类参数，操作日志认 4 个代理头、排除 11 类，同一个请求可能在两种日志里出现
 * 不同 IP，同一个参数类型在一边记录、另一边丢弃。收敛到一处后新增规则只改这里。</p>
 *
 * <p>本类只做无状态的纯判定，不持有请求上下文，可安全用于任意线程。</p>
 *
 * @author wenbin
 * @since 2026-10-31
 */
public final class LogPayloadSupport {

    /** 兼容旧调用：不信任任何转发头，仅使用直连地址。 */
    private static final ClientIpResolver DEFAULT_CLIENT_IP_RESOLVER = new ClientIpResolver(List.of());

    private LogPayloadSupport() {
    }

    /**
     * 解析客户端 IP（兼容旧调用）。
     *
     * <p>默认不信任任何转发头，避免把客户端可伪造的 {@code X-Forwarded-For} 写入日志。需要采信可信代理
     * 追加的地址时，使用 {@link #resolveIp(HttpServletRequest, ClientIpResolver)}，并配置
     * {@code ypbin.security.blocklist.trusted-proxies}。</p>
     *
     * @param request 当前请求
     * @return 客户端 IP
     * @deprecated 使用 {@link #resolveIp(HttpServletRequest, ClientIpResolver)} 以按可信代理规则解析
     */
    @Deprecated(since = "4.4.0", forRemoval = false)
    public static String resolveIp(HttpServletRequest request) {
        return resolveIp(request, DEFAULT_CLIENT_IP_RESOLVER);
    }

    /**
     * 解析客户端 IP，与黑名单过滤器共用同一套可信代理规则：只有直连地址命中可信代理时，才按
     * {@code X-Forwarded-For} / {@code X-Real-IP} 从右往左取第一个不可信地址；否则取直连地址。
     *
     * @param request  当前请求
     * @param resolver 客户端 IP 解析器
     * @return 客户端 IP
     */
    public static String resolveIp(HttpServletRequest request, ClientIpResolver resolver) {
        return resolver.resolve(request.getRemoteAddr(), request.getHeader(ClientIpResolver.HEADER_FORWARDED_FOR),
            request.getHeader(ClientIpResolver.HEADER_REAL_IP));
    }

    /**
     * 判断单个入参是否可安全写入日志。
     *
     * <p>排除请求/响应/会话/上传文件/流/大对象等：它们要么不可序列化，要么只能读一次（读掉会破坏业务），
     * 要么体积不可控。</p>
     *
     * @param arg 方法入参（可为 {@code null}）
     * @return 可记录返回 {@code true}
     */
    public static boolean isLoggable(@Nullable Object arg) {
        if (arg == null) {
            return false;
        }
        return !(arg instanceof HttpServletRequest)
            && !(arg instanceof HttpServletResponse)
            && !(arg instanceof MultipartFile)
            && !(arg instanceof MultipartFile[])
            && !(arg instanceof HttpSession)
            && !(arg instanceof MultipartRequest)
            && !(arg instanceof InputStream)
            && !(arg instanceof OutputStream)
            && !(arg instanceof Resource)
            && !(arg instanceof byte[])
            && !(arg instanceof StreamingResponseBody);
    }
}

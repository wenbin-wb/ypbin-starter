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
package cn.ypbin.starter.core.blocklist;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 客户端 IP 解析器。
 *
 * <p><b>默认不信任转发头</b>：只有直连地址命中
 * {@code ypbin.security.blocklist.trusted-proxies}（精确 IP 或 CIDR）时才采用
 * {@code X-Forwarded-For} / {@code X-Real-IP}。</p>
 *
 * <p>取值口径为「从右往左跳过可信代理，取第一个不可信地址」：最左侧一跳是客户端可任意伪造的，
 * 不能直接采用。所有候选取值都必须是 IP 字面量，域名/主机名一律忽略（不触发 DNS 解析）。</p>
 *
 * @author wenbin
 * @since 2026-11-04
 */
public class ClientIpResolver {

    /** {@code X-Forwarded-For} 请求头。 */
    public static final String HEADER_FORWARDED_FOR = "X-Forwarded-For";

    /** {@code X-Real-IP} 请求头（nginx 常用）。 */
    public static final String HEADER_REAL_IP = "X-Real-IP";

    private static final Logger log = LoggerFactory.getLogger(ClientIpResolver.class);

    private static final String UNKNOWN = "unknown";

    private final List<String> trustedProxies;

    private final AtomicBoolean untrustedForwardingWarned = new AtomicBoolean();

    public ClientIpResolver(List<String> trustedProxies) {
        this.trustedProxies = List.copyOf(trustedProxies);
    }

    /**
     * 解析客户端 IP（兼容旧调用，不含 {@code X-Real-IP}）。
     *
     * @param remoteAddress 直连地址（TCP 对端）
     * @param forwardedFor  {@code X-Forwarded-For} 头（可为空）
     * @return 规范化后的客户端 IP；无法解析时返回 {@code unknown}
     */
    public String resolve(@Nullable String remoteAddress, @Nullable String forwardedFor) {
        return resolve(remoteAddress, forwardedFor, null);
    }

    /**
     * 解析客户端 IP。
     *
     * @param remoteAddress 直连地址（TCP 对端）
     * @param forwardedFor  {@code X-Forwarded-For} 头（可为空）
     * @param realIp        {@code X-Real-IP} 头（可为空；仅在 XFF 缺失时使用）
     * @return 规范化后的客户端 IP；无法解析时返回 {@code unknown}
     */
    public String resolve(@Nullable String remoteAddress, @Nullable String forwardedFor,
                          @Nullable String realIp) {
        String remote = IpAddress.normalize(remoteAddress);
        if (remote == null || remote.isEmpty()) {
            return UNKNOWN;
        }
        boolean hasForwarded = forwardedFor != null && !forwardedFor.isBlank();
        boolean hasRealIp = realIp != null && !realIp.isBlank();
        if (trustedProxies.isEmpty() && (hasForwarded || hasRealIp)) {
            warnUntrustedForwarding();
        }
        if (!isTrustedProxy(remote) || (!hasForwarded && !hasRealIp)) {
            return remote;
        }
        List<String> hops = new ArrayList<>();
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            for (String hop : forwardedFor.split(",")) {
                hops.add(hop);
            }
        } else if (realIp != null) {
            hops.add(realIp);
        }
        String leftmost = null;
        for (int i = hops.size() - 1; i >= 0; i--) {
            String hop = IpAddress.normalize(hops.get(i));
            if (hop == null || hop.isEmpty()) {
                continue;
            }
            if (leftmost == null) {
                leftmost = hop;
            }
            if (!isTrustedProxy(hop)) {
                return hop;
            }
        }
        return leftmost == null ? remote : leftmost;
    }

    private boolean isTrustedProxy(String remote) {
        for (String proxy : trustedProxies) {
            if (proxy != null && IpAddress.matches(proxy, remote)) {
                return true;
            }
        }
        return false;
    }

    private void warnUntrustedForwarding() {
        if (untrustedForwardingWarned.compareAndSet(false, true)) {
            log.warn("[ypbin-starter] 检测到 {} / {} 转发头，但 ypbin.security.blocklist.trusted-proxies 为空，"
                    + "黑名单将按直连地址判断；反向代理/LB/K8s Ingress 部署请配置可信代理网段",
                HEADER_FORWARDED_FOR, HEADER_REAL_IP);
        }
    }

}

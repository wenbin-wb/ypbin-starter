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

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 客户端 IP 解析器。
 *
 * <p><b>默认不信任 {@code X-Forwarded-For}</b>：只有直连地址命中
 * {@code ypbin.security.blocklist.trusted-proxies}（精确 IP 或 CIDR）时才采用转发链的第一跳。
 * 否则客户端可以自己伪造该头，黑名单会被任意绕过，或反过来把无辜 IP 拉黑。</p>
 *
 * @author wenbin
 * @since 2026-11-04
 */
public class ClientIpResolver {

    private static final String UNKNOWN = "unknown";

    private final List<String> trustedProxies;

    public ClientIpResolver(List<String> trustedProxies) {
        this.trustedProxies = List.copyOf(trustedProxies);
    }

    /**
     * 解析客户端 IP。
     *
     * @param remoteAddress 直连地址（TCP 对端）
     * @param forwardedFor  {@code X-Forwarded-For} 头（可为空）
     * @return 规范化后的客户端 IP；无法解析时返回 {@code unknown}
     */
    public String resolve(@Nullable String remoteAddress, @Nullable String forwardedFor) {
        String remote = IpAddress.normalize(remoteAddress);
        if (remote == null || remote.isEmpty()) {
            return UNKNOWN;
        }
        if (forwardedFor == null || forwardedFor.isBlank() || !isTrustedProxy(remote)) {
            return remote;
        }
        String first = forwardedFor.split(",")[0].trim();
        String forwarded = IpAddress.normalize(first);
        return forwarded == null || forwarded.isEmpty() ? remote : forwarded;
    }

    private boolean isTrustedProxy(String remote) {
        for (String proxy : trustedProxies) {
            if (proxy != null && IpAddress.matches(proxy, remote)) {
                return true;
            }
        }
        return false;
    }
}

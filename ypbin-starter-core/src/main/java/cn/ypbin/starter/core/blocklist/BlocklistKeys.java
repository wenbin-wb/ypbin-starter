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

/**
 * 黑名单在 Redis 中的 key 契约。
 *
 * <p><b>Redis Cluster 约束</b>：规则 Hash、规则临时键与自动封禁索引都带 {@code {rules}} hash tag；
 * 单个 IP 的违规计数键与封禁键都带 {@code {ip:<ip>}} hash tag。这样 {@code RENAME} 与 Lua 多 key
 * 脚本里的 key 落在同一 slot，不会触发 {@code CROSSSLOT}。</p>
 *
 * <p>IP 会先规范化为字面量；非法值统一落到 {@code unknown}，避免把任意文本拼进 hash tag。</p>
 *
 * @author wenbin
 * @since 2026-11-04
 */
public final class BlocklistKeys {

    /** 规则 Hash 键（field = 规则 ID，value = 编码后的规则）。 */
    public static final String RULES = "ypbin:security:blocklist:{rules}";

    /** 自动封禁索引 Hash 键（field = IP，value = 到期时间|原因）；与规则同 slot。 */
    public static final String BAN_INDEX = "ypbin:security:blocklist:{rules}:ban-index";

    private static final String IP_KEY_PREFIX = "ypbin:security:blocklist:{ip:";

    private static final String BAN_SUFFIX = "}:ban";

    private static final String VIOLATION_SUFFIX = "}:violation";

    private static final String UNKNOWN_IP = "unknown";

    private BlocklistKeys() {
    }

    /**
     * 自动封禁键。
     *
     * @param clientIp 客户端 IP
     * @return Redis key
     */
    public static String ban(String clientIp) {
        return IP_KEY_PREFIX + canonicalIp(clientIp) + BAN_SUFFIX;
    }

    /**
     * 违规计数键。
     *
     * @param clientIp 客户端 IP
     * @return Redis key
     */
    public static String violation(String clientIp) {
        return IP_KEY_PREFIX + canonicalIp(clientIp) + VIOLATION_SUFFIX;
    }

    /**
     * 规范化用于 key 的 IP；非法值统一为 {@code unknown}，防止 hash tag 注入。
     *
     * @param clientIp 客户端 IP
     * @return 规范化 IP 或 {@code unknown}
     */
    public static String canonicalIp(String clientIp) {
        String normalized = IpAddress.normalize(clientIp);
        return normalized == null || normalized.isEmpty() ? UNKNOWN_IP : normalized;
    }
}

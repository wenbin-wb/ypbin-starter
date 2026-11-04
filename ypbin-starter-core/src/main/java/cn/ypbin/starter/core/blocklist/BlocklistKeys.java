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
 * <p>Servlet 侧与网关侧必须读写同一组 key，集中在此避免两处字面量漂移。</p>
 *
 * @author wenbin
 * @since 2026-11-04
 */
public final class BlocklistKeys {

    /** 规则 Hash 键（field = 规则 ID，value = 编码后的规则）。 */
    public static final String RULES = "ypbin:security:blocklist:rules";

    /** 自动封禁键前缀（完整 key 后缀为客户端 IP）。 */
    public static final String BAN_PREFIX = "ypbin:security:blocklist:ban:";

    /** 违规计数键前缀（完整 key 后缀为客户端 IP）。 */
    public static final String VIOLATION_PREFIX = "ypbin:security:blocklist:violation:";

    private BlocklistKeys() {
    }

    /**
     * 自动封禁键。
     *
     * @param clientIp 客户端 IP
     * @return Redis key
     */
    public static String ban(String clientIp) {
        return BAN_PREFIX + clientIp;
    }

    /**
     * 违规计数键。
     *
     * @param clientIp 客户端 IP
     * @return Redis key
     */
    public static String violation(String clientIp) {
        return VIOLATION_PREFIX + clientIp;
    }
}

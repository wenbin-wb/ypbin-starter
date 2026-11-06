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

import org.jspecify.annotations.Nullable;

/**
 * 自动封禁的地址准入判断：三个计数入口（登录失败旁路、servlet 429 计数、网关 429 计数）共用，
 * 避免各自实现导致口径分叉。
 *
 * <p>反代未配 {@code trusted-proxies} 时，所有终端用户解析出的都是代理地址（内网/回环）。
 * 若这种地址参与计数，攻击者只要刷满阈值就能把代理自身写进封禁表，形成「全站每请求 403」的
 * 自伤式 DoS。故默认只对公网单点地址计数，纯内网部署可显式开启
 * {@code auto-ban.allow-non-public-ip=true}。</p>
 *
 * @author wenbin
 * @since 2026-11-06
 */
public final class AutoBanIpGuard {

    private AutoBanIpGuard() {
    }

    /**
     * 该地址是否应跳过自动封禁计数。
     *
     * @param clientIp         客户端地址（可为空）
     * @param allowNonPublicIp 是否允许内网/回环/保留地址参与计数
     * @return {@code true} 表示跳过计数
     */
    public static boolean shouldSkip(@Nullable String clientIp, boolean allowNonPublicIp) {
        if (allowNonPublicIp) {
            return false;
        }
        return clientIp == null || clientIp.isBlank() || !IpAddress.isPublic(clientIp);
    }
}

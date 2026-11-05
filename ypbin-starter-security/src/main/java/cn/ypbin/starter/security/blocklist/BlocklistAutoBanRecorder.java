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
package cn.ypbin.starter.security.blocklist;

import cn.ypbin.starter.core.blocklist.BlocklistMatcher;
import cn.ypbin.starter.core.blocklist.BlocklistProperties;
import cn.ypbin.starter.core.blocklist.BlocklistStore;
import cn.ypbin.starter.core.blocklist.IpAddress;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 登录失败向黑名单自动封禁计数的旁路入口。
 *
 * <p>本仓业务响应恒为 HTTP 200，登录失败是 {@code 200 + R.code=401}，过滤器读传输层状态码
 * 看不到；因此由登录失败的业务点显式调用本类。{@link cn.ypbin.starter.security.password.lock.PasswordAttemptLimiter}
 * 已在每次失败时接入，宿主自定义登录流程时也可直接注入本类调用 {@link #recordLoginFailure(String)}。</p>
 *
 * <p>计数失败只记录 WARN，不影响登录失败本身的返回——自动封禁是旁路增强，不能反向阻断业务。</p>
 *
 * @author wenbin
 * @since 2026-11-04
 */
public class BlocklistAutoBanRecorder {

    private static final Logger log = LoggerFactory.getLogger(BlocklistAutoBanRecorder.class);

    private static final String LOGIN_FAILURE_REASON = "login failure";

    private final BlocklistProperties properties;

    private final BlocklistStore store;

    private final BlocklistMatcher matcher = new BlocklistMatcher();

    public BlocklistAutoBanRecorder(BlocklistProperties properties, BlocklistStore store) {
        this.properties = properties;
        this.store = store;
    }

    /**
     * 记录一次登录失败；仅当黑名单与自动封禁均开启、客户端 IP 合法且不在
     * {@code exclude-ips} 中时计数。
     *
     * <p>排除判断与 {@code AccessBlocklistFilter} 同口径：被宿主显式排除的网关/内网地址
     * 不会因登录失败旁路被写入封禁表，避免「过滤器放行、计数端仍封禁」的分叉。</p>
     *
     * @param clientIp 客户端 IP（可为空）
     */
    public void recordLoginFailure(@Nullable String clientIp) {
        if (!properties.isEnabled() || !properties.getAutoBan().isEnabled()
            || clientIp == null || clientIp.isBlank() || !IpAddress.isValidIp(clientIp)
            || matcher.isExcludedIp(clientIp, properties.getExcludeIps())) {
            return;
        }
        BlocklistProperties.AutoBan autoBan = properties.getAutoBan();
        try {
            store.recordViolation(clientIp, Duration.ofSeconds(autoBan.getWindowSeconds()),
                autoBan.getThreshold(), Duration.ofSeconds(autoBan.getBanSeconds()), LOGIN_FAILURE_REASON);
        } catch (RuntimeException ex) {
            log.warn("[ypbin-starter] 登录失败计入访问黑名单失败：ip={}", clientIp, ex);
        }
    }
}

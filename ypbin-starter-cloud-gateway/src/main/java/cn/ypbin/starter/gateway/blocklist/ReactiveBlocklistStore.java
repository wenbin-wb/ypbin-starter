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
package cn.ypbin.starter.gateway.blocklist;

import cn.ypbin.starter.core.blocklist.BlocklistRule;
import java.time.Duration;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 网关侧响应式黑名单存储端口。
 *
 * <p>网关是 WebFlux 运行时，不能使用阻塞的 {@code StringRedisTemplate}；本端口与 Servlet 侧
 * {@code BlocklistStore} 共享同一套规则模型与 Redis key 契约。</p>
 *
 * @author wenbin
 * @since 2026-11-04
 */
public interface ReactiveBlocklistStore {

    /**
     * 读取动态规则（带本地缓存）。
     *
     * @return 规则列表
     */
    Mono<List<BlocklistRule>> rules();

    /**
     * 判断 IP 是否处于自动封禁中。
     *
     * @param clientIp 客户端 IP
     * @return 是否封禁
     */
    Mono<Boolean> isBanned(String clientIp);

    /**
     * 记录一次违规；达到阈值时自动封禁。
     *
     * @param clientIp    客户端 IP
     * @param window      计数窗口
     * @param threshold   阈值
     * @param banDuration 封禁时长
     * @param reason      触发原因
     * @return 窗口内累计次数
     */
    Mono<Long> recordViolation(String clientIp, Duration window, int threshold, Duration banDuration, String reason);
}

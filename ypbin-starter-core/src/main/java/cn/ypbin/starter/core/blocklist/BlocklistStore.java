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

import java.time.Duration;
import java.util.Collection;
import java.util.List;

/**
 * 黑名单规则与自动封禁状态的存储端口。
 *
 * <p>两个实现：内存（单机兜底）与 Redis（多节点共享，由 starter-security 在存在
 * {@code StringRedisTemplate} 时装配）。管理端只依赖本接口发布规则，不关心底层存储。</p>
 *
 * @author wenbin
 * @since 2026-11-04
 */
public interface BlocklistStore {

    /**
     * 读取当前动态规则。
     *
     * @return 规则列表（非 null；读取失败时应返回最近一次成功快照或空列表）
     */
    List<BlocklistRule> rules();

    /**
     * 全量替换动态规则。
     *
     * @param rules 新规则集合
     */
    void replaceRules(Collection<BlocklistRule> rules);

    /**
     * 判断 IP 是否处于自动封禁中。
     *
     * @param clientIp 客户端 IP
     * @return 被封禁返回 {@code true}
     */
    boolean isBanned(String clientIp);

    /**
     * 手动封禁一个 IP。
     *
     * @param clientIp 客户端 IP
     * @param duration 封禁时长
     * @param reason   原因
     */
    void ban(String clientIp, Duration duration, String reason);

    /**
     * 解除封禁。
     *
     * @param clientIp 客户端 IP
     */
    void unban(String clientIp);

    /**
     * 记录一次违规；达到阈值时自动封禁。
     *
     * @param clientIp    客户端 IP
     * @param window      计数窗口
     * @param threshold   阈值
     * @param banDuration 封禁时长
     * @param reason      触发原因
     * @return 窗口内累计次数；存储不可用时返回 0
     */
    long recordViolation(String clientIp, Duration window, int threshold, Duration banDuration, String reason);

    /**
     * 列出当前自动封禁记录。
     *
     * @return 自动封禁列表（非 null）
     */
    List<AutoBanEntry> autoBans();
}

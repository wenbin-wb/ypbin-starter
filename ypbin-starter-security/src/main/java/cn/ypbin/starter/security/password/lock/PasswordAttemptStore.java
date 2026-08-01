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
package cn.ypbin.starter.security.password.lock;

import java.time.Duration;

/**
 * 密码错误计数存储扩展点。
 *
 * <p>记录每个账号的连续登录失败次数，锁定时长即计数键的过期时间，到期自动解锁。默认内存实现，
 * 分布式场景由 Redis 实现覆盖。首次失败时设置过期时间，后续失败递增计数但不重置过期，
 * 保证锁定窗口从首次失败开始计算。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public interface PasswordAttemptStore {

    /**
     * 递增失败计数并返回递增后的当前值。
     *
     * <p>首次失败时按 {@code window}（观察窗口）设置过期；当计数达到 {@code threshold}（锁定阈值）时，
     * 用 {@code lockDuration}（满额锁定时长）刷新过期时间——确保锁定从"触发锁定的那一刻"起足额生效，
     * 而非被观察窗口的剩余 TTL 提前放行。</p>
     *
     * @param key          计数键
     * @param window       观察窗口时长（首次失败时生效）
     * @param threshold    锁定阈值
     * @param lockDuration 满额锁定时长（达到阈值时刷新为此时长）
     * @return 递增后的失败次数
     */
    long increment(String key, Duration window, int threshold, Duration lockDuration);

    /**
     * 获取当前失败计数。
     *
     * @param key 计数键
     * @return 当前失败次数，无记录为 0
     */
    long get(String key);

    /**
     * 获取计数键剩余存活时间（秒），即距离自动解锁的剩余时间。
     *
     * @param key 计数键
     * @return 剩余秒数，无记录或已过期为 0
     */
    long getTimeToLiveSeconds(String key);

    /**
     * 清除失败计数（登录成功或手动解锁时调用）。
     *
     * @param key 计数键
     */
    void reset(String key);

    /**
     * 按键前缀批量清除失败计数（管理员按账号解锁其全部维度时调用）。
     *
     * @param keyPrefix 键前缀
     */
    void resetByPrefix(String keyPrefix);
}

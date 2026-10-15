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
package cn.ypbin.starter.tools.lock;

/**
 * 租约锁存储的 SPI。
 *
 * <p>把「原子比较并写」这一步抽出来，上层 {@link LeaseLockService} 的租约/续期/释放逻辑
 * 就与具体介质无关：Redis 用 Lua 保证原子，单实例回退用 {@code ConcurrentHashMap.compute} 保证原子。
 * 上层逻辑因此可以在没有 Redis 的环境里被完整测到。</p>
 *
 * <p><b>值的形态</b>：{@code token@expireAtMillis}。携带到期时刻是为了让「抢一个已过期的锁」
 * 也能在**一条原子命令**里完成，而不是「先看后抢」（那是竞态）。</p>
 *
 * @author wenbin
 * @since 2026-10-15
 */
interface LeaseLockStore {

    /**
     * 原子地：锁不存在、或已过期（{@code expireAtMillis <= nowMillis}）时写入并返回 {@code true}；
     * 否则保持原值返回 {@code false}。
     *
     * @param key            锁键
     * @param token          持有者标识（本次获取唯一）
     * @param expireAtMillis 新的到期时刻（epoch 毫秒）
     * @param nowMillis      判定「是否已过期」用的当前时刻（可注入时钟）
     * @param ttlMillis      存储层兜底 TTL（防止应用时钟异常导致键永久残留）
     * @return 是否抢到
     */
    boolean tryLock(String key, String token, long expireAtMillis, long nowMillis, long ttlMillis);

    /**
     * 原子地：值仍属于 {@code token} 时把到期时刻推后。
     *
     * @param key               锁键
     * @param token             持有者标识
     * @param newExpireAtMillis 新的到期时刻（epoch 毫秒）
     * @param ttlMillis         存储层兜底 TTL
     * @return 是否续期成功（锁已不属于自己时返回 {@code false}）
     */
    boolean renew(String key, String token, long newExpireAtMillis, long ttlMillis);

    /**
     * 原子地：值仍属于 {@code token} 时删除。
     *
     * @param key   锁键
     * @param token 持有者标识
     * @return 是否真的删掉了自己的锁
     */
    boolean unlock(String key, String token);
}

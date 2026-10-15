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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 进程内租约锁存储（单实例回退实现）。
 *
 * <p><b>什么时候用它</b>：部署里没有 Redis（没有 {@code StringRedisTemplate} bean）时。
 * 它给出**只覆盖单实例**的语义——多副本仍会重复执行。选它而不是启动失败，是因为
 * 「没有 Redis 就起不来」比「多副本可能重复跑一次幂等任务」代价大得多；
 * 但装配层会打 WARN 把这条边界显式暴露出来，绝不静默。</p>
 *
 * <p><b>为什么单元测试也用它</b>：到期判定用的是调用方传入的 {@code nowMillis}（可注入时钟），
 * 因此 TTL 到期、续期、释放这些时序语义都能在不真等、不依赖 Redis 的情况下被逐条验证。</p>
 *
 * @author wenbin
 * @since 2026-10-15
 */
class InMemoryLeaseLockStore implements LeaseLockStore {

    private final Map<String, Entry> locks = new ConcurrentHashMap<>();

    @Override
    public boolean tryLock(String key, String token, long expireAtMillis, long nowMillis, long ttlMillis) {
        AtomicBoolean acquired = new AtomicBoolean(false);
        locks.compute(key, (k, existing) -> {
            if (existing == null || existing.expireAtMillis() <= nowMillis) {
                acquired.set(true);
                return new Entry(token, expireAtMillis);
            }
            return existing;
        });
        return acquired.get();
    }

    @Override
    public boolean renew(String key, String token, long newExpireAtMillis, long ttlMillis) {
        AtomicBoolean renewed = new AtomicBoolean(false);
        locks.computeIfPresent(key, (k, existing) -> {
            if (existing.token().equals(token)) {
                renewed.set(true);
                return new Entry(token, newExpireAtMillis);
            }
            return existing;
        });
        return renewed.get();
    }

    @Override
    public boolean unlock(String key, String token) {
        AtomicBoolean removed = new AtomicBoolean(false);
        locks.computeIfPresent(key, (k, existing) -> {
            if (existing.token().equals(token)) {
                removed.set(true);
                return null;
            }
            return existing;
        });
        return removed.get();
    }

    /** 锁条目：持有者 token + 到期时刻（epoch 毫秒）。 */
    private record Entry(String token, long expireAtMillis) {
    }
}

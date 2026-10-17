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

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/**
 * 基于内存的单机锁实现（兜底）。
 *
 * <p>无 Redis 时装配，仅在单个 JVM 内互斥，不跨节点。适合单体应用或本地开发；多实例部署需 Redis 实现。
 * 记录持有者与过期时间戳，过期后自动可再次抢占，续期/释放时校验持有者防误删。</p>
 *
 * <p><b>原子性</b>：三个原语都用 {@link ConcurrentHashMap#compute}/{@code computeIfPresent} 完成
 * 「比对持有者再动作」，不做「先 get 再 put」——后者在过期边界上会与并发的 {@link #tryLock} 竞态。</p>
 *
 * <p><b>可注入时钟</b>：构造器允许注入 {@link Clock}，使 TTL 到期、续期这些时序语义可以在测试里被
 * 精确驱动（不真等）；生产默认用系统时钟。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
public class InMemoryLockService implements LockService {

    private record Holder(String owner, long expireAt) {
    }

    private final ConcurrentHashMap<String, Holder> locks = new ConcurrentHashMap<>();

    private final Clock clock;

    /** 用系统时钟构造（生产默认）。 */
    public InMemoryLockService() {
        this(Clock.systemUTC());
    }

    /**
     * 用指定时钟构造（测试注入可控时钟，精确验证到期/续期边界）。
     *
     * @param clock 时钟
     */
    InMemoryLockService(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean tryLock(String key, String owner, Duration ttl) {
        long now = clock.millis();
        long expireAt = now + ttl.toMillis();
        Holder result = locks.compute(key, (k, current) -> {
            if (current == null || current.expireAt() <= now) {
                return new Holder(owner, expireAt);
            }
            return current;
        });
        return result.owner().equals(owner) && result.expireAt() == expireAt;
    }

    @Override
    public boolean renew(String key, String owner, Duration ttl) {
        long now = clock.millis();
        long newExpireAt = now + ttl.toMillis();
        boolean[] renewed = {false};
        locks.computeIfPresent(key, (k, current) -> {
            // 已过期的锁在语义上已不属于任何人：此时续期等于「复活」一把别人可能已经抢到的锁
            if (current.owner().equals(owner) && current.expireAt() > now) {
                renewed[0] = true;
                return new Holder(owner, newExpireAt);
            }
            return current;
        });
        return renewed[0];
    }

    /**
     * 当前持有的锁条目数（仅供测试/监控观察内存占用）。
     *
     * @return 条目数
     */
    int mapSize() {
        return locks.size();
    }

    /**
     * 指定 key 的到期时刻（epoch 毫秒）；key 不存在时返回 {@code null}。仅供测试/监控读取。
     *
     * @param key 锁键
     * @return 到期时刻；key 不存在时为 {@code null}
     */
    @Nullable
    Long expireAtMillis(String key) {
        Holder holder = locks.get(key);
        return holder == null ? null : holder.expireAt();
    }

    @Override
    public boolean unlock(String key, String owner) {
        long now = clock.millis();
        boolean[] released = {false};
        locks.computeIfPresent(key, (k, current) -> {
            // 只要是自己加的锁，无论是否已过期都移除，避免动态 key 过期条目永久堆积导致内存泄漏
            if (current.owner().equals(owner)) {
                released[0] = current.expireAt() > now;
                return null;
            }
            // 顺手清理：非本持有者但已过期的锁也回收
            if (current.expireAt() <= now) {
                return null;
            }
            return current;
        });
        return released[0];
    }
}

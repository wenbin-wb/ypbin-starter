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

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于内存的单机锁实现（兜底）。
 *
 * <p>无 Redis 时装配，仅在单个 JVM 内互斥，不跨节点。适合单体应用或本地开发；多实例部署需 Redis 实现。
 * 记录持有者与过期时间戳，过期后自动可再次抢占，释放时校验持有者防误删。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
public class InMemoryLockService implements LockService {

    private record Holder(String owner, long expireAt) {
    }

    private final ConcurrentHashMap<String, Holder> locks = new ConcurrentHashMap<>();

    @Override
    public boolean tryLock(String key, String owner, Duration ttl) {
        long now = System.currentTimeMillis();
        long expireAt = now + ttl.toMillis();
        Holder result = locks.compute(key, (k, current) -> {
            if (current == null || current.expireAt() <= now) {
                return new Holder(owner, expireAt);
            }
            return current;
        });
        return result.owner().equals(owner) && result.expireAt() == expireAt;
    }

    /**
     * 当前持有的锁条目数（仅供测试/监控观察内存占用）。
     *
     * @return 条目数
     */
    int mapSize() {
        return locks.size();
    }

    @Override
    public boolean unlock(String key, String owner) {
        long now = System.currentTimeMillis();
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

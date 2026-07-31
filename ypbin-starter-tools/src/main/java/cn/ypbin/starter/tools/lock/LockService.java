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

/**
 * 分布式锁契约。
 *
 * <p>提供「尝试加锁 + 释放」两个原子原语，业务方通过 {@link cn.ypbin.starter.tools.lock.DistributedLock}
 * 注解或直接注入本接口使用。存在 Redis 时为跨节点分布式锁，否则退化为单机内存锁；两者均可被业务方
 * 自定义 Bean 覆盖。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
public interface LockService {

    /**
     * 尝试加锁（不阻塞，立即返回）。
     *
     * @param key   锁键
     * @param owner 持有者唯一标识（释放时用于校验，防止误删他人的锁）
     * @param ttl   锁自动过期时间，防止持有者宕机导致死锁
     * @return 是否加锁成功
     */
    boolean tryLock(String key, String owner, Duration ttl);

    /**
     * 释放锁（仅当持有者匹配时才释放）。
     *
     * @param key   锁键
     * @param owner 持有者唯一标识，与加锁时一致才会释放
     * @return 是否释放成功
     */
    boolean unlock(String key, String owner);
}

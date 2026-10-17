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
 * <p>提供「尝试加锁 + 续期 + 释放」三个原子原语，业务方通过 {@link cn.ypbin.starter.tools.lock.DistributedLock}
 * 注解或直接注入本接口使用。存在 Redis 时为跨节点分布式锁，否则退化为单机内存锁；两者均可被业务方
 * 自定义 Bean 覆盖。</p>
 *
 * <p><b>三个原语都必须是「比对持有者再动作」的原子操作</b>，实现层禁止「先查再踩」：查询时锁还是自己的，
 * 中间一旦过期并被他人抢走，随后的动作就会作用到他人的锁上。</p>
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
     * 续期：仅当锁仍属于 {@code owner} 时把 TTL 往后推（原子操作）。
     *
     * <p><b>为什么不能「先查再续」</b>：查询时锁还是自己的，中间锁过期、他人抢到并写入新 owner，
     * 随后的续期就会作用到他人的锁上——两个持有者同时自以为持锁。实现必须在一次原子操作内完成
     * 比对与续期（Redis：Lua 脚本 {@code GET == owner} 才 {@code PEXPIRE}；内存：
     * {@code ConcurrentHashMap.compute}）。</p>
     *
     * <p><b>不重建锁</b>：锁不存在或已不属于 {@code owner} 时返回 {@code false}，绝不用
     * {@code SET}/{@code PEXPIRE} 把键重新造出来或续上他人的锁。</p>
     *
     * @param key   锁键
     * @param owner 持有者唯一标识，与加锁时一致才会续期
     * @param ttl   续期后的存活时长（正数）
     * @return 是否续期成功（{@code false} = 锁已不属于自己，调用方应停止假装持锁）
     */
    boolean renew(String key, String owner, Duration ttl);

    /**
     * 释放锁（仅当持有者匹配时才释放）。
     *
     * @param key   锁键
     * @param owner 持有者唯一标识，与加锁时一致才会释放
     * @return 是否释放成功
     */
    boolean unlock(String key, String owner);
}

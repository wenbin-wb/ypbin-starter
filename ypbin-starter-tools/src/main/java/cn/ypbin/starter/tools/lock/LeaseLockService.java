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

import org.jspecify.annotations.Nullable;

/**
 * 带租约的分布式锁契约（长任务用）。
 *
 * <p><b>为什么要有它（与既有 {@link LockService} 的分工）</b>：
 * {@link LockService} 只有 {@code tryLock(key, owner, ttl)} / {@code unlock(key, owner)} 两个原语，
 * 锁的 TTL 在整个临界区里是**固定的**。它适合「秒级完成、失败可重试」的场景（注解
 * {@link DistributedLock} 面向的就是这类）。而保留清理、多租户轮询评估这类后台任务可能跑几分钟，
 * 固定 TTL 只有两个坏结局：TTL 太短 ⇒ 任务跑一半失锁、另一个副本接手并发执行；TTL 太长 ⇒
 * 持锁者崩溃后要等很久别的副本才能接管。</p>
 *
 * <p>本接口把「租约」做在应用层解决这个矛盾：每次加锁写下的值里带**到期时刻**，存储层只需原子地判断
 * 「不存在 or 已过期」即可抢锁。于是 TTL 的行为不依赖 Redis 的服务端过期精度，也可以被可注入时钟
 * 精确驱动（测试据此不真等）；持锁期间由 {@link LeaseRenewalScheduler} 周期性 {@link LockLease#renew()}
 * 把租约往后推，任务时长不再受 TTL 限制。</p>
 *
 * <p><b>获取失败 = 跳过，不是报错</b>：{@link #tryAcquire(String)} 在锁被其它实例持有时返回
 * {@code null}，而不是抛异常。调用方（{@link LeaseLockGuard}）据此「跳过本轮、下一轮再来」——
 * 这正是后台任务的正确重试方式，报错只会被调度器记成异常噪音。</p>
 *
 * <p><b>存储异常会上抛</b>：Redis 不可达等运行时异常原样抛给调用方，由调用方按
 * {@code ypbin.lock.fail-open} 决定继续执行（fail-open）还是上抛（fail-closed）。本接口不替调用方
 * 决定「锁不可用时业务能不能跑」——那取决于任务是否幂等。</p>
 *
 * @author wenbin
 * @since 2026-10-15
 */
public interface LeaseLockService {

    /**
     * 尝试获取租约（非阻塞）。
     *
     * @param lockKey 锁键（调用方应带上统一前缀，如 {@code LeaseLockProperties#getKeyPrefix()}）
     * @return 获取成功返回租约；锁已被其它实例持有返回 {@code null}；存储异常直接抛出
     */
    @Nullable
    LockLease tryAcquire(String lockKey);
}

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
 * 一次成功获取的锁租约。
 *
 * <p><b>与 {@link LockService} 的分工</b>：{@code LockService} 是「加锁 + 释放」两步原语，
 * 适合「拿到锁后很快做完、幂等可重试」的短临界区（注解 {@link DistributedLock} 即基于它）。
 * 本接口服务于**长任务**：临界区可能跑几分钟甚至更久，单靠一个固定 TTL 会在任务跑完之前失锁，
 * 于是需要「持有期间可续期」的租约语义——到期时刻存在值里，续期即把它往后推。</p>
 *
 * <p><b>生命周期</b>：{@link #renew()} 在 TTL 到期前把租约往后推；{@link #release()} 立即释放。
 * 持锁方必须保证 {@code release()} 一定被调用（用 {@code try/finally}）——否则一次异常就会让锁
 * 一直挂到 TTL 结束，期间所有副本都跑不了这条任务。{@link LeaseLockGuard} 已经用
 * {@code try/finally} 兜住了这件事。</p>
 *
 * <p>实现 {@link AutoCloseable} 是为了让 {@code try (LockLease lease = ...)} 也能正确释放；
 * {@link #close()} 默认委托 {@link #release()} 且**不抛受检异常**。</p>
 *
 * @author wenbin
 * @since 2026-10-15
 */
public interface LockLease extends AutoCloseable {

    /**
     * 锁键。
     *
     * @return 锁键
     */
    String lockKey();

    /**
     * 是否仍持有锁（续期失败或已释放后为 {@code false}）。
     *
     * @return 是否仍持有
     */
    boolean isHeld();

    /**
     * 租约到期时刻（epoch 毫秒；用于观测与测试断言）。
     *
     * @return 到期时刻
     */
    long expiresAtMillis();

    /**
     * 续期（把到期时刻推到「现在 + TTL」）。
     *
     * <p>续期失败（锁已被别的实例接管 / 存储不可用）时返回 {@code false} 并把 {@link #isHeld()}
     * 置为 {@code false}，**不抛异常**——续期只是看门狗，抛异常会打断任务本身。</p>
     *
     * @return 是否续期成功
     */
    boolean renew();

    /**
     * 释放锁（按 token 比对，只有仍是自己的锁才会被删除；重复调用无害）。
     */
    void release();

    @Override
    default void close() {
        release();
    }
}

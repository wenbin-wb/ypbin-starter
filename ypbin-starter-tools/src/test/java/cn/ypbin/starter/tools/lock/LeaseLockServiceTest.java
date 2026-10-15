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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link LeaseLockServiceImpl} 的租约语义单测（进程内存储 + 可注入时钟，不真等、不依赖 Redis）。
 *
 * <p>钉住四件事：① 抢到的锁别人抢不到；② **已过期的锁可被接管**（长任务的核心语义：持锁者崩溃后
 * 别的副本必须能接上）；③ 续期把到期时刻推后，且**续期失败置 {@code isHeld=false} 但不抛异常**
 * （续期是看门狗，不能打断任务）；④ 释放按 token 比对，**不是自己的锁绝不删**。</p>
 *
 * @author wenbin
 * @since 2026-10-15
 */
class LeaseLockServiceTest {

    private static final long START = 1_000_000L;

    private final MutableClock clock = new MutableClock(START);

    private final LeaseLockProperties properties = properties(Duration.ofSeconds(60));

    private final LeaseLockStore store = new InMemoryLeaseLockStore();

    private final LeaseLockServiceImpl lock = new LeaseLockServiceImpl(store, properties, clock);

    @Test
    @DisplayName("抢锁：首个持有者成功，第二者失败（同一把锁同时只能有一个持有者）")
    void tryAcquireShouldExcludeOthers() {
        LockLease first = lock.tryAcquire("task-a");

        assertThat(first).as("首个持有者必须拿到租约").isNotNull();
        assertThat(first.lockKey()).isEqualTo("task-a");
        assertThat(first.isHeld()).isTrue();
        assertThat(first.expiresAtMillis()).isEqualTo(START + 60_000L);
        assertThat(lock.tryAcquire("task-a")).as("同一把锁的第二个请求必须失败").isNull();
    }

    @Test
    @DisplayName("抢锁：不同锁键互不影响")
    void tryAcquireShouldIsolateKeys() {
        assertThat(lock.tryAcquire("task-a")).isNotNull();
        assertThat(lock.tryAcquire("task-b")).as("不同锁键必须能各自加锁").isNotNull();
    }

    @Test
    @DisplayName("过期接管：租约到期后别的持有者可以抢到（多副本接管的核心语义）")
    void expiredLeaseShouldBeTakenOver() {
        LockLease first = lock.tryAcquire("task-a");
        assertThat(first).isNotNull();

        // 未到期时抢不到
        clock.advance(59_000L);
        assertThat(lock.tryAcquire("task-a")).as("未到期前不得被抢占").isNull();

        // 跨过到期时刻（expireAt <= now 即可接管）
        clock.advance(1_000L);
        LockLease second = lock.tryAcquire("task-a");
        assertThat(second).as("到期后必须可被接管，否则持锁者崩溃会让任务永久停摆").isNotNull();
        assertThat(second.expiresAtMillis()).isEqualTo(START + 60_000L + 60_000L);
    }

    @Test
    @DisplayName("续期：把到期时刻推后，且在 TTL 内反复续期可持续持有")
    void renewShouldExtendExpiry() {
        LockLease lease = lock.tryAcquire("task-a");
        assertThat(lease).isNotNull();

        clock.advance(40_000L);
        assertThat(lease.renew()).as("仍持有自己的锁，续期必须成功").isTrue();
        assertThat(lease.expiresAtMillis()).isEqualTo(START + 40_000L + 60_000L);
        assertThat(lease.isHeld()).isTrue();

        // 反复续期后仍持有，且从未让锁过期
        clock.advance(50_000L);
        assertThat(lease.renew()).isTrue();
        assertThat(lock.tryAcquire("task-a")).as("续期期间锁不应被他人抢走").isNull();
    }

    @Test
    @DisplayName("续期失败：锁已被他人接管时 renew 返回 false、置 isHeld=false，且不抛异常")
    void renewShouldFailQuietlyAfterTakeover() {
        LockLease lease = lock.tryAcquire("task-a");
        assertThat(lease).isNotNull();

        // 越过到期时刻，让第二个持有者接管
        clock.advance(60_000L);
        LockLease taker = lock.tryAcquire("task-a");
        assertThat(taker).isNotNull();

        assertThat(lease.renew()).as("锁已被接管，续期必须返回 false").isFalse();
        assertThat(lease.isHeld()).as("续期失败后必须如实置为「已丢失」").isFalse();
        // 再续一次也不能复活
        assertThat(lease.renew()).isFalse();
    }

    @Test
    @DisplayName("释放：token 比对——拿旧租约释放不得删掉新持有者的锁")
    void releaseShouldNotDeleteOthersLease() {
        LockLease stale = lock.tryAcquire("task-a");
        assertThat(stale).isNotNull();

        clock.advance(60_000L);
        LockLease current = lock.tryAcquire("task-a");
        assertThat(current).isNotNull();

        // 旧租约迟到释放：不得误删新持有者的锁
        stale.release();
        assertThat(current.isHeld()).isTrue();
        assertThat(lock.tryAcquire("task-a")).as("新持有者的锁必须仍然存在").isNull();

        // 真正的持有者释放后，锁才可再次被抢
        current.release();
        assertThat(lock.tryAcquire("task-a")).as("持有者释放后锁应可再次获取").isNotNull();
    }

    @Test
    @DisplayName("释放：重复调用无害，且释放后 isHeld=false")
    void releaseShouldBeIdempotent() {
        LockLease lease = lock.tryAcquire("task-a");
        assertThat(lease).isNotNull();

        lease.release();
        assertThat(lease.isHeld()).isFalse();
        lease.release();
        assertThat(lease.isHeld()).isFalse();
    }

    @Test
    @DisplayName("AutoCloseable：close 等价于 release（try-with-resources 可正确释放）")
    void closeShouldRelease() {
        try (LockLease lease = lock.tryAcquire("task-a")) {
            assertThat(lease).isNotNull();
            assertThat(lease.isHeld()).isTrue();
        }
        assertThat(lock.tryAcquire("task-a")).as("close 之后锁必须已释放").isNotNull();
    }

    /**
     * 构造配置。
     *
     * @param ttl 租约时长
     * @return 配置
     */
    private static LeaseLockProperties properties(Duration ttl) {
        LeaseLockProperties props = new LeaseLockProperties();
        props.setTtl(ttl);
        props.setRenewInterval(ttl.dividedBy(3));
        return props;
    }
}

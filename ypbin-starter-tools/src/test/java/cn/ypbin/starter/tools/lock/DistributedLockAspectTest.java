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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

/**
 * {@link DistributedLockAspect} 单元测试（真实 AOP 代理织入）。
 *
 * @author wenbin
 * @since 2026-07-31
 */
class DistributedLockAspectTest {

    /** 记录 tryLock/unlock 调用的假锁 */
    static class RecordingLockService implements LockService {
        final AtomicInteger tryCount = new AtomicInteger();
        final AtomicInteger unlockCount = new AtomicInteger();
        boolean grant;

        RecordingLockService(boolean grant) {
            this.grant = grant;
        }

        @Override
        public boolean tryLock(String key, String owner, Duration ttl) {
            tryCount.incrementAndGet();
            return grant;
        }

        @Override
        public boolean unlock(String key, String owner) {
            unlockCount.incrementAndGet();
            return true;
        }
    }

    public static class Service {
        int executed;

        @DistributedLock(key = "biz", waitTime = 0)
        public String run() {
            executed++;
            return "done";
        }

        @DistributedLock(key = "biz", failStrategy = DistributedLock.FailStrategy.EXCEPTION)
        public String runOrThrow() {
            executed++;
            return "done";
        }
    }

    private Service proxy(LockService lockService) {
        AspectJProxyFactory factory = new AspectJProxyFactory(new Service());
        factory.addAspect(new DistributedLockAspect(lockService));
        return factory.getProxy();
    }

    @Test
    void shouldExecuteAndReleaseWhenLockGranted() {
        RecordingLockService lock = new RecordingLockService(true);
        Service service = proxy(lock);

        assertThat(service.run()).isEqualTo("done");
        assertThat(lock.tryCount.get()).isEqualTo(1);
        assertThat(lock.unlockCount.get()).isEqualTo(1);
    }

    @Test
    void shouldSkipWhenLockDeniedAndStrategySkip() {
        RecordingLockService lock = new RecordingLockService(false);
        Service service = proxy(lock);

        // 抢锁失败 + SKIP：方法不执行，返回 null，也不应尝试释放
        assertThat(service.run()).isNull();
        assertThat(lock.unlockCount.get()).isZero();
    }

    @Test
    void shouldThrowWhenLockDeniedAndStrategyException() {
        RecordingLockService lock = new RecordingLockService(false);
        Service service = proxy(lock);

        assertThatThrownBy(service::runOrThrow)
            .isInstanceOf(LockAcquireException.class);
    }
}

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
 * 空租约锁实现（{@code ypbin.lock.enabled=false} 时装配）。
 *
 * <p>语义：永远「抢到」，即**退回到加锁之前的行为**——每个副本都会执行任务。
 * 仅供单实例部署或排障时显式关闭锁用；多副本下会重复执行，装配层会打 WARN。</p>
 *
 * @author wenbin
 * @since 2026-10-15
 */
public class NoopLeaseLockService implements LeaseLockService {

    @Override
    public LockLease tryAcquire(String lockKey) {
        return new NoopLease(lockKey);
    }

    /** 永远「持有」的租约（续期恒成功、释放无动作）。 */
    private static final class NoopLease implements LockLease {

        private final String lockKey;

        private NoopLease(String lockKey) {
            this.lockKey = lockKey;
        }

        @Override
        public String lockKey() {
            return lockKey;
        }

        @Override
        public boolean isHeld() {
            return true;
        }

        @Override
        public long expiresAtMillis() {
            return Long.MAX_VALUE;
        }

        @Override
        public boolean renew() {
            return true;
        }

        @Override
        public void release() {
            // 空锁没有需要释放的资源：显式留空而非省略，表明这是设计而非遗漏
        }
    }
}

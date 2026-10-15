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
 * 锁租约续期的调度抽象。
 *
 * <p>抽出来是为了让 {@link LeaseLockGuard} 的测试不必依赖真实线程与真实等待；
 * 生产实现是 {@link ScheduledLeaseRenewalScheduler}。</p>
 *
 * @author wenbin
 * @since 2026-10-15
 */
public interface LeaseRenewalScheduler {

    /**
     * 按固定间隔重复执行续期任务。
     *
     * @param task     续期任务
     * @param interval 间隔
     * @return 可取消句柄（任务结束时应取消）
     */
    Cancellable schedule(Runnable task, Duration interval);

    /** 可取消句柄。 */
    interface Cancellable {

        /** 取消后续执行（幂等）。 */
        void cancel();
    }
}

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
 * {@link DistributedLock} 自动续期的调度抽象。
 *
 * <p><b>为什么与租约锁的 {@link LeaseRenewalScheduler} 分开</b>：两者生命周期不同——租约锁是
 * 「一个任务一个看门狗」，注解续期是「每一次成功持锁一个注册」。复用同一个单线程调度器会互相挤占：
 * 一个锁的长续期回调（存储抖动时）会推迟另一个锁的续期时刻，把「各自独立续期」变成「排队续期」。
 * 关停语义也不同：本调度器必须在方法退出时**立即注销单个锁**的续期，而租约看门狗只在任务结束时取消。</p>
 *
 * <p>抽成接口是为了让 {@link DistributedLockAspect} 的测试注入「手动触发」的假调度器，
 * 不依赖真实线程与真实等待；生产实现是 {@link ScheduledLockRenewalScheduler}。</p>
 *
 * @author wenbin
 * @since 2026-10-17
 */
public interface LockRenewalScheduler {

    /**
     * 注册一个按固定间隔重复执行的续期任务。
     *
     * <p>每次成功持锁调用一次；方法退出（正常或异常）时必须 {@link Registration#cancel()}，
     * 否则调度器里会留下永不停歇的续期任务（线程/内存泄漏，且会一直对已释放的锁做存储往返）。</p>
     *
     * @param renewalTask 续期任务（失败告警与停止续期由任务内部处理，不向调度器抛异常）
     * @param interval    续期间隔（必须为正）
     * @return 注册句柄（用于注销；{@link Registration#cancel()} 幂等）
     */
    Registration schedule(Runnable renewalTask, Duration interval);

    /** 一次续期注册的可注销句柄。 */
    interface Registration {

        /**
         * 注销：取消后续执行并释放调度器内的登记（幂等；重复调用无副作用）。
         */
        void cancel();
    }
}

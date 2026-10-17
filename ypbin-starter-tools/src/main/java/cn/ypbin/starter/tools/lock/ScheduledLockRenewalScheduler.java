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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.DisposableBean;

/**
 * {@link DistributedLock} 自动续期的生产调度器：一条守护线程，按锁实例注册/注销。
 *
 * <p><b>线程模型</b>：单线程 + daemon。续期任务只做一次存储往返（Redis 侧一条 Lua），负载可忽略；
 * 单线程同时保证了「同一时刻最多一个续期在途」，不需要为共享的 {@code StringRedisTemplate} 额外加锁。
 * 用 daemon 线程使 JVM 退出不被它拖住；应用关闭时由 {@link #destroy()} 显式停止（Spring 对
 * {@link DisposableBean} 的自动回调），不会留下仍在续期的任务。</p>
 *
 * <p><b>为什么按实例注册而不是按 key 去重</b>：同一 key 在极端时序下可能先后被两个持有者注册
 * （前一个已超时失锁、后一个抢到），按 key 覆盖会把后注册者的句柄误挂到前一个任务上，导致
 * 「注销了别人的续期」。每次 {@link #schedule} 返回独立句柄，互不干扰。</p>
 *
 * <p><b>零成本空转</b>：{@code ScheduledThreadPoolExecutor} 按需创建线程，未注册任何续期时
 * 连线程都不会启动——不用 {@code autoRenew} 的应用没有额外线程开销。</p>
 *
 * @author wenbin
 * @since 2026-10-17
 */
public class ScheduledLockRenewalScheduler implements LockRenewalScheduler, DisposableBean {

    /** 续期线程名（排障时一眼能认出，与租约锁看门狗区分）。 */
    static final String THREAD_NAME = "ypbin-distributed-lock-renewal";

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, THREAD_NAME);
        thread.setDaemon(true);
        return thread;
    });

    /** 未注销的续期登记（注销时移除，避免句柄随调用次数无限堆积）。 */
    private final Set<Registration> registrations = ConcurrentHashMap.newKeySet();

    @Override
    public Registration schedule(Runnable renewalTask, Duration interval) {
        if (interval == null || interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("自动续期间隔必须为正数：interval=" + interval);
        }
        Registration registration = new Registration();
        ScheduledFuture<?> future = executor.scheduleAtFixedRate(
            renewalTask, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
        registration.future = future;
        registrations.add(registration);
        return registration;
    }

    @Override
    public void destroy() {
        // 先注销全部登记再关停线程：关闭窗口内不再发起新的续期。已入队的任务随 shutdownNow 取消；
        // 已进入执行体的一次续期不会被强行打断（cancel(false) 语义，且打断线程也无从回滚已发出的 PEXPIRE），
        // 它最多做一次 owner 校验后的无害延长——这是可接受的最坏结果，不宣称「关闭后绝不 PEXPIRE」。
        registrations.forEach(Registration::cancel);
        registrations.clear();
        executor.shutdownNow();
    }

    /**
     * 当前未注销的续期登记数（测试与监控用；恒不为负）。
     *
     * @return 登记数
     */
    int activeRegistrations() {
        return registrations.size();
    }

    /** 一次续期登记：持有 future，注销时把自己从登记集合中移除。 */
    private final class Registration implements LockRenewalScheduler.Registration {

        private final AtomicBoolean cancelled = new AtomicBoolean();

        /** schedule 返回前赋值；volatile 保证注销线程可见。 */
        @Nullable
        private volatile ScheduledFuture<?> future;

        @Override
        public void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                ScheduledFuture<?> scheduled = future;
                if (scheduled != null) {
                    // 不打断在途的一次续期：它持有 owner 比对，最坏结果是一次无害的 PEXPIRE
                    scheduled.cancel(false);
                }
                registrations.remove(this);
            }
        }
    }
}

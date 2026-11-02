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

import cn.ypbin.starter.core.util.LogSanitizer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 后台任务的「先抢租约、抢不到就跳过」守卫。
 *
 * <p>所有需要单实例执行的长任务都通过它包裹执行，语义集中在三处：</p>
 * <ol>
 *   <li><b>抢到锁</b> ⇒ 执行任务，并在执行期间由看门狗自动续期；</li>
 *   <li><b>没抢到</b> ⇒ **跳过**（返回 {@code executed=false}），记 info 日志 + 计数，不报错、不排队；</li>
 *   <li><b>存储不可用</b> ⇒ 默认 fail-open（记 error 全堆栈后继续执行），可配 {@code ypbin.lock.fail-open=false}
 *       改为 fail-closed（把异常上抛，让调度器记成任务失败）。</li>
 * </ol>
 *
 * <p><b>绝不泄漏锁</b>：无论任务正常返回还是抛异常，{@code finally} 都会先取消续期看门狗、
 * 再释放租约。这是本类存在的最大理由——把「忘记释放」这类错误从每个任务里消除掉。</p>
 *
 * <p><b>与既有 {@link DistributedLock} 注解的分工</b>：注解走 AOP + {@link LockService}，
 * 适合「秒级、固定 TTL」的短临界区；本类面向长任务（可续期、fail-open 策略、按任务计数指标），
 * 需显式包裹调用。两者并存，互不影响。</p>
 *
 * <p><b>指标</b>：{@code <metric-prefix>.skipped} / {@code .unavailable} / {@code .renew.failed}
 * 按 {@code task} tag 区分，前缀由 {@link LeaseLockProperties#getMetricPrefix()} 配置（默认
 * {@code ypbin.lock}），用于回答「某个任务到底有没有在某个副本上跑」「锁存储是不是一直在抖」。</p>
 *
 * <p><b>指标是可选能力，不是使用守卫的前置条件</b>：micrometer 在 tools 模块是 optional 依赖，
 * 且 {@code MeterRegistry} 在宿主里可能尚未注册（自动配置处理顺序）。因此本类同时提供
 * {@link #LeaseLockGuard(LeaseLockService, LeaseRenewalScheduler, LeaseLockProperties)} 这个
 * **无指标**构造器：没有 registry 时三个计数一律跳过，锁语义（跳过/fail-open/finally 释放）完全不变。
 * 这是一次线上事故的教训——把可选依赖当必要条件，会让「锁能力可用」被「指标可用」绑架：
 * 一旦宿主容器里取不到 {@code MeterRegistry}，守卫 Bean 整块消失，下游注入直接启动失败。</p>
 *
 * @author wenbin
 * @since 2026-10-15
 */
public class LeaseLockGuard {

    private static final Logger log = LoggerFactory.getLogger(LeaseLockGuard.class);

    /** 因锁被其它实例持有而跳过的计数后缀。 */
    public static final String METRIC_SKIPPED_SUFFIX = ".skipped";

    /** 锁存储不可用、按 fail-open 降级执行的计数后缀。 */
    public static final String METRIC_UNAVAILABLE_SUFFIX = ".unavailable";

    /** 续期失败的计数后缀（>0 说明锁存储不稳定或任务时长已逼近 TTL）。 */
    public static final String METRIC_RENEW_FAILED_SUFFIX = ".renew.failed";

    private final LeaseLockService leaseLockService;

    private final LeaseRenewalScheduler renewalScheduler;

    private final LeaseLockProperties properties;

    /** 指标注册表；{@code null} = 本次装配没有可用的 registry，计数降级为「不记录」。 */
    @Nullable
    private final MeterRegistry meterRegistry;

    /**
     * 不记录指标地构造守卫（micrometer 缺失或容器中没有 {@code MeterRegistry} 时用）。
     *
     * @param leaseLockService 租约锁服务
     * @param renewalScheduler 续期看门狗调度器
     * @param properties       配置
     */
    public LeaseLockGuard(LeaseLockService leaseLockService, LeaseRenewalScheduler renewalScheduler,
                          LeaseLockProperties properties) {
        this(leaseLockService, renewalScheduler, properties, null);
    }

    /**
     * 构造守卫。
     *
     * @param leaseLockService 租约锁服务
     * @param renewalScheduler 续期看门狗调度器
     * @param properties       配置
     * @param meterRegistry    指标注册表；{@code null} 表示不记录指标（能力可用性不受影响）
     */
    public LeaseLockGuard(LeaseLockService leaseLockService, LeaseRenewalScheduler renewalScheduler,
                          LeaseLockProperties properties, @Nullable MeterRegistry meterRegistry) {
        this.leaseLockService = leaseLockService;
        this.renewalScheduler = renewalScheduler;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    /**
     * 抢到锁才执行（无返回值任务）。
     *
     * @param lockName 锁名（业务侧常量，如 {@code alert-evaluate}）
     * @param task     任务
     * @return 是否执行了任务
     */
    public boolean runExclusively(String lockName, Runnable task) {
        return callExclusively(lockName, () -> {
            task.run();
            return Boolean.TRUE;
        }).executed();
    }

    /**
     * 抢到锁才执行（有返回值任务）。
     *
     * @param lockName 锁名
     * @param task     任务
     * @param <T>      返回值类型
     * @return 执行结果（跳过时 {@code executed=false}）
     */
    public <T> LeaseLockOutcome<T> callExclusively(String lockName, Supplier<T> task) {
        String lockKey = properties.getKeyPrefix() + lockName;
        LockLease lease;
        try {
            lease = leaseLockService.tryAcquire(lockKey);
        } catch (RuntimeException ex) {
            if (!properties.isFailOpen()) {
                // fail-closed：不假装执行过，如实上抛让调度器记成任务失败
                throw ex;
            }
            count(METRIC_UNAVAILABLE_SUFFIX, lockName);
            log.error("[ypbin] 分布式锁不可用，按 fail-open **继续执行**任务 {}（多副本下可能重复执行；"
                + "如需严格单实例请设 {}.fail-open=false）", lockName, LeaseLockProperties.PREFIX, ex);
            return new LeaseLockOutcome<>(true, true, task.get());
        }
        if (lease == null) {
            count(METRIC_SKIPPED_SUFFIX, lockName);
            log.info("[ypbin] 任务 {} 的分布式锁已被其它实例持有，本次**跳过**（获取失败=跳过，不报错）", lockName);
            return LeaseLockOutcome.skipped();
        }
        // 续期任务必须在 try 内安排：容器关闭期 schedule 会抛 RejectedExecutionException，
        // 若发生在 try 之外，已抢到的租约就永远不会释放，锁要占满整个 TTL 才被其它副本拿到。
        LeaseRenewalScheduler.Cancellable renewal = null;
        try {
            renewal = renewalScheduler.schedule(() -> renewOrLog(lockName, lease), properties.getRenewInterval());
            return new LeaseLockOutcome<>(true, false, task.get());
        } finally {
            if (renewal != null) {
                renewal.cancel();
            }
            lease.release();
        }
    }

    /**
     * 看门狗续期：失败不抛（续期是尽力而为），但必须计数 + 留痕。
     *
     * @param lockName 锁名
     * @param lease    租约
     */
    private void renewOrLog(String lockName, LockLease lease) {
        if (lease.renew()) {
            return;
        }
        count(METRIC_RENEW_FAILED_SUFFIX, lockName);
        log.warn("[ypbin] 任务 {} 的锁续期失败（锁可能已被其它实例接管）：本轮仍会跑完，"
            + "但多副本下该时间窗内可能出现并发执行", lockName);
    }

    /**
     * 按任务取计数（Micrometer 对同 id 幂等注册，无需自行缓存）；无 registry 时为 no-op。
     *
     * @param suffix   指标名后缀
     * @param lockName 锁名
     */
    private void count(String suffix, String lockName) {
        if (meterRegistry == null) {
            // 指标不可用只是「少了个观测维度」，绝不因此改变锁语义或抛错
            return;
        }
        String name = properties.getMetricPrefix() + suffix;
        Counter.builder(name)
            .description(name)
            .tag("task", LogSanitizer.sanitize(lockName))
            .register(meterRegistry)
            .increment();
    }
}

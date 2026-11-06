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
package cn.ypbin.starter.job.core;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.PeriodicTrigger;

/**
 * 定时任务调度管理器。
 *
 * <p>基于 Spring {@link TaskScheduler} + 自维护 {@link ScheduledFuture} 注册表实现运行时动态调度：
 * 注册/启停/改 cron/立即执行一次，均不需重启。多实例下每个节点各自持有调度器，同一任务到点会各自触发。</p>
 *
 * <p><strong>防重双保险</strong>：执行入口先做<em>任务级内存互斥</em>（同一节点上同任务绝不同时执行，
 * 慢执行跨触发间隔不会在本节点双跑），再做<em>per-slice 分布式锁</em>抢占（多节点只有一个实例真正执行，
 * 锁键带触发时间片，长任务不会持锁挡住下一次触发）。</p>
 *
 * <p><strong>时间片取「计划触发时刻」而非执行时刻</strong>：锁键里的时间片若取执行线程拿到任务时的
 * {@code now()}，节点间会因排队延迟、时钟毫秒差得到不同的键，同一触发时刻在各节点各跑一次，防重失效。
 * 故：cron 任务在调度线程里按 {@link CronExpression} 顺序推算本次计划时刻（各节点同一 cron 得到完全
 * 相同的序列）；固定频率任务按 {@code epochSecond / fixedRateSeconds} 分桶（同一频率下相邻两次触发
 * 必落不同桶）。锁键时间片用 <b>epoch 秒</b>，与节点时区无关。内存互斥仅单节点生效；跨节点同一触发
 * 时刻仍可能双双执行（各节点各自持片锁），彻底防重需再叠加任务级分布式锁，由宿主按需引入。</p>
 *
 * <p><strong>执行线程池拒绝</strong>：提交到 {@link Executor} 时可能被拒（有界线程池满或已关闭）。
 * 定时触发被拒时回调 {@code onSkip} 并记 WARN，<b>绝不让异常逃逸到调度线程</b>——Spring 的
 * {@code ReschedulingRunnable} 一旦抛出，后续触发不再排期，任务会静默停摆。手动触发被拒时抛
 * {@link IllegalStateException}，由调用方（接口）明确告知用户。</p>
 *
 * <p>本类不持久化任务；任务的存储/CRUD/页面由业务方实现，通过本类的方法把内存调度与任务表同步。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public class JobManager {

    private static final Logger log = LoggerFactory.getLogger(JobManager.class);

    /** 节点唯一标识（用于分布式锁持有者） */
    private final String nodeId;
    private final TaskScheduler taskScheduler;
    private final Executor jobExecutor;
    private final Map<String, JobHandler> handlers;
    private final JobExecutionListener listener;
    private final JobLock jobLock;
    private final CronService cronService;
    private final Clock clock;

    /** jobId -> 运行时调度句柄 */
    private final Map<Long, Scheduled> registry = new ConcurrentHashMap<>();

    /**
     * jobId -> 该任务是否正在执行（任务级内存互斥标志）。
     *
     * <p>entry 常驻不随任务注销移除：避免删除操作与并发 CAS 竞争导致「新触发已置位却被清掉」；
     * 数量与历史 jobId 规模相当、量级很小，无内存风险。</p>
     */
    private final Map<Long, AtomicBoolean> runningJobs = new ConcurrentHashMap<>();

    private record Scheduled(JobDefinition definition, ScheduledFuture<?> future, Object activationToken) {
    }

    public JobManager(String nodeId, TaskScheduler taskScheduler, Executor jobExecutor,
            Map<String, JobHandler> handlers,
            JobExecutionListener listener, JobLock jobLock, CronService cronService) {
        this(nodeId, taskScheduler, jobExecutor, handlers, listener, jobLock, cronService, Clock.systemDefaultZone());
    }

    /**
     * 指定时钟构造（触发时刻、锁键时间片以此为基准，便于测试跨节点一致的锁键）。
     *
     * @param nodeId        节点标识
     * @param taskScheduler 调度线程池
     * @param jobExecutor   执行线程池
     * @param handlers      执行器名称 → 执行体
     * @param listener      执行监听
     * @param jobLock       集群防重锁
     * @param cronService   cron 校验服务
     * @param clock         时钟
     */
    public JobManager(String nodeId, TaskScheduler taskScheduler, Executor jobExecutor,
            Map<String, JobHandler> handlers,
            JobExecutionListener listener, JobLock jobLock, CronService cronService, Clock clock) {
        this.nodeId = nodeId;
        this.taskScheduler = taskScheduler;
        this.jobExecutor = jobExecutor;
        this.handlers = handlers;
        this.listener = listener;
        this.jobLock = jobLock;
        this.cronService = cronService;
        this.clock = clock;
    }

    /**
     * 注册（或原子替换）一个任务并开始调度。候选调度创建失败时保留旧调度。
     *
     * @param definition 任务定义
     */
    public synchronized void register(JobDefinition definition) {
        validate(definition);
        Scheduled candidate = createCandidate(definition);
        Scheduled previous = registry.put(definition.getId(), candidate);
        cancel(previous);
        logRegistration(definition);
    }

    /**
     * 原子替换任务。与 {@link #register(JobDefinition)} 语义一致。
     *
     * @param definition 任务定义
     */
    public synchronized void replace(JobDefinition definition) {
        register(definition);
    }

    /**
     * 将本节点运行态与完整任务定义集合对齐。候选调度全部创建成功后才统一切换；任一候选创建失败时，
     * 取消已创建的候选并完整保留原运行态。
     *
     * @param definitions 完整的启用任务定义集合
     */
    public synchronized void reconcile(List<JobDefinition> definitions) {
        Objects.requireNonNull(definitions, "任务定义集合不能为空");
        Map<Long, JobDefinition> desired = new LinkedHashMap<>();
        for (JobDefinition definition : definitions) {
            validate(definition);
            if (desired.putIfAbsent(definition.getId(), definition) != null) {
                throw new IllegalArgumentException("任务定义 ID 重复：" + definition.getId());
            }
        }

        Map<Long, Scheduled> candidates = new LinkedHashMap<>();
        try {
            for (JobDefinition definition : desired.values()) {
                Scheduled current = registry.get(definition.getId());
                if (current == null || !sameDefinition(current.definition(), definition)) {
                    candidates.put(definition.getId(), createCandidate(definition));
                }
            }
        } catch (RuntimeException e) {
            candidates.values().forEach(this::cancel);
            throw e;
        }

        for (Map.Entry<Long, Scheduled> entry : candidates.entrySet()) {
            Scheduled previous = registry.put(entry.getKey(), entry.getValue());
            cancel(previous);
            logRegistration(entry.getValue().definition());
        }
        for (Long jobId : new ArrayList<>(registry.keySet())) {
            if (!desired.containsKey(jobId)) {
                cancel(jobId);
            }
        }
    }

    /**
     * 停止并移除一个任务（正在执行的不中断，仅不再触发）。
     *
     * @param jobId 任务 ID
     */
    public synchronized void unregister(Long jobId) {
        cancel(jobId);
    }

    /**
     * 立即执行一次（不影响周期调度），用于「手动触发」。同样受集群防重约束。
     *
     * @param jobId 任务 ID
     */
    public void triggerNow(Long jobId) {
        Scheduled scheduled = registry.get(jobId);
        if (scheduled == null) {
            throw new IllegalArgumentException("任务未注册：" + jobId);
        }
        // 使用执行线程池执行，调度线程不阻塞
        submit(scheduled.definition(), true, clock.instant());
    }

    /**
     * 用给定定义手动执行一次（任务未注册调度时也可临时跑，如「测试执行」）。
     *
     * @param definition 任务定义
     */
    public void triggerNow(JobDefinition definition) {
        validate(definition);
        submit(definition, true, clock.instant());
    }

    /**
     * 是否已注册调度。
     *
     * @param jobId 任务 ID
     * @return 是否已注册
     */
    public boolean isScheduled(Long jobId) {
        return registry.containsKey(jobId);
    }

    /**
     * 已注册的任务 ID 列表。
     *
     * @return 任务 ID 列表
     */
    public List<Long> scheduledIds() {
        return new ArrayList<>(registry.keySet());
    }

    private Scheduled createCandidate(JobDefinition definition) {
        Object activationToken = new Object();
        // 计划时刻序列随定义创建：重新注册（改 cron）即换新序列，不会复用旧表达式的进度
        Supplier<Instant> triggerTimes = triggerTimes(definition);
        // 调度线程只负责判断激活态和提交，不执行任何阻塞操作
        Runnable task = () -> {
            if (isActive(definition.getId(), activationToken)) {
                submit(definition, false, triggerTimes.get());
            }
        };
        ScheduledFuture<?> future = schedule(definition, task);
        if (future == null) {
            throw new IllegalStateException("任务调度器未返回调度句柄：" + definition.getId());
        }
        return new Scheduled(definition, future, activationToken);
    }

    /**
     * 构造「本次计划触发时刻」的序列（各节点同一定义应得到相同取值，用作分布式锁时间片）。
     *
     * <p>cron：按 {@link CronExpression} 从注册时刻起顺序推进，节点间序列完全一致；固定频率：按
     * {@code epochSecond / fixedRateSeconds} 分桶，同一频率下相邻两次触发必落不同桶。</p>
     *
     * @param definition 任务定义
     * @return 每次调用返回下一次计划触发时刻（整秒）
     */
    Supplier<Instant> triggerTimes(JobDefinition definition) {
        String cron = definition.getCron();
        if (cron != null && !cron.isBlank()) {
            CronExpression expression = CronExpression.parse(cron);
            ZoneId zone = clock.getZone();
            AtomicReference<Instant> next = new AtomicReference<>(nextCronTime(expression, clock.instant(), zone));
            return () -> {
                // 调度线程串行触发，无需加锁；表达式走到尽头（无后续时刻）时保留当前值
                Instant current = Objects.requireNonNull(next.get());
                next.set(nextCronTime(expression, current, zone));
                return current;
            };
        }
        Long fixedRateSeconds = definition.getFixedRateSeconds();
        long period = fixedRateSeconds == null || fixedRateSeconds <= 0 ? 1L : fixedRateSeconds;
        return () -> {
            long epochSecond = clock.instant().getEpochSecond();
            return Instant.ofEpochSecond(Math.floorDiv(epochSecond, period) * period);
        };
    }

    /**
     * cron 的下一次计划触发时刻。
     *
     * <p>{@link CronExpression#next} 需要带时区的日历类型（{@code Instant} 不支持星期/月等字段），
     * 故先按 {@link Clock#getZone()} 转成 {@link ZonedDateTime} 再求值——与 {@code CronTrigger}
     * 默认按系统时区排期同口径。表达式无后续时刻时返回入参（任务已走到尽头）。</p>
     */
    private static Instant nextCronTime(CronExpression expression, Instant after, ZoneId zone) {
        ZonedDateTime next = expression.next(after.atZone(zone));
        return next == null ? after : next.toInstant();
    }

    /**
     * 提交到执行线程池；被拒绝时按触发方式分流处理（见类注释）。
     */
    private void submit(JobDefinition definition, boolean manual, Instant triggerTime) {
        try {
            jobExecutor.execute(() -> runWithGuard(definition, manual, triggerTime));
        } catch (RejectedExecutionException e) {
            if (manual) {
                throw new IllegalStateException("任务执行线程池已满或已关闭，本次触发被拒绝：" + definition.getName(), e);
            }
            log.warn("[ypbin-starter] job skipped: 执行线程池已满或已关闭，本次触发不再执行: name={}, trigger={}",
                definition.getName(), triggerTime);
            listener.onSkip(context(definition, manual, triggerTime));
        }
    }

    private JobContext context(JobDefinition definition, boolean manual, Instant triggerTime) {
        return new JobContext(definition.getId(), definition.getName(), definition.getExecutor(),
            definition.getArgs(), manual, LocalDateTime.ofInstant(triggerTime, clock.getZone()));
    }

    @Nullable
    private ScheduledFuture<?> schedule(JobDefinition definition, Runnable task) {
        // 用局部变量 + 显式判空（而非 isCronTrigger()）：静态分析无法从 isCronTrigger() 反推 cron 非空
        String cron = definition.getCron();
        if (cron != null && !cron.isBlank()) {
            return taskScheduler.schedule(task, new CronTrigger(cron));
        }
        Long fixedRateSeconds = definition.getFixedRateSeconds();
        if (fixedRateSeconds == null || fixedRateSeconds <= 0) {
            // 不静默兜底：既不是 cron 触发、又没有合法固定间隔，属任务定义错误，显式失败
            throw new IllegalStateException("任务既未配置 cron 也未配置合法的 fixedRateSeconds：id="
                + definition.getId());
        }
        PeriodicTrigger trigger = new PeriodicTrigger(Duration.ofSeconds(fixedRateSeconds));
        trigger.setFixedRate(true);
        return taskScheduler.schedule(task, trigger);
    }

    private boolean isActive(Long jobId, Object activationToken) {
        Scheduled scheduled = registry.get(jobId);
        return scheduled != null && scheduled.activationToken() == activationToken;
    }

    private boolean sameDefinition(JobDefinition left, JobDefinition right) {
        return Objects.equals(left.getId(), right.getId())
            && Objects.equals(left.getName(), right.getName())
            && Objects.equals(left.getExecutor(), right.getExecutor())
            && Objects.equals(left.getCron(), right.getCron())
            && Objects.equals(left.getFixedRateSeconds(), right.getFixedRateSeconds())
            && Objects.equals(left.getArgs(), right.getArgs())
            && left.getTimeoutSeconds() == right.getTimeoutSeconds()
            && left.isConcurrentGuard() == right.isConcurrentGuard();
    }

    private void logRegistration(JobDefinition definition) {
        log.info("[ypbin-starter] job registered: id={}, name={}, cron={}, rate={}s.",
            definition.getId(), definition.getName(), definition.getCron(), definition.getFixedRateSeconds());
    }

    private void cancel(Long jobId) {
        cancel(registry.remove(jobId));
    }

    private void cancel(@Nullable Scheduled scheduled) {
        if (scheduled != null) {
            scheduled.future().cancel(false);
        }
    }

    /**
     * 执行入口：任务级内存互斥 → per-slice 分布式抢锁 → 找执行体 → 回调监听 → 执行。
     */
    private void runWithGuard(JobDefinition definition, boolean manual, Instant triggerTime) {
        JobContext context = context(definition, manual, triggerTime);

        JobHandler handler = handlers.get(definition.getExecutor());
        if (handler == null) {
            log.warn("[ypbin-starter] job executor not found: {} (job={})", definition.getExecutor(),
                definition.getName());
            listener.onError(context, 0L, new IllegalStateException("执行器不存在：" + definition.getExecutor()));
            return;
        }

        boolean guarded = definition.isConcurrentGuard();
        // 任务级内存互斥：单节点上同任务绝不同时执行（慢执行跨触发间隔时，本次触发直接按防重跳过，
        // 不会双跑）；未开启防重的任务（concurrentGuard=false）不参与互斥，保持按调度逐个触发
        if (guarded && !markRunning(definition.getId())) {
            log.debug("[ypbin-starter] job skipped by local running guard: name={}", definition.getName());
            listener.onSkip(context);
            return;
        }
        try {
            // 集群防重：锁键带「计划触发时刻」的时间片，避免长任务持锁挡住下一次正常触发；
            // 时间片用 epoch 秒（与节点时区无关），各节点同一次触发得到同一个键
            // 内存互斥已消除单节点并发，分布式锁在此承担多节点抢跑仲裁
            String lockKey = null;
            boolean locked = false;
            if (guarded) {
                lockKey = "ypbin:job:" + definition.getId() + ":" + triggerTime.getEpochSecond();
                // timeoutSeconds 仅作锁 TTL 放大依据（非执行超时）：长任务按「timeoutSeconds + 5」秒持锁，
                // 确保执行完成前锁不过期、其它节点不抢跑；未配置时取 1 小时兜底。任务不会被该值强制中断
                Duration ttl = Duration.ofSeconds(definition.getTimeoutSeconds() > 0
                    ? definition.getTimeoutSeconds() + 5 : 3600);
                locked = jobLock.tryLock(lockKey, nodeId, ttl);
                if (!locked) {
                    listener.onSkip(context);
                    return;
                }
            }

            long start = System.currentTimeMillis();
            try {
                listener.onStart(context);
                handler.execute(context);
                listener.onSuccess(context, System.currentTimeMillis() - start);
            } catch (Throwable e) {
                // 末位传 e 让日志框架打印完整堆栈：只记 e.getMessage() 时，NPE 等 message 为 null 的异常
                // 会退化成 "err=null"，线上完全无法定位（issue #95）。
                log.warn("[ypbin-starter] job execute failed: name={}", definition.getName(), e);
                listener.onError(context, System.currentTimeMillis() - start, e);
            } finally {
                String currentLockKey = lockKey;
                if (locked && currentLockKey != null) {
                    jobLock.unlock(currentLockKey, nodeId);
                }
            }
        } finally {
            // 无论执行成功/失败/未抢到分布式锁，都要释放内存互斥标志，保证下一次触发可正常进入
            if (guarded) {
                releaseRunning(definition.getId());
            }
        }
    }

    /**
     * CAS 置位"该任务执行中"，成功表示本节点此前无同任务在执行。
     *
     * @param jobId 任务 ID
     * @return 是否成功抢占（false 表示同任务已在执行，本次应跳过）
     */
    private boolean markRunning(Long jobId) {
        AtomicBoolean flag = runningJobs.computeIfAbsent(jobId, id -> new AtomicBoolean(false));
        return flag.compareAndSet(false, true);
    }

    /**
     * 清除"该任务执行中"标志，供执行结束后的 finally 调用。
     *
     * @param jobId 任务 ID
     */
    private void releaseRunning(Long jobId) {
        AtomicBoolean flag = runningJobs.get(jobId);
        if (flag != null) {
            flag.set(false);
        }
    }

    /**
     * 校验任务定义是否可执行，不会注册或触发调度。
     *
     * @param definition 任务定义
     */
    public void validateDefinition(JobDefinition definition) {
        validate(definition);
    }

    private void validate(JobDefinition definition) {
        if (definition.getId() == null) {
            throw new IllegalArgumentException("任务 ID 不能为空");
        }
        if (definition.getExecutor() == null || definition.getExecutor().isBlank()) {
            throw new IllegalArgumentException("任务执行器不能为空");
        }
        if (!handlers.containsKey(definition.getExecutor())) {
            throw new IllegalArgumentException("任务执行器不存在：" + definition.getExecutor());
        }
        boolean cronTrigger = definition.isCronTrigger();
        boolean fixedRateTrigger = definition.getFixedRateSeconds() != null
            && definition.getFixedRateSeconds() > 0;
        if (cronTrigger == fixedRateTrigger) {
            throw new IllegalArgumentException("任务须且只能指定 cron 或正的固定频率秒数之一");
        }
        if (cronTrigger) {
            String cron = definition.getCron();
            if (cron == null || cron.isBlank()) {
                throw new IllegalArgumentException("任务标记为 cron 触发但未提供 cron 表达式");
            }
            cronService.validate(cron);
        }
    }

    /**
     * 分布式锁抽象：解耦 tools 模块，存在 LockService 时桥接，否则单机无锁。
     */
    public interface JobLock {
        boolean tryLock(String key, String owner, Duration ttl);

        boolean unlock(String key, String owner);
    }
}

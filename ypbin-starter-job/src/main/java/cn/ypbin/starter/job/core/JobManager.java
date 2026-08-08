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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.PeriodicTrigger;

/**
 * 定时任务调度管理器。
 *
 * <p>基于 Spring {@link TaskScheduler} + 自维护 {@link ScheduledFuture} 注册表实现运行时动态调度：
 * 注册/启停/改 cron/立即执行一次，均不需重启。多实例下每个节点各自持有调度器，同一任务到点会各自触发，
 * 故执行入口用分布式锁抢占（锁键带触发时间片），只有抢到的节点真正执行，实现集群防重。</p>
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
    private final Map<String, JobHandler> handlers;
    private final JobExecutionListener listener;
    private final JobLock jobLock;
    private final CronService cronService;

    /** jobId -> 运行时调度句柄 */
    private final Map<Long, Scheduled> registry = new ConcurrentHashMap<>();

    private record Scheduled(JobDefinition definition, ScheduledFuture<?> future) {
    }

    public JobManager(String nodeId, TaskScheduler taskScheduler, Map<String, JobHandler> handlers,
        JobExecutionListener listener, JobLock jobLock, CronService cronService) {
        this.nodeId = nodeId;
        this.taskScheduler = taskScheduler;
        this.handlers = handlers;
        this.listener = listener;
        this.jobLock = jobLock;
        this.cronService = cronService;
    }

    /**
     * 注册（或替换）一个任务并开始调度。已存在同 ID 任务时先取消再注册（用于 cron/参数变更即时生效）。
     *
     * @param definition 任务定义
     */
    public synchronized void register(JobDefinition definition) {
        validate(definition);
        cancel(definition.getId());
        Runnable task = () -> runWithGuard(definition, false);
        ScheduledFuture<?> future = definition.isCronTrigger()
            ? taskScheduler.schedule(task, new CronTrigger(definition.getCron()))
            : taskScheduler.schedule(task, new PeriodicTrigger(Duration.ofSeconds(definition.getFixedRateSeconds())));
        registry.put(definition.getId(), new Scheduled(definition, future));
        log.info("[ypbin-starter] job registered: id={}, name={}, cron={}, rate={}s.",
            definition.getId(), definition.getName(), definition.getCron(), definition.getFixedRateSeconds());
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
        taskScheduler.schedule(() -> runWithGuard(scheduled.definition(), true), Instant.now());
    }

    /**
     * 用给定定义手动执行一次（任务未注册调度时也可临时跑，如「测试执行」）。
     *
     * @param definition 任务定义
     */
    public void triggerNow(JobDefinition definition) {
        validate(definition);
        taskScheduler.schedule(() -> runWithGuard(definition, true), Instant.now());
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

    private void cancel(Long jobId) {
        Scheduled scheduled = registry.remove(jobId);
        if (scheduled != null) {
            scheduled.future().cancel(false);
        }
    }

    /**
     * 执行入口：集群防重抢锁 → 找执行体 → 回调监听 → 执行。
     */
    private void runWithGuard(JobDefinition definition, boolean manual) {
        JobContext context = new JobContext(definition.getId(), definition.getName(), definition.getExecutor(),
            definition.getArgs(), manual, LocalDateTime.now());

        JobHandler handler = handlers.get(definition.getExecutor());
        if (handler == null) {
            log.warn("[ypbin-starter] job executor not found: {} (job={})", definition.getExecutor(),
                definition.getName());
            listener.onError(context, 0L, new IllegalStateException("执行器不存在：" + definition.getExecutor()));
            return;
        }

        // 集群防重：锁键带触发时间片，避免长任务持锁挡住下一次正常触发
        String lockKey = null;
        boolean locked = false;
        if (definition.isConcurrentGuard()) {
            lockKey = "ypbin:job:" + definition.getId() + ":" + context.getTriggerTime().withNano(0);
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
            log.warn("[ypbin-starter] job execute failed: name={}, err={}", definition.getName(), e.getMessage());
            listener.onError(context, System.currentTimeMillis() - start, e);
        } finally {
            if (locked) {
                jobLock.unlock(lockKey, nodeId);
            }
        }
    }

    private void validate(JobDefinition definition) {
        if (definition.getId() == null) {
            throw new IllegalArgumentException("任务 ID 不能为空");
        }
        if (definition.getExecutor() == null || definition.getExecutor().isBlank()) {
            throw new IllegalArgumentException("任务执行器不能为空");
        }
        if (definition.isCronTrigger()) {
            cronService.validate(definition.getCron());
        } else if (definition.getFixedRateSeconds() == null || definition.getFixedRateSeconds() <= 0) {
            throw new IllegalArgumentException("任务须指定 cron 或正的固定间隔秒数");
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

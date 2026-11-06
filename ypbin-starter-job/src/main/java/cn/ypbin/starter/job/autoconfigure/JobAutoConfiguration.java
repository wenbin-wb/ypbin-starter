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
package cn.ypbin.starter.job.autoconfigure;

import cn.ypbin.starter.job.core.CronService;
import cn.ypbin.starter.job.core.JobExecutionListener;
import cn.ypbin.starter.job.core.JobHandler;
import cn.ypbin.starter.job.core.JobManager;
import cn.ypbin.starter.job.core.SpringCronService;
import cn.ypbin.starter.job.core.YpbinJob;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 定时任务自动配置。
 *
 * <p>仅当 {@code ypbin.job.enabled=true}（默认）时装配。收集所有 {@link YpbinJob} 标注的 {@link JobHandler}
 * 按执行器名索引；装配调度线程池、执行监听（默认空实现，业务方覆盖以落库）、集群防重锁桥接（存在
 * tools 的 {@code LockService} 时用之，否则单机无锁），以及核心 {@link JobManager}。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = JobProperties.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(JobProperties.class)
public class JobAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JobAutoConfiguration.class);

    /**
     * 旧签名：保留二进制兼容，实际装配由 {@link #jobExecutor(JobProperties)} 完成。
     *
     * @return 按默认配置创建的执行线程池
     * @deprecated 请使用 {@link #jobExecutor(JobProperties)}（本方法不再注册 Bean）
     */
    @Deprecated
    public Executor jobExecutor() {
        return createJobExecutor(new JobProperties());
    }

    /**
     * 任务执行线程池（与调度线程池隔离）。
     *
     * <p>执行体跑在虚拟线程上，每个任务独占一条虚拟线程；调度线程只负责触发和提交，不直接执行业务
     * handler，避免单个阻塞任务堵住调度器、导致其他 cron/fixed-rate 任务延迟触发。</p>
     *
     * <p><b>并发与排队必须有界</b>：原先用 {@code SimpleAsyncTaskExecutor} 无界创建线程，一个每秒
     * 触发、执行缓慢且 {@code concurrentGuard=false} 的任务可以无限堆叠执行体直至内存耗尽。现改为
     * 「有界线程池 + 虚拟线程工厂 + 拒绝即跳过」（{@link JobProperties.ExecutorProperties}），
     * 拒绝的语义与日志由 {@code JobManager} 负责（定时触发不抛异常到调度线程，避免任务静默停摆）。</p>
     */
    @Bean(name = "jobExecutor")
    @ConditionalOnMissingBean(name = "jobExecutor")
    public Executor jobExecutor(JobProperties properties) {
        return createJobExecutor(properties);
    }

    private static Executor createJobExecutor(JobProperties properties) {
        JobProperties.ExecutorProperties pool = properties.getExecutor();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("ypbin-job-exec-");
        executor.setVirtualThreads(true);
        executor.setCorePoolSize(Math.max(1, pool.getMaxConcurrency()));
        executor.setMaxPoolSize(Math.max(1, pool.getMaxConcurrency()));
        executor.setQueueCapacity(Math.max(0, pool.getQueueCapacity()));
        // 拒绝而不是 CallerRuns：CallerRuns 会让调度线程执行阻塞任务，堵死后续触发
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * 调度线程池。
     */
    @Bean
    @ConditionalOnMissingBean(name = "jobTaskScheduler")
    public TaskScheduler jobTaskScheduler(JobProperties properties) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(properties.getPoolSize());
        scheduler.setThreadNamePrefix(properties.getThreadNamePrefix());
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.initialize();
        return scheduler;
    }

    /**
     * 默认执行监听：空实现。业务方提供自定义实现即可把执行记录落库。
     */
    @Bean
    @ConditionalOnMissingBean
    public JobExecutionListener jobExecutionListener() {
        return new JobExecutionListener() {
        };
    }

    /**
     * 集群防重锁：桥接 tools 的 LockService；未引入 tools 时退化为单机无锁（永远抢锁成功）。
     */
    @Bean
    @ConditionalOnMissingBean(JobManager.JobLock.class)
    public JobManager.JobLock jobLock(ApplicationContext applicationContext) {
        return JobLockFactory.create(applicationContext);
    }

    /**
     * Spring Cron 表达式校验与执行时间计算服务。
     */
    @Bean
    @ConditionalOnMissingBean
    public CronService cronService() {
        return new SpringCronService();
    }

    /**
     * 任务调度管理器。
     */
    @Bean
    @ConditionalOnMissingBean
    public JobManager jobManager(TaskScheduler jobTaskScheduler, Executor jobExecutor,
            JobExecutionListener listener,
            JobManager.JobLock jobLock, CronService cronService, ApplicationContext applicationContext) {
        Map<String, JobHandler> handlers = collectHandlers(applicationContext);
        String nodeId = UUID.randomUUID().toString().replace("-", "");
        log.info("[ypbin-starter] job manager initialized, executors={}, nodeId={}.", handlers.keySet(), nodeId);
        return new JobManager(nodeId, jobTaskScheduler, jobExecutor, handlers, listener, jobLock, cronService);
    }

    /**
     * 收集所有 @YpbinJob 标注的 JobHandler，按执行器名索引。
     */
    private Map<String, JobHandler> collectHandlers(ApplicationContext ctx) {
        Map<String, JobHandler> handlers = new LinkedHashMap<>();
        for (JobHandler handler : ctx.getBeansOfType(JobHandler.class).values()) {
            YpbinJob anno = AnnotationUtils.findAnnotation(AopUtils.getTargetClass(handler), YpbinJob.class);
            if (anno == null || anno.value().isBlank()) {
                log.warn("[ypbin-starter] JobHandler {} 缺少 @YpbinJob 名称，已跳过。",
                    handler.getClass().getName());
                continue;
            }
            JobHandler existing = handlers.putIfAbsent(anno.value(), handler);
            if (existing != null) {
                throw new IllegalStateException("重复的任务执行器名称：" + anno.value());
            }
        }
        return handlers;
    }
}

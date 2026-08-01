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

import cn.ypbin.starter.job.core.JobExecutionListener;
import cn.ypbin.starter.job.core.JobHandler;
import cn.ypbin.starter.job.core.JobManager;
import cn.ypbin.starter.job.core.YpbinJob;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
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
@ConditionalOnProperty(prefix = "ypbin.job", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(JobProperties.class)
public class JobAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JobAutoConfiguration.class);

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
     * 任务调度管理器。
     */
    @Bean
    @ConditionalOnMissingBean
    public JobManager jobManager(TaskScheduler jobTaskScheduler, JobExecutionListener listener,
        JobManager.JobLock jobLock, ApplicationContext applicationContext) {
        Map<String, JobHandler> handlers = collectHandlers(applicationContext);
        String nodeId = UUID.randomUUID().toString().replace("-", "");
        log.info("[ypbin-starter] job manager initialized, executors={}, nodeId={}.", handlers.keySet(), nodeId);
        return new JobManager(nodeId, jobTaskScheduler, handlers, listener, jobLock);
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

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
package cn.ypbin.starter.async.autoconfigure;

import cn.ypbin.starter.async.core.LoggingAsyncUncaughtExceptionHandler;
import cn.ypbin.starter.async.util.AsyncHolder;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 异步执行自动配置。
 *
 * <p>装配统一线程池 {@code ypbinTaskExecutor}、调度器 {@code ypbinTaskScheduler}，绑定
 * {@link AsyncHolder} 供 AsyncUtils 静态使用，并按需接管 {@code @Async}。线程池自动挂载容器内的
 * {@link TaskDecorator}（core 提供上下文透传装饰器），使租户/用户/MDC 透传到异步线程。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = AsyncProperties.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AsyncProperties.class)
public class AsyncAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AsyncAutoConfiguration.class);

    /**
     * 统一业务线程池。
     */
    @Bean(name = "ypbinTaskExecutor")
    @ConditionalOnMissingBean(name = "ypbinTaskExecutor")
    public ThreadPoolTaskExecutor ypbinTaskExecutor(AsyncProperties properties,
            ObjectProvider<TaskDecorator> taskDecorator) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getCoreSize());
        executor.setMaxPoolSize(properties.getMaxSize());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setKeepAliveSeconds(properties.getKeepAliveSeconds());
        executor.setAllowCoreThreadTimeOut(properties.isAllowCoreThreadTimeout());
        executor.setThreadNamePrefix(properties.getThreadNamePrefix());
        executor.setWaitForTasksToCompleteOnShutdown(properties.isAwaitTermination());
        executor.setAwaitTerminationSeconds(properties.getAwaitTerminationSeconds());
        executor.setRejectedExecutionHandler(resolveRejectionHandler(properties.getRejectionPolicy()));
        if (properties.isVirtualThreads()) {
            executor.setVirtualThreads(true);
            log.warn("[ypbin-starter] 已启用虚拟线程，线程池的 core-size/max-size/queue-capacity/"
                + "keep-alive-seconds/rejection-policy 等池化参数将不生效。");
        }
        taskDecorator.ifAvailable(executor::setTaskDecorator);
        executor.initialize();
        return executor;
    }

    /**
     * 统一任务调度器。
     */
    @Bean(name = "ypbinTaskScheduler")
    @ConditionalOnMissingBean(name = "ypbinTaskScheduler")
    public ThreadPoolTaskScheduler ypbinTaskScheduler(AsyncProperties properties) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(properties.getSchedulerPoolSize());
        scheduler.setThreadNamePrefix(properties.getSchedulerThreadNamePrefix());
        scheduler.setWaitForTasksToCompleteOnShutdown(properties.isAwaitTermination());
        scheduler.setAwaitTerminationSeconds(properties.getAwaitTerminationSeconds());
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.initialize();
        return scheduler;
    }

    /**
     * 异步异常处理器。
     */
    @Bean
    @ConditionalOnMissingBean(AsyncUncaughtExceptionHandler.class)
    public AsyncUncaughtExceptionHandler asyncUncaughtExceptionHandler() {
        return new LoggingAsyncUncaughtExceptionHandler();
    }

    /**
     * 绑定静态持有者，供 AsyncUtils 使用。
     */
    @Bean
    public AsyncHolderInitializer asyncHolderInitializer(
            @org.springframework.beans.factory.annotation.Qualifier("ypbinTaskExecutor") Executor executor,
            @org.springframework.beans.factory.annotation.Qualifier("ypbinTaskScheduler") TaskScheduler scheduler) {
        AsyncHolder.bind(executor, scheduler);
        return new AsyncHolderInitializer();
    }

    private java.util.concurrent.RejectedExecutionHandler resolveRejectionHandler(
            AsyncProperties.RejectionPolicy policy) {
        return switch (policy) {
            case ABORT -> new ThreadPoolExecutor.AbortPolicy();
            case DISCARD -> new ThreadPoolExecutor.DiscardPolicy();
            case DISCARD_OLDEST -> new ThreadPoolExecutor.DiscardOldestPolicy();
            case CALLER_RUNS -> new ThreadPoolExecutor.CallerRunsPolicy();
        };
    }

    /**
     * 空标记 Bean，仅用于触发 {@link AsyncHolder#bind}。
     */
    public static final class AsyncHolderInitializer {
    }
}

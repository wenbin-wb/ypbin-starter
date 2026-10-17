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
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 异步执行自动配置。
 *
 * <p>装配统一执行器 {@code ypbinTaskExecutor}、调度器 {@code ypbinTaskScheduler}，绑定
 * {@link AsyncHolder} 供 AsyncUtils 静态使用，并按需接管 {@code @Async}。
 *
 * <p>执行器有两种模式（由 {@code ypbin.async.virtual-threads} 控制）：
 * <ul>
 *   <li><b>虚拟线程模式（默认，JDK 21+）</b>：使用 {@link SimpleAsyncTaskExecutor} + {@link Thread#ofVirtual()}
 *       工厂，每次提交创建一个虚拟线程，无池化上限，适合 I/O 密集型任务。池化参数（core-size / max-size /
 *       queue-capacity / rejection-policy）在此模式下不生效。</li>
 *   <li><b>平台线程模式</b>：使用有界 {@link ThreadPoolTaskExecutor}，pool / queue / rejection 参数全部生效，
 *       适合 CPU 密集或需要明确背压控制的任务。</li>
 * </ul>
 *
 * <p>两种模式下 {@link TaskDecorator}（core 提供上下文透传装饰器）均会挂载，确保租户/用户/MDC 透传到异步线程。
 *
 * @author wenbin
 * @since 2026-08-15
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = AsyncProperties.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AsyncProperties.class)
public class AsyncAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AsyncAutoConfiguration.class);

    /**
     * 秒 → 毫秒换算系数。
     *
     * <p>{@code AsyncProperties#getAwaitTerminationSeconds()} 以秒为单位，
     * 而 {@code SimpleAsyncTaskExecutor#setTaskTerminationTimeout(long)} 以<strong>毫秒</strong>为单位
     * （Spring Framework 7.0.9 源码：{@code setTaskTerminationTimeout} 的 Javadoc 明确 “the timeout in
     * milliseconds”，{@code close()} 中以 {@code threads.wait(timeout)} 消费该值）。
     */
    private static final long MILLIS_PER_SECOND = 1000L;

    /**
     * 统一业务执行器。
     *
     * <p>虚拟线程模式：{@link SimpleAsyncTaskExecutor} + JDK 21 {@link Thread#ofVirtual()} 工厂，
     * 无池化上限，直接利用平台虚拟线程调度，零空闲线程开销，适合大量短生命周期 I/O 任务。
     * 平台线程模式：有界 {@link ThreadPoolTaskExecutor}，所有池化参数生效。
     *
     * <p><b>两种模式的停机语义并不一致，差异只在「等待超时后是否中断剩余线程」</b>——以下均依
     * Spring Framework 7.0.9 字节码实测（{@code javap -c SimpleAsyncTaskExecutor#close()}、
     * {@code javap -c ExecutorConfigurationSupport#shutdown()}），接线时切勿为追求「一致」而改写：
     * <ul>
     *   <li><b>虚拟线程模式（销毁走 {@link SimpleAsyncTaskExecutor#close()}，Spring 按
     *       {@code AutoCloseable} 推导的 destroy 方法）</b>：
     *       等待时长来自 {@code await-termination-seconds}（接线处换算为
     *       {@code setTaskTerminationTimeout} 的毫秒值）。{@code close()} 在
     *       {@code taskTerminationTimeout > 0} 时等待 {@code activeThreads} 至多该时长，<b>等待结束后无论
     *       是否还剩线程，都会对剩余活动线程逐个 {@code interrupt()}</b>（本仓未开
     *       {@code cancelRemainingTasksOnClose}，走的是 {@code close()} 末尾那段「未取消则中断」逻辑）。
     *       即：<b>虚拟线程模式受 Spring API 固有语义限制——等待超时后必然中断</b>，无法配置成「只告警不中断」。
     *       {@code await-termination-seconds=0} ⇒ timeout=0 ⇒ {@code close()} 直接返回，<b>既不等待也不中断</b>。
     *       {@code await-termination=false} 时接 {@code setCancelRemainingTasksOnClose(true)}：{@code close()}
     *       立即中断在途线程（对齐平台线程池的 {@code shutdownNow()}），不等待。</li>
     *   <li><b>平台线程模式（销毁走
     *       {@link org.springframework.scheduling.concurrent.ExecutorConfigurationSupport#shutdown()}）</b>：
     *       等待时长来自 {@code await-termination-seconds}（{@code setAwaitTerminationSeconds}）。
     *       {@code await-termination=true} ⇒ {@code waitForTasksToCompleteOnShutdown=true}：先
     *       {@code ExecutorService.shutdown()} 优雅停机（不中断），再在
     *       {@code awaitTerminationIfNecessary} 中等待；<b>超时后只 {@code logger.warn}
     *       （“did not terminate within …”），不会中断剩余线程</b>。
     *       {@code await-termination-seconds=0} ⇒ 不等待、不中断。
     *       {@code await-termination=false} ⇒ {@code waitForTasksToCompleteOnShutdown=false}：直接
     *       {@code shutdownNow()} 并对排空出的任务调用 {@code cancelRemainingTask}（{@code Future.cancel(true)}
     *       ⇒ 中断在途任务）。</li>
     * </ul>
     */
    @Bean(name = "ypbinTaskExecutor")
    @ConditionalOnMissingBean(name = "ypbinTaskExecutor")
    public Executor ypbinTaskExecutor(AsyncProperties properties,
            ObjectProvider<TaskDecorator> taskDecorator) {
        if (properties.isVirtualThreads()) {
            SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor(properties.getThreadNamePrefix());
            executor.setVirtualThreads(true);
            if (properties.isAwaitTermination()) {
                // 单位毫秒；0 表示不等待（与平台线程池 awaitTerminationMillis=0 的语义一致）
                executor.setTaskTerminationTimeout(
                    properties.getAwaitTerminationSeconds() * MILLIS_PER_SECOND);
            } else {
                // 不等待时与平台线程池的 shutdownNow() 对齐：close() 立即中断在途线程
                executor.setCancelRemainingTasksOnClose(true);
            }
            taskDecorator.ifAvailable(executor::setTaskDecorator);
            log.info("[ypbin-starter] 已启用虚拟线程执行器（{}），池化参数不生效。",
                properties.getThreadNamePrefix() + "*");
            return executor;
        }
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
        taskDecorator.ifAvailable(executor::setTaskDecorator);
        executor.initialize();
        log.info("[ypbin-starter] 已启用平台线程执行器，core={} max={} queue={} policy={}。",
            properties.getCoreSize(), properties.getMaxSize(),
            properties.getQueueCapacity(), properties.getRejectionPolicy());
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
            @Qualifier("ypbinTaskExecutor") Executor executor,
            @Qualifier("ypbinTaskScheduler") TaskScheduler scheduler) {
        AsyncHolder.bind(executor, scheduler);
        return new AsyncHolderInitializer();
    }

    private RejectedExecutionHandler resolveRejectionHandler(
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

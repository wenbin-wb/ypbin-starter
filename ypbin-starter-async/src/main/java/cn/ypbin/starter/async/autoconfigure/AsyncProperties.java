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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 异步执行配置项。
 *
 * @author wenbin
 * @since 2026-07-31
 */
@ConfigurationProperties(prefix = AsyncProperties.PREFIX)
public class AsyncProperties {

    public static final String PREFIX = "ypbin.async";

    /** 是否启用异步能力，默认开启 */
    private boolean enabled = true;

    /** 是否接管 {@code @Async}（启用 @EnableAsync 并把默认执行器指向本模块线程池），默认开启 */
    private boolean enableAnnotation = true;

    /** 是否优先使用虚拟线程（JDK 21+ 生效）；开启后 taskExecutor 使用虚拟线程执行器 */
    private boolean virtualThreads = false;

    /** 核心线程数 */
    private int coreSize = 8;

    /** 最大线程数 */
    private int maxSize = 32;

    /** 队列容量 */
    private int queueCapacity = 1000;

    /** 空闲线程存活秒数 */
    private int keepAliveSeconds = 60;

    /** 是否允许核心线程超时回收 */
    private boolean allowCoreThreadTimeout = false;

    /** 线程名前缀 */
    private String threadNamePrefix = "ypbin-async-";

    /** 拒绝策略 */
    private RejectionPolicy rejectionPolicy = RejectionPolicy.CALLER_RUNS;

    /** 关闭时是否等待任务执行完 */
    private boolean awaitTermination = true;

    /** 关闭时最长等待秒数 */
    private int awaitTerminationSeconds = 30;

    /** 调度线程池大小（用于 TaskScheduler） */
    private int schedulerPoolSize = 2;

    /** 调度线程名前缀 */
    private String schedulerThreadNamePrefix = "ypbin-scheduler-";

    /**
     * 线程池拒绝策略。
     */
    public enum RejectionPolicy {
        /** 由提交任务的线程直接执行，形成反压 */
        CALLER_RUNS,
        /** 抛出 RejectedExecutionException */
        ABORT,
        /** 丢弃当前任务，不抛异常 */
        DISCARD,
        /** 丢弃队列中最老的任务后重试入队 */
        DISCARD_OLDEST
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnableAnnotation() {
        return enableAnnotation;
    }

    public void setEnableAnnotation(boolean enableAnnotation) {
        this.enableAnnotation = enableAnnotation;
    }

    public boolean isVirtualThreads() {
        return virtualThreads;
    }

    public void setVirtualThreads(boolean virtualThreads) {
        this.virtualThreads = virtualThreads;
    }

    public int getCoreSize() {
        return coreSize;
    }

    public void setCoreSize(int coreSize) {
        this.coreSize = coreSize;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public int getKeepAliveSeconds() {
        return keepAliveSeconds;
    }

    public void setKeepAliveSeconds(int keepAliveSeconds) {
        this.keepAliveSeconds = keepAliveSeconds;
    }

    public boolean isAllowCoreThreadTimeout() {
        return allowCoreThreadTimeout;
    }

    public void setAllowCoreThreadTimeout(boolean allowCoreThreadTimeout) {
        this.allowCoreThreadTimeout = allowCoreThreadTimeout;
    }

    public String getThreadNamePrefix() {
        return threadNamePrefix;
    }

    public void setThreadNamePrefix(String threadNamePrefix) {
        this.threadNamePrefix = threadNamePrefix;
    }

    public RejectionPolicy getRejectionPolicy() {
        return rejectionPolicy;
    }

    public void setRejectionPolicy(RejectionPolicy rejectionPolicy) {
        this.rejectionPolicy = rejectionPolicy;
    }

    public boolean isAwaitTermination() {
        return awaitTermination;
    }

    public void setAwaitTermination(boolean awaitTermination) {
        this.awaitTermination = awaitTermination;
    }

    public int getAwaitTerminationSeconds() {
        return awaitTerminationSeconds;
    }

    public void setAwaitTerminationSeconds(int awaitTerminationSeconds) {
        this.awaitTerminationSeconds = awaitTerminationSeconds;
    }

    public int getSchedulerPoolSize() {
        return schedulerPoolSize;
    }

    public void setSchedulerPoolSize(int schedulerPoolSize) {
        this.schedulerPoolSize = schedulerPoolSize;
    }

    public String getSchedulerThreadNamePrefix() {
        return schedulerThreadNamePrefix;
    }

    public void setSchedulerThreadNamePrefix(String schedulerThreadNamePrefix) {
        this.schedulerThreadNamePrefix = schedulerThreadNamePrefix;
    }
}

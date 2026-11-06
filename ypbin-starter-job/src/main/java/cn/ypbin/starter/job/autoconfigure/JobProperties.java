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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 定时任务配置项。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@ConfigurationProperties(prefix = JobProperties.PREFIX)
public class JobProperties {

    public static final String PREFIX = "ypbin.job";

    /** 是否启用定时任务调度，默认开启 */
    private boolean enabled = true;

    /** 调度线程池大小 */
    private int poolSize = 4;

    /** 线程名前缀 */
    private String threadNamePrefix = "ypbin-job-";

    /** 任务执行线程池（与调度线程池隔离）。 */
    private final ExecutorProperties executor = new ExecutorProperties();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPoolSize() {
        return poolSize;
    }

    public void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
    }

    public String getThreadNamePrefix() {
        return threadNamePrefix;
    }

    public void setThreadNamePrefix(String threadNamePrefix) {
        this.threadNamePrefix = threadNamePrefix;
    }

    public ExecutorProperties getExecutor() {
        return executor;
    }

    /**
     * 任务执行线程池配置。
     *
     * <p>执行体跑在虚拟线程上，但并发数与排队数<b>必须有界</b>：无界创建虚拟线程时，一个每秒触发、
     * 执行缓慢且关闭了防重（{@code concurrentGuard=false}）的任务可以无限堆叠执行体，最终拖垮进程。
     * 队列满后按「拒绝」处理：定时触发跳过本次并回调 {@code onSkip}，手动触发抛异常。</p>
     */
    public static class ExecutorProperties {

        /** 同时执行的任务数上限，默认 64 */
        private int maxConcurrency = 64;

        /** 等待执行的任务数上限，默认 256；超出即拒绝 */
        private int queueCapacity = 256;

        public int getMaxConcurrency() {
            return maxConcurrency;
        }

        public void setMaxConcurrency(int maxConcurrency) {
            this.maxConcurrency = maxConcurrency;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }
    }
}

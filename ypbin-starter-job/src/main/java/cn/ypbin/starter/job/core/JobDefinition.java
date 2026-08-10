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

/**
 * 任务调度定义。
 *
 * <p>业务方从任务表读出记录后转为本对象交给 {@link JobManager} 注册调度。starter 不持久化任务，
 * 只按本定义在内存中维护调度；持久化、CRUD、页面由业务方实现。</p>
 *
 * <p>触发方式二选一：{@link #cron} 非空走 cron 触发；否则按 {@link #fixedRateSeconds} 固定间隔触发。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public class JobDefinition {

    /** 任务 ID（业务任务表主键，作为调度注册表的键，必填） */
    private Long id;

    /** 任务名称 */
    private String name;

    /** 执行器名称，对应 {@link YpbinJob#value()} */
    private String executor;

    /** cron 表达式（与 fixedRateSeconds 二选一） */
    private String cron;

    /** 固定频率秒数（与 cron 二选一） */
    private Long fixedRateSeconds;

    /** 执行参数（业务自定义，透传给 {@link JobContext#getArgs()}） */
    private String args;

    /** 执行超时秒数，<=0 不限制 */
    private long timeoutSeconds;

    /** 是否启用集群防重（多实例只跑一个），默认 true */
    private boolean concurrentGuard = true;

    public JobDefinition() {
    }

    public JobDefinition(Long id, String name, String executor, String cron) {
        this.id = id;
        this.name = name;
        this.executor = executor;
        this.cron = cron;
    }

    /**
     * 是否 cron 触发（否则固定间隔）。
     *
     * @return 是否 cron 触发
     */
    public boolean isCronTrigger() {
        return cron != null && !cron.isBlank();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getExecutor() {
        return executor;
    }

    public void setExecutor(String executor) {
        this.executor = executor;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public Long getFixedRateSeconds() {
        return fixedRateSeconds;
    }

    public void setFixedRateSeconds(Long fixedRateSeconds) {
        this.fixedRateSeconds = fixedRateSeconds;
    }

    public String getArgs() {
        return args;
    }

    public void setArgs(String args) {
        this.args = args;
    }

    public long getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public boolean isConcurrentGuard() {
        return concurrentGuard;
    }

    public void setConcurrentGuard(boolean concurrentGuard) {
        this.concurrentGuard = concurrentGuard;
    }
}

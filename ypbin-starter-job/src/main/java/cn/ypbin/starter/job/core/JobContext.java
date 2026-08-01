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

import java.time.LocalDateTime;

/**
 * 任务执行上下文。
 *
 * <p>传入 {@link JobHandler#execute(JobContext)}，携带本次执行的任务标识、参数与触发方式。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public class JobContext {

    /** 任务 ID（业务任务表主键，可空） */
    private final Long jobId;

    /** 任务名称 */
    private final String jobName;

    /** 执行器名称（对应 {@link YpbinJob#value()} / 定义中的 executor） */
    private final String executor;

    /** 执行参数（业务自定义格式，通常为 JSON 或简单串，可空） */
    private final String args;

    /** 是否手动触发（true=手动立即执行，false=定时触发） */
    private final boolean manual;

    /** 本次触发时间 */
    private final LocalDateTime triggerTime;

    public JobContext(Long jobId, String jobName, String executor, String args, boolean manual,
        LocalDateTime triggerTime) {
        this.jobId = jobId;
        this.jobName = jobName;
        this.executor = executor;
        this.args = args;
        this.manual = manual;
        this.triggerTime = triggerTime;
    }

    public Long getJobId() {
        return jobId;
    }

    public String getJobName() {
        return jobName;
    }

    public String getExecutor() {
        return executor;
    }

    public String getArgs() {
        return args;
    }

    public boolean isManual() {
        return manual;
    }

    public LocalDateTime getTriggerTime() {
        return triggerTime;
    }
}

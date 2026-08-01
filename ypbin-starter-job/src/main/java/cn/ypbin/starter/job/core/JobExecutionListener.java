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
 * 任务执行监听扩展点。
 *
 * <p>调度器在任务执行的各阶段回调，业务方实现本接口把执行记录落库（如 sys_job_log）。starter 只回调，
 * 不持久化。未提供实现时使用默认空实现。所有回调都应轻量、吞掉自身异常，不得影响任务本身执行。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public interface JobExecutionListener {

    /**
     * 任务开始执行前回调。
     *
     * @param context 执行上下文
     */
    default void onStart(JobContext context) {
    }

    /**
     * 任务执行成功后回调。
     *
     * @param context     执行上下文
     * @param durationMs  执行耗时（毫秒）
     */
    default void onSuccess(JobContext context, long durationMs) {
    }

    /**
     * 任务执行失败回调。
     *
     * @param context    执行上下文
     * @param durationMs 执行耗时（毫秒）
     * @param error      异常
     */
    default void onError(JobContext context, long durationMs, Throwable error) {
    }

    /**
     * 因集群防重未抢到锁而跳过本次执行时回调（多实例下非执行节点）。
     *
     * @param context 执行上下文
     */
    default void onSkip(JobContext context) {
    }
}

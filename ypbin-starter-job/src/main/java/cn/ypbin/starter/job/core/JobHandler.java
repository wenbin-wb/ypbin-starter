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
 * 定时任务执行体。
 *
 * <p>业务方将任务逻辑实现为一个 Spring 组件并实现本接口，用 {@link YpbinJob} 声明执行器名称；
 * 调度时按名称路由到对应实现执行。执行器名称即业务任务表中记录的「执行器标识」。</p>
 *
 * <pre>{@code
 * @Component
 * @YpbinJob("cleanTempFile")
 * public class CleanTempFileJob implements JobHandler {
 *     @Override
 *     public void execute(JobContext context) {
 *         // 清理逻辑，可读 context.getArgs()
 *     }
 * }
 * }</pre>
 *
 * @author wenbin
 * @since 2026-08-01
 */
@FunctionalInterface
public interface JobHandler {

    /**
     * 执行任务。
     *
     * @param context 执行上下文
     * @throws Exception 执行异常将被调度器捕获并回调 {@link JobExecutionListener#onError}
     */
    void execute(JobContext context) throws Exception;
}

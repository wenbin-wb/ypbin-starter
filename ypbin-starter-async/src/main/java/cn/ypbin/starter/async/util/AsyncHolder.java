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
package cn.ypbin.starter.async.util;

import java.util.concurrent.Executor;
import org.springframework.scheduling.TaskScheduler;

/**
 * 异步执行器静态持有者。
 *
 * <p>由自动配置在容器启动时注入执行器与调度器，供 {@link AsyncUtils} 在非托管环境静态使用。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
public final class AsyncHolder {

    private static Executor executor;

    private static TaskScheduler scheduler;

    private AsyncHolder() {
    }

    public static void bind(Executor executor, TaskScheduler scheduler) {
        AsyncHolder.executor = executor;
        AsyncHolder.scheduler = scheduler;
    }

    public static Executor getExecutor() {
        if (executor == null) {
            throw new IllegalStateException(
                "异步执行器尚未初始化：请确认已引入 ypbin-starter-async 且 ypbin.async.enabled=true");
        }
        return executor;
    }

    public static TaskScheduler getScheduler() {
        if (scheduler == null) {
            throw new IllegalStateException(
                "任务调度器尚未初始化：请确认已引入 ypbin-starter-async 且 ypbin.async.enabled=true");
        }
        return scheduler;
    }

    public static boolean isReady() {
        return executor != null;
    }
}

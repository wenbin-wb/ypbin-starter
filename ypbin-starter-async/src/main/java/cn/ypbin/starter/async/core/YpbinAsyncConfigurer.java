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
package cn.ypbin.starter.async.core;

import java.util.concurrent.Executor;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.scheduling.annotation.AsyncConfigurer;

/**
 * 异步注解配置器。
 *
 * <p>把 {@code @Async} 的默认执行器指向本模块统一线程池，并统一异步异常处理。业务方仍可通过
 * {@code @Async("otherExecutor")} 指定其它执行器。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
public class YpbinAsyncConfigurer implements AsyncConfigurer {

    private final Executor taskExecutor;

    private final AsyncUncaughtExceptionHandler exceptionHandler;

    public YpbinAsyncConfigurer(Executor taskExecutor, AsyncUncaughtExceptionHandler exceptionHandler) {
        this.taskExecutor = taskExecutor;
        this.exceptionHandler = exceptionHandler;
    }

    @Override
    public Executor getAsyncExecutor() {
        return taskExecutor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return exceptionHandler;
    }
}

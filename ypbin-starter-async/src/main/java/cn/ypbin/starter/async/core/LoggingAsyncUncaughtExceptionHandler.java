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

import java.lang.reflect.Method;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;

/**
 * 记录日志的异步异常处理器。
 *
 * <p>{@code @Async} 返回 void 的方法抛出的异常默认会被 Spring 静默吞掉。本处理器统一记录方法名、
 * 入参与堆栈，避免异步异常丢失。返回 Future 的方法异常仍由调用方通过 Future 感知，不在此处理。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
public class LoggingAsyncUncaughtExceptionHandler implements AsyncUncaughtExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(LoggingAsyncUncaughtExceptionHandler.class);

    @Override
    public void handleUncaughtException(Throwable ex, Method method, Object... params) {
        log.error("[ypbin-starter] 异步任务执行异常：method={}#{}, params={}",
            method.getDeclaringClass().getSimpleName(), method.getName(), Arrays.toString(params), ex);
    }
}

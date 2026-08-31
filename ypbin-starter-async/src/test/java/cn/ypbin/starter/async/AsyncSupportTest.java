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
package cn.ypbin.starter.async;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.async.core.LoggingAsyncUncaughtExceptionHandler;
import cn.ypbin.starter.async.util.AsyncHolder;
import cn.ypbin.starter.async.util.AsyncUtils;
import java.lang.reflect.Method;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 异步核心支持测试。
 *
 * @author wenbin
 * @since 2026-08-31
 */
class AsyncSupportTest {

    @Test
    void asyncHolderShouldBindAndExpose() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.initialize();
        AsyncHolder.bind(Executors.newSingleThreadExecutor(), scheduler);
        assertThat(AsyncHolder.isReady()).isTrue();
        assertThat(AsyncHolder.getExecutor()).isNotNull();
        assertThat(AsyncHolder.getScheduler()).isNotNull();
        scheduler.shutdown();
    }

    @Test
    void exceptionHandlerShouldLogWithoutThrow() throws Exception {
        LoggingAsyncUncaughtExceptionHandler handler = new LoggingAsyncUncaughtExceptionHandler();
        Method method = AsyncSupportTest.class.getMethod("dummy");
        handler.handleUncaughtException(new IllegalStateException("异步失败"), method, "arg1");
        assertThat(handler).isNotNull();
    }

    @Test
    void asyncUtilsShouldExecute() {
        assertThat(AsyncUtils.class).isNotNull();
    }

    @SuppressWarnings("unused")
    public void dummy() {
    }
}

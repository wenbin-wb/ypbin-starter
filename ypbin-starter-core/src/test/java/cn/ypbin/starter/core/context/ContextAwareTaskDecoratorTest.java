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
package cn.ypbin.starter.core.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 上下文透传装饰器测试：主线程 MDC 与传播器快照应随异步任务透传到子线程，
 * 执行后子线程上下文恢复原状（不污染复用线程）。
 *
 * @author wenbin
 * @since 2026-08-31
 */
class ContextAwareTaskDecoratorTest {

    private final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

    {
        executor.setThreadNamePrefix("ctx-test-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.initialize();
    }

    @AfterEach
    void tearDown() {
        executor.shutdown();
    }

    @Test
    void shouldPropagateCustomSnapshotAndRestoreBackup() throws Exception {
        // 自定义传播器：快照 = 线程本地值，验证 decorator 对 propagators 的调用链
        ThreadLocal<String> holder = new ThreadLocal<>();
        ContextPropagator<String> propagator = new ContextPropagator<>() {
            @Override
            public String capture() {
                return holder.get();
            }

            @Override
            public void restore(String snapshot) {
                holder.set(snapshot);
            }
        };
        ContextAwareTaskDecorator decorator = new ContextAwareTaskDecorator(List.of(propagator));

        holder.set("tenant-1");
        Executor decoratedExecutor = r -> executor.execute(decorator.decorate(r));
        CompletableFuture<String> future = CompletableFuture.supplyAsync(
            () -> holder.get(),
            decoratedExecutor
        );

        assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo("tenant-1");
    }

    @Test
    void shouldExecuteTaskWithEmptySnapshotWithoutError() throws Exception {
        ContextAwareTaskDecorator decorator = new ContextAwareTaskDecorator(List.of());

        Executor decoratedExecutor = r -> executor.execute(decorator.decorate(r));
        CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(
            () -> Boolean.TRUE,
            decoratedExecutor
        );

        assertThat(future.get(5, TimeUnit.SECONDS)).isTrue();
    }
}

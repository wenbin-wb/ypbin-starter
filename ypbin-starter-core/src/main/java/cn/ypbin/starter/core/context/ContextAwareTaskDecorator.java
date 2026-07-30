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

import java.util.List;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.lang.NonNull;

/**
 * 上下文感知任务装饰器。
 *
 * <p>解决异步任务（{@code @Async}、线程池）子线程"失忆"问题：主线程提交任务时抓取所有
 * {@link ContextPropagator} 的上下文快照与 SLF4J MDC，子线程执行前还原、执行后清理。
 * 使多租户、当前用户、数据权限状态、日志 traceId 等能正确透传到异步线程。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class ContextAwareTaskDecorator implements TaskDecorator {

    private final List<ContextPropagator<?>> propagators;

    public ContextAwareTaskDecorator(List<ContextPropagator<?>> propagators) {
        this.propagators = propagators;
    }

    @Override
    @NonNull
    public Runnable decorate(@NonNull Runnable runnable) {
        // 主线程：抓取快照
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        Object[] snapshots = new Object[propagators.size()];
        for (int i = 0; i < propagators.size(); i++) {
            snapshots[i] = propagators.get(i).capture();
        }

        return () -> {
            // 子线程执行前：还原
            if (mdc != null) {
                MDC.setContextMap(mdc);
            }
            restoreAll(snapshots);
            try {
                runnable.run();
            } finally {
                // 子线程执行后：清理，防止线程池复用串上下文
                clearAll();
                MDC.clear();
            }
        };
    }

    @SuppressWarnings("unchecked")
    private void restoreAll(Object[] snapshots) {
        for (int i = 0; i < propagators.size(); i++) {
            ((ContextPropagator<Object>) propagators.get(i)).restore(snapshots[i]);
        }
    }

    private void clearAll() {
        for (ContextPropagator<?> propagator : propagators) {
            propagator.clear();
        }
    }
}

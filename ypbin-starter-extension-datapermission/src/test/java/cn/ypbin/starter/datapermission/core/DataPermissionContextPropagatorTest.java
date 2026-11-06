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
package cn.ypbin.starter.datapermission.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link DataPermissionContextPropagator} 跨线程传播用例。
 *
 * @author wenbin
 * @since 2026-11-06
 */
class DataPermissionContextPropagatorTest {

    @Test
    @DisplayName("主线程的作用域计数可还原到子线程，异步查询不会静默失去数据范围")
    void propagatesScopeToWorkerThread() throws Exception {
        DataPermissionContextPropagator propagator = new DataPermissionContextPropagator();
        assertThat(propagator.capture()).isZero();

        DataPermissionContext.enter();
        Integer snapshot;
        try {
            snapshot = propagator.capture();
            assertThat(snapshot).isEqualTo(1);
        } finally {
            DataPermissionContext.exit();
        }

        AtomicReference<Boolean> activeInWorker = new AtomicReference<>(false);
        Thread worker = new Thread(() -> {
            assertThat(DataPermissionContext.isActive()).as("新线程默认无作用域").isFalse();
            propagator.restore(snapshot);
            activeInWorker.set(DataPermissionContext.isActive());
            DataPermissionContext.exit();
        });
        worker.start();
        worker.join();

        assertThat(activeInWorker.get()).isTrue();
        assertThat(DataPermissionContext.isActive()).as("主线程不受子线程影响").isFalse();
    }

    @Test
    @DisplayName("空快照还原即清空作用域")
    void restoreNullClearsScope() {
        DataPermissionContext.enter();

        new DataPermissionContextPropagator().restore(0);

        assertThat(DataPermissionContext.isActive()).isFalse();
    }

    @Test
    @DisplayName("嵌套作用域按计数还原，内层退出不关闭外层")
    void restoresNestedDepth() {
        DataPermissionContextPropagator propagator = new DataPermissionContextPropagator();
        DataPermissionContext.enter();
        DataPermissionContext.enter();
        try {
            assertThat(propagator.capture()).isEqualTo(2);
            DataPermissionContext.exit();

            new DataPermissionContextPropagator().restore(2);

            DataPermissionContext.exit();
            assertThat(DataPermissionContext.isActive()).isTrue();
        } finally {
            DataPermissionContext.restore(null);
        }
    }
}

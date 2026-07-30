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
package cn.ypbin.starter.tenant.core;

import cn.ypbin.starter.core.context.ContextPropagator;

/**
 * 租户忽略标记的跨线程传播器。
 *
 * <p>注册到 core 的上下文透传体系，使 {@code @Async} 等异步任务的子线程能继承主线程的
 * 忽略租户状态，避免异步任务因子线程丢失标记而错误地重新启用隔离。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class TenantContextPropagator implements ContextPropagator<Integer> {

    @Override
    public Integer capture() {
        return TenantContext.snapshot();
    }

    @Override
    public void restore(Integer snapshot) {
        TenantContext.restore(snapshot);
    }

    @Override
    public void clear() {
        TenantContext.clear();
    }
}

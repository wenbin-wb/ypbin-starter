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

import io.micrometer.context.ThreadLocalAccessor;

/**
 * 租户上下文的 Reactor 跨线程传播器。
 *
 * <p>实现 {@link ThreadLocalAccessor}，使 Reactor 在每次线程切换（Scheduler 调度、
 * flatMap、subscribeOn 等）前后自动快照并还原 {@link TenantContext}，从而让
 * {@code @Tool} 工具方法、Flux 操作链等异步执行上下文中都能正确读取到发起请求的租户 ID，
 * 不需要业务代码做任何改动，且不破坏租户隔离。
 *
 * <p>注册方式：{@link TenantAutoConfiguration} 将本类声明为 Bean，Spring Boot 4 的
 * {@code ObservationAutoConfiguration} 会自动将其注册到全局 {@code ContextRegistry}，
 * Reactor 3.5+ 只要引入 {@code io.micrometer:context-propagation} 即可自动发现。
 *
 * @author wenbin
 * @since 2026-08-17
 */
public class TenantThreadLocalAccessor implements ThreadLocalAccessor<TenantContext.ContextSnapshot> {

    /** key 需全局唯一，采用全限定类名作为命名空间 */
    public static final String KEY = "cn.ypbin.tenant.context";

    @Override
    public Object key() {
        return KEY;
    }

    /**
     * 抓取当前线程的租户快照（调度前由 Reactor 在发起线程上调用）。
     */
    @Override
    public TenantContext.ContextSnapshot getValue() {
        return TenantContext.snapshot();
    }

    /**
     * 将快照还原到目标线程（调度后由 Reactor 在执行线程上调用）。
     */
    @Override
    public void setValue(TenantContext.ContextSnapshot value) {
        TenantContext.restore(value);
    }

    /**
     * 调度结束后由 Reactor 清理目标线程上下文，防止 ThreadLocal 泄漏。
     */
    @Override
    public void reset() {
        TenantContext.clear();
    }
}

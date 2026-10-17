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
package cn.ypbin.starter.messaging.sse;

import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshotFactory;
import reactor.util.context.Context;

/**
 * 基于 micrometer {@code context-propagation} 的 {@link SseContextPropagator} 实现。
 *
 * <p><strong>本类只在 {@code io.micrometer.context.ContextRegistry} 存在时才会被加载</strong>
 * （由 {@link SseContextPropagator#micrometerOrNoop()} 用 {@code ClassUtils.isPresent} 守卫），
 * 因此这里可以放心引用 micrometer 与 Reactor 类型。</p>
 *
 * <p><strong>两个经验证的 Reactor 语义</strong>（本实现的正确性依赖它们，均已用 3.8.7 实测）：</p>
 * <ul>
 *   <li><strong>{@code contextWrite} 会抑制订阅期的自动 ThreadLocal 捕获</strong>：链上只要出现
 *       {@code contextWrite}（即使 lambda 是恒等函数），Reactor 就不再在 {@code subscribe()} 时自动抓取
 *       调用线程的 ThreadLocal。故「快照为空」时<strong>绝不能</strong>写 Context——那会把宿主本来生效的
 *       自动传播掐断。{@link #carriesContext()} 就是为此存在。</li>
 *   <li><strong>{@code ContextSnapshot.updateContext(Context)} 仅在快照非空时才改写 Context</strong>：
 *       空快照会原样返回入参（同一实例）。据此可判断「快照是否携带内容」而无需反射框架内部实现。</li>
 * </ul>
 *
 * @author wenbin
 * @since 2026-10-16
 */
final class MicrometerSseContextPropagator implements SseContextPropagator {

    /** 快照工厂：按全局 {@code ContextRegistry} 抓取全部已注册的 ThreadLocalAccessor。 */
    private final ContextSnapshotFactory factory = ContextSnapshotFactory.builder().build();

    @Override
    public Snapshot capture() {
        return new MicrometerSnapshot(factory.captureAll());
    }

    /**
     * micrometer {@link ContextSnapshot} 的适配实现。
     */
    private static final class MicrometerSnapshot implements Snapshot {

        private final ContextSnapshot delegate;

        MicrometerSnapshot(ContextSnapshot delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean carriesContext() {
            // 空快照时 updateContext 原样返回入参 ⇒ 得到的 Context 仍为空
            return !delegate.updateContext(Context.empty()).isEmpty();
        }

        @Override
        public <C> C enrich(C context) {
            return delegate.updateContext(context);
        }

        @Override
        public Scope restore() {
            ContextSnapshot.Scope scope = delegate.setThreadLocals();
            return scope::close;
        }
    }
}

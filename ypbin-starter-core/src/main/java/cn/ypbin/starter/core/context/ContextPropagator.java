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

/**
 * 线程上下文传播扩展点。
 *
 * <p>各模块（多租户、用户、数据权限、MDC 等）通过实现本接口，把自己基于 {@code ThreadLocal}
 * 的上下文纳入跨线程传播。异步任务提交前，{@code ContextAwareTaskDecorator} 在主线程调用
 * {@link #capture()} 抓取快照；子线程执行前 {@link #restore} 还原，执行后由装饰器备份-恢复执行线程
 * 原有上下文，无需实现方主动清理。</p>
 *
 * <p>这样 core 无需反向依赖具体业务模块，各模块自行注册传播器，实现解耦的上下文透传。</p>
 *
 * @param <T> 上下文快照类型
 * @author wenbin
 * @since 2026-07-30
 */
public interface ContextPropagator<T> {

    /**
     * 在主线程抓取当前上下文快照。
     *
     * @return 上下文快照，无上下文时返回 {@code null}
     */
    T capture();

    /**
     * 在子线程执行前还原上下文。
     *
     * @param snapshot 主线程抓取的快照（可能为 {@code null}）
     */
    void restore(T snapshot);
}

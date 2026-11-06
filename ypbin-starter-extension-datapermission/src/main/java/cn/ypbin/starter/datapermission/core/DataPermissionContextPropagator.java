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

import cn.ypbin.starter.core.context.ContextPropagator;

/**
 * 数据权限作用域的跨线程传播器。
 *
 * <p>注册到 core 的上下文透传体系后，{@code @Async}、线程池等异步任务会继承调用方的数据权限作用域。
 * 缺了它，异步线程里 {@link DataPermissionContext#isActive()} 恒为 {@code false} ⇒ 拦截器不拼接
 * 数据范围 ⇒ <b>异步查询静默变成全量查询</b>（fail-open，且没有任何日志）。</p>
 *
 * @author wenbin
 * @since 2026-11-06
 */
public class DataPermissionContextPropagator implements ContextPropagator<Integer> {

    /**
     * 抓取作用域计数；未激活时以 {@code 0} 表示（SPI 约定快照非空）。
     *
     * @return 作用域计数，0 表示无上下文
     */
    @Override
    public Integer capture() {
        Integer depth = DataPermissionContext.snapshot();
        return depth == null ? 0 : depth;
    }

    /**
     * 还原作用域计数；{@code 0} 表示清空。
     *
     * @param snapshot 作用域计数
     */
    @Override
    public void restore(Integer snapshot) {
        DataPermissionContext.restore(snapshot == 0 ? null : snapshot);
    }
}

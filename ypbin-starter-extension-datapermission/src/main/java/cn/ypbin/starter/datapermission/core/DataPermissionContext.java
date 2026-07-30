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

/**
 * 数据权限激活上下文。
 *
 * <p>用线程内计数标记当前是否处于 {@code @DataPermission} 方法内，仅在激活时拦截器才拼接
 * 权限 SQL。用计数（而非布尔）支持标注方法的嵌套调用：进入 +1、退出 -1，归零才真正清理，
 * 避免内层方法退出时误关闭外层的数据权限。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public final class DataPermissionContext {

    private static final ThreadLocal<Integer> DEPTH = new ThreadLocal<>();

    private DataPermissionContext() {
    }

    /**
     * 进入数据权限作用域。
     */
    public static void enter() {
        Integer depth = DEPTH.get();
        DEPTH.set(depth == null ? 1 : depth + 1);
    }

    /**
     * 退出数据权限作用域，归零时清理 ThreadLocal 防止内存泄漏。
     */
    public static void exit() {
        Integer depth = DEPTH.get();
        if (depth == null || depth <= 1) {
            DEPTH.remove();
        } else {
            DEPTH.set(depth - 1);
        }
    }

    /**
     * 当前线程是否已激活数据权限。
     *
     * @return 是否激活
     */
    public static boolean isActive() {
        return DEPTH.get() != null;
    }
}

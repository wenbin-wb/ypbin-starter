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

import java.util.function.Supplier;

/**
 * 租户上下文。
 *
 * <p>用线程内计数标记"当前是否忽略租户隔离"，供超管全局查询、后台定时任务全表扫描等
 * 跨租户场景临时逃逸。计数式设计支持嵌套：进入 +1、退出 -1，归零才真正恢复隔离，
 * 避免内层逃逸块退出时误开启外层的隔离。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public final class TenantContext {

    private static final ThreadLocal<Integer> IGNORE_DEPTH = new ThreadLocal<>();

    private TenantContext() {
    }

    /**
     * 进入忽略租户作用域。
     */
    public static void enterIgnore() {
        Integer depth = IGNORE_DEPTH.get();
        IGNORE_DEPTH.set(depth == null ? 1 : depth + 1);
    }

    /**
     * 退出忽略租户作用域，归零时清理 ThreadLocal 防止内存泄漏。
     */
    public static void exitIgnore() {
        Integer depth = IGNORE_DEPTH.get();
        if (depth == null || depth <= 1) {
            IGNORE_DEPTH.remove();
        } else {
            IGNORE_DEPTH.set(depth - 1);
        }
    }

    /**
     * 当前线程是否忽略租户隔离。
     *
     * @return 是否忽略
     */
    public static boolean isIgnored() {
        return IGNORE_DEPTH.get() != null;
    }

    /**
     * 在忽略租户的作用域内执行有返回值的逻辑。
     *
     * @param supplier 业务逻辑
     * @param <T>      返回类型
     * @return 执行结果
     */
    public static <T> T executeIgnore(Supplier<T> supplier) {
        enterIgnore();
        try {
            return supplier.get();
        } finally {
            exitIgnore();
        }
    }

    /**
     * 在忽略租户的作用域内执行无返回值的逻辑。
     *
     * @param runnable 业务逻辑
     */
    public static void runIgnore(Runnable runnable) {
        enterIgnore();
        try {
            runnable.run();
        } finally {
            exitIgnore();
        }
    }

    /**
     * 获取当前忽略计数快照（供上下文透传使用）。
     *
     * @return 当前计数，未激活为 {@code null}
     */
    public static Integer snapshot() {
        return IGNORE_DEPTH.get();
    }

    /**
     * 还原忽略计数快照（供上下文透传使用）。
     *
     * @param depth 快照值
     */
    public static void restore(Integer depth) {
        if (depth == null) {
            IGNORE_DEPTH.remove();
        } else {
            IGNORE_DEPTH.set(depth);
        }
    }

    /**
     * 清理当前线程上下文。
     */
    public static void clear() {
        IGNORE_DEPTH.remove();
    }
}

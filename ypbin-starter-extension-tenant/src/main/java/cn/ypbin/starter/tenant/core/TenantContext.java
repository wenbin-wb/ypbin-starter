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

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 租户上下文。
 *
 * <p>维护当前线程显式绑定的租户及忽略租户隔离的嵌套深度。显式租户供后台任务在无请求上下文时
 * 绑定单个租户；忽略计数供明确的跨租户场景临时放行，并在嵌套作用域退出时准确恢复。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public final class TenantContext {

    private static final ThreadLocal<Integer> IGNORE_DEPTH = new ThreadLocal<>();
    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();

    /**
     * 租户上下文快照。
     *
     * @param ignoreDepth 忽略租户隔离的嵌套深度
     * @param tenantId    显式租户 ID
     */
    public record ContextSnapshot(Integer ignoreDepth, Long tenantId) {
    }

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
     * 获取当前线程显式绑定的租户 ID。
     *
     * @return 租户 ID
     */
    public static Optional<Long> getTenantId() {
        return Optional.ofNullable(TENANT_ID.get());
    }

    /**
     * 在指定租户作用域内执行有返回值的逻辑。
     *
     * @param tenantId 租户 ID
     * @param supplier 业务逻辑
     * @param <T>      返回类型
     * @return 执行结果
     */
    public static <T> T executeWithTenant(Long tenantId, Supplier<T> supplier) {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        Objects.requireNonNull(supplier, "业务逻辑不能为空");
        Long previous = TENANT_ID.get();
        TENANT_ID.set(tenantId);
        try {
            return supplier.get();
        } finally {
            restoreTenantId(previous);
        }
    }

    /**
     * 在指定租户作用域内执行无返回值的逻辑。
     *
     * @param tenantId 租户 ID
     * @param runnable 业务逻辑
     */
    public static void runWithTenant(Long tenantId, Runnable runnable) {
        Objects.requireNonNull(runnable, "业务逻辑不能为空");
        executeWithTenant(tenantId, () -> {
            runnable.run();
            return null;
        });
    }

    /**
     * 获取当前完整上下文快照。
     *
     * @return 上下文快照
     */
    public static ContextSnapshot snapshot() {
        return new ContextSnapshot(IGNORE_DEPTH.get(), TENANT_ID.get());
    }

    /**
     * 还原完整上下文快照。
     *
     * @param snapshot 上下文快照
     */
    public static void restore(ContextSnapshot snapshot) {
        if (snapshot == null) {
            clear();
            return;
        }
        restoreIgnoreDepth(snapshot.ignoreDepth());
        restoreTenantId(snapshot.tenantId());
    }

    /**
     * 清理当前线程上下文。
     */
    public static void clear() {
        IGNORE_DEPTH.remove();
        TENANT_ID.remove();
    }

    private static void restoreIgnoreDepth(Integer depth) {
        if (depth == null) {
            IGNORE_DEPTH.remove();
        } else {
            IGNORE_DEPTH.set(depth);
        }
    }

    private static void restoreTenantId(Long tenantId) {
        if (tenantId == null) {
            TENANT_ID.remove();
        } else {
            TENANT_ID.set(tenantId);
        }
    }
}

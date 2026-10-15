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
package cn.ypbin.starter.tools.lock;

import org.jspecify.annotations.Nullable;

/**
 * 一次「带锁执行」的结果。
 *
 * <p>{@code result} 显式可空：跳过（未取得锁）与 fail-open 降级路径下任务可能根本没执行，
 * 没有可返回的值。调用方必须先看 {@code executed} 再取 {@code result}——这是把
 * 「锁没抢到」与「任务返回了 null」区分开的唯一办法。</p>
 *
 * @param executed 是否执行了任务（{@code false} = 锁被其它实例持有，本轮跳过）
 * @param degraded 是否在锁存储不可用时降级执行（fail-open；{@code true} 表示多副本可能重复执行）
 * @param result   任务返回值（未执行时为 {@code null}）
 * @param <T>      任务返回值类型
 * @author wenbin
 * @since 2026-10-15
 */
public record LeaseLockOutcome<T>(boolean executed, boolean degraded, @Nullable T result) {

    /**
     * 跳过的结果（未取得锁）。
     *
     * @param <T> 任务返回值类型
     * @return 跳过结果
     */
    public static <T> LeaseLockOutcome<T> skipped() {
        return new LeaseLockOutcome<>(false, false, null);
    }
}

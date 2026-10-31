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
package cn.ypbin.starter.tools.idempotent;

import java.time.Duration;

/**
 * 幂等键存储扩展点。
 *
 * <p>核心语义是"占位成功即首次调用"：原子地尝试写入键，写入成功返回 {@code true}
 * （首次，放行），键已存在返回 {@code false}（重复，拒绝）。默认内存实现，
 * 分布式场景由 Redis 实现覆盖。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public interface IdempotentStore {

    /**
     * 尝试占位。
     *
     * @param key    幂等键
     * @param expire 占位有效期
     * @return {@code true} 表示占位成功（首次调用）；{@code false} 表示键已存在（重复调用）
     */
    boolean tryAcquire(String key, Duration expire);

    /**
     * 带属主的尝试占位。
     *
     * <p>属主用于「失败释放」时确认占位仍属于本次调用：业务执行时间超过 {@code expire} 后，
     * 占位会自然过期并被另一个请求重新占用，此时若按 key 无条件删除，会误删他人的占位、
     * 让幂等形同虚设。</p>
     *
     * <p>默认实现忽略属主、退化为 {@link #tryAcquire(String, Duration)}，保持对历史宿主存储的
     * 二进制与行为兼容（这类存储的释放仍是无条件删除）。</p>
     *
     * @param key    幂等键
     * @param expire 占位有效期
     * @param owner  本次调用的唯一属主标识
     * @return {@code true} 表示占位成功（首次调用）
     */
    default boolean tryAcquire(String key, Duration expire, String owner) {
        return tryAcquire(key, expire);
    }

    /**
     * 释放占位键。
     *
     * <p>业务方法执行异常后由幂等切面调用，删除占位使客户端可立即重试，而不必等到
     * {@link #tryAcquire} 的有效期自然过期。默认空实现保持向后兼容：未覆盖本方法的
     * 宿主存储不会释放占位（失败后仍需等待窗口过期才能重试）。</p>
     *
     * @param key 幂等键
     */
    default void release(String key) {
        // 默认不释放，兼容仅实现 tryAcquire 的历史宿主存储
    }

    /**
     * 仅当占位仍属于 {@code owner} 时释放（compare-and-delete）。
     *
     * <p>默认实现退化为 {@link #release(String)}（无条件删除），同样用于兼容未覆盖本方法的
     * 历史宿主存储；内置的内存/Redis 实现均已改为按属主校验。</p>
     *
     * @param key   幂等键
     * @param owner 占位时登记的属主标识
     */
    default void release(String key, String owner) {
        release(key);
    }
}

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
}

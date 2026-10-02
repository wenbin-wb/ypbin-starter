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
package cn.ypbin.starter.gateway.ratelimit;

/**
 * 固定窗口限流判定（纯函数，可单测）。
 *
 * <p>语义：计数 ≤ 配额放行；配额 ≤ 0 表示不限（`0 = 不限` 与各业务既有口径一致）。
 *
 * @param allowed 是否放行
 * @param count 本窗口实际计数
 * @param limit 本窗口配额
 *
 * @author wenbin
 * @since 2026-10-02
 */
public record FixedWindowRateLimit(boolean allowed, long count, long limit) {

    /**
     * 按计数判定。
     *
     * @param count 实际计数（Redis 异常时调用方传负数 ⇒ 按不限处理，fail-open 由调用方决定）
     * @param limit 配额（≤ 0 = 不限）
     * @return 判定结果（携带计数与配额，供日志/审计）
     */
    public static FixedWindowRateLimit forCount(long count, long limit) {
        if (limit <= 0) {
            return new FixedWindowRateLimit(true, count, 0L);
        }
        return new FixedWindowRateLimit(count <= limit, count, limit);
    }
}

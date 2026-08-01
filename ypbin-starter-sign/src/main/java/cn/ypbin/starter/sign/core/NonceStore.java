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
package cn.ypbin.starter.sign.core;

import java.time.Duration;

/**
 * nonce 防重放存储扩展点。
 *
 * <p>记录已使用的 nonce，有效期内重复出现即视为重放攻击。默认内存实现，分布式场景由 Redis
 * 实现覆盖。核心语义与幂等一致：原子地"首次记录成功"返回 true，已存在返回 false。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public interface NonceStore {

    /**
     * 尝试记录 nonce。
     *
     * @param key    nonce 键（通常为 accessKey + nonce）
     * @param expire 有效期（应不小于签名超时）
     * @return {@code true} 首次使用（放行）；{@code false} 已使用过（重放，拒绝）
     */
    boolean tryUse(String key, Duration expire);
}

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
package cn.ypbin.starter.tools.limiter;

import java.time.Duration;

/**
 * 限流计数存储扩展点。
 *
 * <p>抽象限流的计数与窗口存储，默认本地内存实现（单机），业务方可实现基于 Redis 的
 * 分布式版本并通过 {@code @ConditionalOnMissingBean} 覆盖。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public interface RateLimiterStore {

    /**
     * 在时间窗口内对指定 key 计数并返回当前计数值。
     *
     * <p>首次访问时应以 {@code window} 为过期时间创建计数，窗口过期后自动重置。</p>
     *
     * @param key    限流键
     * @param window 时间窗口
     * @return 当前窗口内的累计次数（含本次）
     */
    long incrementAndGet(String key, Duration window);
}

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
package cn.ypbin.starter.cache.multilevel;

/**
 * 本地缓存失效广播发布器。
 *
 * <p>某实例写/删 key 后，通过本发布器把失效消息广播给集群其它实例，令各实例摘除各自的 L1 本地缓存，
 * 实现多实例最终一致。默认基于 Redis Pub/Sub 实现。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
public interface CacheInvalidationPublisher {

    /**
     * 广播某个 key 已失效。
     *
     * @param key 缓存键
     */
    void publish(String key);
}

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

import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 基于 Redis Pub/Sub 的失效广播发布器。
 *
 * <p>把失效的 key 发布到约定频道，集群内所有实例的订阅者收到后摘除各自 L1。为避免自广播回环，
 * 消息体带上实例标识，订阅方忽略自己发出的消息。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
public class RedisCacheInvalidationPublisher implements CacheInvalidationPublisher {

    private final StringRedisTemplate redisTemplate;
    private final String channel;
    private final String instanceId;

    public RedisCacheInvalidationPublisher(StringRedisTemplate redisTemplate, String channel, String instanceId) {
        this.redisTemplate = redisTemplate;
        this.channel = channel;
        this.instanceId = instanceId;
    }

    @Override
    public void publish(String key) {
        // 消息格式：实例标识|key，订阅方据此忽略自身消息
        redisTemplate.convertAndSend(channel, instanceId + '|' + key);
    }

    public String getChannel() {
        return channel;
    }

    public String getInstanceId() {
        return instanceId;
    }
}

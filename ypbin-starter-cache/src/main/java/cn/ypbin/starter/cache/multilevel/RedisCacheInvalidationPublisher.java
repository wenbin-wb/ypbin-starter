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
        // 消息格式：<instanceId 长度>:<instanceId><key>。用长度前缀为 instanceId 定界，key 取定界后的剩余
        // 全部内容，业务 key 可含任意字符（含 ':'/多个 '|'），不会像旧版 'instanceId|key' 拼接那样产生歧义。
        // 长度前缀解析失败的消息按非法消息忽略，滚动升级窗口内的旧格式消息会短暂丢弃属预期。
        redisTemplate.convertAndSend(channel, instanceId.length() + ":" + instanceId + key);
    }

    public String getChannel() {
        return channel;
    }

    public String getInstanceId() {
        return instanceId;
    }
}

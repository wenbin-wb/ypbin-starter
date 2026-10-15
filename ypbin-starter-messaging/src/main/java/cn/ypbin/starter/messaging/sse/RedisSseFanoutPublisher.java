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
package cn.ypbin.starter.messaging.sse;

import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 基于 Redis Pub/Sub 的 SSE 跨实例扇出发布器。
 *
 * <p><strong>多实例扇出怎么做</strong>：连接注册表在 JVM 内存里，单实例内直接投递即可；跨实例时，
 * 一次 {@link #publish(SseFanoutMessage)} 把帧发到约定频道，集群内所有实例的订阅容器
 * （{@link SseFanoutSubscriber}，由自动配置装配为 {@code RedisMessageListenerContainer} Bean）收到后
 * 投给<strong>本实例</strong>的连接。于是「连在实例 A 的客户端」也能收到「在实例 B 上产生的推送」。</p>
 *
 * <p><strong>去自广播回环</strong>：帧里带发布实例标识（长度前缀定界，照抄同仓
 * {@code RedisCacheInvalidationPublisher} 的做法），订阅方丢弃「自己发的帧」——本地连接已由
 * {@link SseEmitterManager} 就地投递过，不丢就会收到两份。发布器与订阅方共用同一个 {@code instanceId}
 * （由自动配置里唯一的 {@code sseFanoutInstanceId} Bean 提供），这是回环判定成立的前提。</p>
 *
 * <p>频道名可配（{@code ypbin.sse.fanout-channel}）。<strong>多应用共用同一 Redis 时必须改成本应用
 * 专属频道名</strong>，否则会互相投递本不属于自己的帧（帧里没有应用标识，无法二次过滤）。</p>
 *
 * @author wenbin
 * @since 2026-10-15
 */
public class RedisSseFanoutPublisher implements SseFanoutBridge {

    private final StringRedisTemplate redisTemplate;

    private final String channel;

    private final String instanceId;

    public RedisSseFanoutPublisher(StringRedisTemplate redisTemplate, String channel, String instanceId) {
        this.redisTemplate = redisTemplate;
        this.channel = channel;
        this.instanceId = instanceId;
    }

    @Override
    public void publish(SseFanoutMessage message) {
        redisTemplate.convertAndSend(channel, SseFanoutProtocol.encode(message, instanceId));
    }

    @Override
    public String instanceId() {
        return instanceId;
    }

    /**
     * 发布频道（诊断/测试用）。
     *
     * @return 频道名
     */
    public String getChannel() {
        return channel;
    }
}

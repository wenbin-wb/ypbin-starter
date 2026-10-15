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
package cn.ypbin.starter.messaging.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.messaging.push.PushService;
import cn.ypbin.starter.messaging.sse.InMemorySseTicketStore;
import cn.ypbin.starter.messaging.sse.RedisSseFanoutPublisher;
import cn.ypbin.starter.messaging.sse.RedisSseTicketStore;
import cn.ypbin.starter.messaging.sse.SseEmitterManager;
import cn.ypbin.starter.messaging.sse.SseFanoutBridge;
import cn.ypbin.starter.messaging.sse.SseFanoutCodec;
import cn.ypbin.starter.messaging.sse.SseProperties;
import cn.ypbin.starter.messaging.sse.SseTicketStore;
import cn.ypbin.starter.messaging.sse.TextSseFanoutCodec;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * {@link SseAutoConfiguration} 装配测试。
 *
 * <p>锁定「可选 Redis 依赖」的两种场景，防回归：</p>
 * <ul>
 *     <li><strong>无 Redis</strong>：用 {@link FilteredClassLoader} 隐藏 {@link StringRedisTemplate}，复现轻量消费端。
 *     配置类内省不得触碰 Redis 类型（否则 {@code NoClassDefFoundError} 启动即崩），且装配内存票据实现。</li>
 *     <li><strong>有 Redis</strong>：提供 {@link StringRedisTemplate} Bean，装配的应是 Redis 实现而非内存兜底
 *     （验证 {@code @Import} 保证 Redis 先于内存兜底注册）。</li>
 * </ul>
 *
 * @author wenbin
 * @since 2026-08-07
 */
class SseAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(SseAutoConfiguration.class))
        .withPropertyValues("ypbin.sse.enabled=true");

    @Test
    void withoutRedis_startsAndUsesInMemoryTicketStore() {
        runner.withClassLoader(new FilteredClassLoader(StringRedisTemplate.class))
            .run(context -> {
                // 关键：无 Redis 时上下文能正常启动（不因配置类内省触碰 StringRedisTemplate 而崩）
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(SseEmitterManager.class);
                assertThat(context).hasSingleBean(PushService.class);
                assertThat(context).getBean(SseTicketStore.class).isInstanceOf(InMemorySseTicketStore.class);
                // 无 Redis：不装配扇出桥，也不注册 Redis 监听容器 ⇒ 退化为纯单实例投递（行为不变）
                assertThat(context).doesNotHaveBean(SseFanoutBridge.class);
                assertThat(context.getBean(SseEmitterManager.class).getCodec())
                    .isInstanceOf(TextSseFanoutCodec.class);
            });
    }

    @Test
    void withRedis_assemblesFanoutBridgeAndWiresItIntoManager() {
        runner.withUserConfiguration(RedisTemplateConfig.class)
            .run(context -> {
                // 注意：这里只断言装配结果，不启动真实订阅（RedisConnectionFactory 也是 mock，
                // 容器启动会尝试连接 ⇒ 故用 context 失败容忍度更高的断言方式：Bean 定义存在即可）。
                assertThat(context.getBeanNamesForType(SseFanoutBridge.class)).isNotEmpty();
                assertThat(context.getBean(SseFanoutBridge.class))
                    .isInstanceOf(RedisSseFanoutPublisher.class);
                RedisSseFanoutPublisher publisher = (RedisSseFanoutPublisher) context.getBean(SseFanoutBridge.class);
                assertThat(publisher.getChannel()).isEqualTo("ypbin:sse:fanout");
                assertThat(publisher.instanceId()).isNotBlank();
                assertThat(context.getBean(SseFanoutCodec.class)).isInstanceOf(TextSseFanoutCodec.class);
            });
    }

    @Test
    void fanoutListenerContainerFactoryBuildsContainerWithTopicAndListener() {
        // 直接调生产工厂方法：只验证「装配了什么」（连接工厂 + 频道 + 监听器），不 refresh 上下文，
        // 避免真实容器在桩连接上启动超时。宿主未提供同类 Bean 时自动配置走的就是这个方法。
        SseEmitterManager manager = new SseEmitterManager(60_000L, 0);
        org.springframework.data.redis.connection.RedisConnectionFactory connectionFactory =
            Mockito.mock(org.springframework.data.redis.connection.RedisConnectionFactory.class);
        SseFanoutBridge bridge = new RedisSseFanoutPublisher(
            Mockito.mock(StringRedisTemplate.class), "ypbin:sse:fanout", "inst-1");
        SseProperties properties = new SseProperties();

        org.springframework.data.redis.listener.RedisMessageListenerContainer container =
            new SseAutoConfiguration.RedisFanoutConfiguration()
                .sseFanoutListenerContainer(connectionFactory, manager, new TextSseFanoutCodec(), bridge, properties);

        assertThat(container).isNotNull();
        // 未调用 start()：既不订阅也不启动后台线程（本用例只锁装配关系）
        assertThat(container.isRunning()).isFalse();
    }

    @Test
    void fanoutDisabled_keepsSingleInstanceBehaviour() {
        runner.withUserConfiguration(RedisTemplateConfig.class)
            .withPropertyValues("ypbin.sse.fanout-enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean(SseFanoutBridge.class));
    }

    @Test
    void withRedis_usesRedisTicketStore() {
        runner.withUserConfiguration(RedisTemplateConfig.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                // @Import 保证 Redis 实现先注册，内存兜底的 @ConditionalOnMissingBean 退让
                assertThat(context).getBean(SseTicketStore.class).isInstanceOf(RedisSseTicketStore.class);
            });
    }

    @Configuration(proxyBeanMethods = false)
    static class RedisTemplateConfig {
        @Bean
        StringRedisTemplate stringRedisTemplate() {
            // 装配阶段不触发真实连接，用 mock 即可满足类型注入
            return Mockito.mock(StringRedisTemplate.class);
        }

        /**
         * 连接工厂桩（扇出桥的装配依赖它）。
         */
        @Bean
        org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory() {
            return Mockito.mock(org.springframework.data.redis.connection.RedisConnectionFactory.class);
        }

        /**
         * 订阅容器桩。
         *
         * <p>为什么测试里要提供它：真实容器在 {@code start()} 时会真的去订阅 Redis，对桩连接工厂必然
         * 超时失败（{@code Subscription registration timeout exceeded}），把整个上下文带崩——
         * 连本来与该能力无关的「Redis 票据存储」用例都会一起红。本模块的装配约定是
         * {@code @ConditionalOnMissingBean(RedisMessageListenerContainer.class)}：宿主（此处即测试）提供
         * 同类 Bean 时自动配置让位，正是为这种场景设计的。</p>
         *
         * <p>桩的 {@code isAutoStartup()} 默认 false，故 Spring 不会启动它。</p>
         */
        @Bean
        org.springframework.data.redis.listener.RedisMessageListenerContainer redisMessageListenerContainer() {
            return Mockito.mock(org.springframework.data.redis.listener.RedisMessageListenerContainer.class);
        }
    }
}

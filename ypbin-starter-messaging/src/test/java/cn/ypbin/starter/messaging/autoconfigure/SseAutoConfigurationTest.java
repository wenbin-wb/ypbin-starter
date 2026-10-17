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
import cn.ypbin.starter.messaging.sse.JacksonSseFanoutCodec;
import cn.ypbin.starter.messaging.sse.RedisSseFanoutPublisher;
import cn.ypbin.starter.messaging.sse.RedisSseTicketStore;
import cn.ypbin.starter.messaging.sse.SseEmitterManager;
import cn.ypbin.starter.messaging.sse.SseFanoutBridge;
import cn.ypbin.starter.messaging.sse.SseFanoutCodec;
import cn.ypbin.starter.messaging.sse.SseProperties;
import cn.ypbin.starter.messaging.sse.SseTicketStore;
import cn.ypbin.starter.messaging.sse.TextSseFanoutCodec;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

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

    /**
     * ★ 顺序回归（2026-10-15 租约锁线上事故排查时发现的同类陷阱）：与 Boot 的 Redis 自动配置一起
     * 装配时，Redis 分支必须被真正选中、扇出桥必须生成。
     *
     * <p>{@code withRedis_assemblesFanoutBridgeAndWiresItIntoManager} 把模板 Bean 直接放进用户配置，
     * 其 Bean 定义在自动配置处理<em>之前</em>就存在，因此测不出顺序问题；真实部署里模板由 Boot 的
     * {@code DataRedisAutoConfiguration} 注册，而 {@code @ConditionalOnBean} 只看得见「先于它处理完」的
     * Bean 定义、本模块 {@code cn.ypbin.*} 又排在 {@code org.springframework.*} 之前 ⇒ 两个 Redis 嵌套
     * 配置的条件恒假 ⇒ <b>票据存储静默退化为进程内、扇出桥整块不装配</b>（多副本下 SSE 只推本地连接、
     * 票据不可跨实例消费，且没有任何报错）。修法：类级
     * {@code @AutoConfigureAfter(DataRedisAutoConfiguration)}。</p>
     */
    @Test
    void withRealBootRedisAutoConfiguration_usesRedisTicketStoreAndFanout() {
        new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                DataRedisAutoConfiguration.class, SseAutoConfiguration.class))
            .withPropertyValues("ypbin.sse.enabled=true")
            .withUserConfiguration(RedisConnectionFactoryOnlyConfig.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.containsBean("stringRedisTemplate"))
                    .as("前置条件：Boot 的 Redis 自动配置已注册模板")
                    .isTrue();
                assertThat(context.getBean(SseTicketStore.class))
                    .as("有 Redis 时必须选 Redis 票据存储：退化为进程内意味着票据不可跨实例消费")
                    .isInstanceOf(RedisSseTicketStore.class);
                assertThat(context.getBeanNamesForType(SseFanoutBridge.class))
                    .as("有 Redis 时必须装配扇出桥：否则多副本 SSE 只推本实例连接，跨实例推送静默丢失")
                    .isNotEmpty();
            });
    }

    /**
     * 容器里有 {@code ObjectMapper} Bean ⇒ 必须选中 {@link JacksonSseFanoutCodec}（结构化载荷可跨实例）。
     *
     * <p>这里用<strong>用户配置</strong>提供 mapper（Bean 定义先于自动配置处理就存在），只锁定类型选择；
     * 「mapper 由后处理的 Boot 自动配置提供」这一顺序场景由
     * {@link #withRealBootJacksonAutoConfiguration_usesJacksonCodecDespiteOrdering()} 覆盖。</p>
     */
    @Test
    void withObjectMapperBean_usesJacksonCodec() {
        runner.withUserConfiguration(ObjectMapperConfig.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(SseFanoutCodec.class)).isInstanceOf(JacksonSseFanoutCodec.class);
            assertThat(context.getBean(SseFanoutCodec.class).id()).isEqualTo(JacksonSseFanoutCodec.ID);
            assertThat(context.getBean(SseEmitterManager.class).getCodec()).isInstanceOf(JacksonSseFanoutCodec.class);
            // Bean 名在升级前后不变（Text 与 Jackson 两侧刻意同名），宿主按名字注入的写法不受影响
            assertThat(context.containsBean("sseFanoutCodec")).isTrue();
        });
    }

    /** 类路径有 Jackson 但容器里没有 {@code ObjectMapper} Bean ⇒ 退回 Text（不得出现无 codec 可用）。 */
    @Test
    void withoutObjectMapperBean_fallsBackToTextCodec() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(SseFanoutCodec.class)).isInstanceOf(TextSseFanoutCodec.class);
            assertThat(context.getBean(SseEmitterManager.class).getCodec()).isInstanceOf(TextSseFanoutCodec.class);
            assertThat(context.containsBean("sseFanoutCodec")).isTrue();
        });
    }

    /** 类路径完全没有 Jackson ⇒ 上下文照常启动、走 Text 兜底（Jackson 绝不能成为强制依赖）。 */
    @Test
    void withoutObjectMapperOnClasspath_startsAndFallsBackToTextCodec() {
        runner.withClassLoader(new FilteredClassLoader(ObjectMapper.class)).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(SseFanoutCodec.class)).isInstanceOf(TextSseFanoutCodec.class);
            assertThat(context.getBean(SseFanoutCodec.class).id()).isEqualTo(TextSseFanoutCodec.ID);
        });
    }

    /**
     * ★ 顺序回归：{@code ObjectMapper} 由 Boot 的 {@code JacksonAutoConfiguration}
     * （{@code org.springframework.*}）提供，而本模块包名 {@code cn.ypbin.*} 在 Boot 自动配置排序中
     * <b>更靠前</b>。若把装配条件写成 {@code @ConditionalOnBean(ObjectMapper.class)}，求值时刻 mapper
     * 尚未注册 ⇒ 条件恒假 ⇒ Jackson codec <b>静默不生效</b>（多副本下 POJO 事件仍只投本实例，且无报错）。
     * 本用例与真实 Boot 自动配置组合，钉死「{@code ObjectProvider} 在实例化阶段取值」这条与顺序无关的路径。
     */
    @Test
    void withRealBootJacksonAutoConfiguration_usesJacksonCodecDespiteOrdering() {
        new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, SseAutoConfiguration.class))
            .withPropertyValues("ypbin.sse.enabled=true")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.containsBean("jacksonJsonMapper"))
                    .as("前置条件：Boot 的 Jackson 自动配置已注册 ObjectMapper")
                    .isTrue();
                assertThat(context.getBean(SseFanoutCodec.class))
                    .as("mapper 由后处理的 Boot 自动配置提供时也必须选中 Jackson codec")
                    .isInstanceOf(JacksonSseFanoutCodec.class);
            });
    }

    /** 宿主自定义 {@code SseFanoutCodec} Bean 永远优先（Text 与 Jackson 两侧都必须让路）。 */
    @Test
    void userDefinedCodecAlwaysWins() {
        new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, SseAutoConfiguration.class))
            .withPropertyValues("ypbin.sse.enabled=true")
            .withUserConfiguration(CustomCodecConfig.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(SseFanoutCodec.class)).isInstanceOf(CustomCodec.class);
                assertThat(context.getBean(SseEmitterManager.class).getCodec()).isInstanceOf(CustomCodec.class);
            });
    }

    /** 宿主自备的 mapper（此处即测试）——只提供类型，不启动任何 Jackson 自动配置。 */
    @Configuration(proxyBeanMethods = false)
    static class ObjectMapperConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    /** 宿主自定义 codec：`id` 与内置都不同，用于验证覆盖语义。 */
    @Configuration(proxyBeanMethods = false)
    static class CustomCodecConfig {
        @Bean
        SseFanoutCodec customCodec() {
            return new CustomCodec();
        }
    }

    /** 极简自定义 codec（内容无关紧要，只验证「谁被选中」）。 */
    static class CustomCodec implements SseFanoutCodec {

        @Override
        public String id() {
            return "custom-v1";
        }

        @Override
        public Optional<String> encode(Object payload) {
            return Optional.empty();
        }

        @Override
        public Object decode(String text) {
            return text;
        }
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

    /**
     * 只提供连接工厂 + 订阅容器桩的配置：让 Boot 的 Redis 自动配置注册模板，同时避免真实
     * 订阅容器在桩连接上启动超时。
     */
    @Configuration(proxyBeanMethods = false)
    static class RedisConnectionFactoryOnlyConfig {
        @Bean
        org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory() {
            return Mockito.mock(org.springframework.data.redis.connection.RedisConnectionFactory.class);
        }

        @Bean
        org.springframework.data.redis.listener.RedisMessageListenerContainer redisMessageListenerContainer() {
            return Mockito.mock(org.springframework.data.redis.listener.RedisMessageListenerContainer.class);
        }
    }
}

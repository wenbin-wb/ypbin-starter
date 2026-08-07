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

import cn.ypbin.starter.messaging.push.DefaultPushService;
import cn.ypbin.starter.messaging.push.PushService;
import cn.ypbin.starter.messaging.sse.InMemorySseTicketStore;
import cn.ypbin.starter.messaging.sse.RedisSseTicketStore;
import cn.ypbin.starter.messaging.sse.SseEmitterManager;
import cn.ypbin.starter.messaging.sse.SseProperties;
import cn.ypbin.starter.messaging.sse.SseSubscribeController;
import cn.ypbin.starter.messaging.sse.SseTicketController;
import cn.ypbin.starter.messaging.sse.SseTicketStore;
import cn.ypbin.starter.messaging.sse.SseUserIdResolver;
import java.time.Duration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 与统一推送门面自动配置。
 *
 * <p>仅在 Servlet Web 环境、类路径存在 {@link SseEmitter} 且 {@code ypbin.sse.enabled=true} 时生效。
 * 装配连接管理器、统一推送门面 {@link PushService}，并按需注册内置订阅端点。所有 Bean 可被业务方覆盖。</p>
 *
 * <p><strong>为何把 Redis 票据存储放进嵌套的 {@link RedisTicketStoreConfiguration}</strong>：方法级
 * {@code @ConditionalOnClass} 只能阻止 Bean 注册，阻止不了 Spring 对配置类做方法内省——若本类某个
 * {@code @Bean} 方法签名里含 {@code StringRedisTemplate} 而 classpath 无 spring-data-redis，内省阶段即抛
 * {@code NoClassDefFoundError}，条件根本来不及生效。故收拢到<strong>类级</strong>
 * {@code @ConditionalOnClass(StringRedisTemplate.class)} 的嵌套配置，无 Redis 时整个类被跳过、其
 * {@code @Bean} 方法不被内省。用 {@code @Import} 引入（先于本类自身 {@code @Bean} 方法注册），
 * 故存在 Redis 时优先生效，内存兜底的 {@code @ConditionalOnMissingBean} 正确退让。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
@AutoConfiguration
@ConditionalOnClass(SseEmitter.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "ypbin.sse", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(SseProperties.class)
@Import(SseAutoConfiguration.RedisTicketStoreConfiguration.class)
public class SseAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SseEmitterManager sseEmitterManager(SseProperties properties) {
        return new SseEmitterManager(properties.getTimeout(), properties.getHeartbeatIntervalSeconds());
    }

    @Bean
    @ConditionalOnMissingBean
    public PushService pushService(SseEmitterManager sseEmitterManager) {
        return new DefaultPushService(sseEmitterManager);
    }

    /**
     * 一次性订阅票据存储：内存兜底，仅当容器中不存在任何 {@link SseTicketStore} 时装配
     * （Redis 实现存在时即退让，见 {@link RedisTicketStoreConfiguration}）。
     */
    @Bean
    @ConditionalOnMissingBean(SseTicketStore.class)
    public SseTicketStore inMemorySseTicketStore() {
        return new InMemorySseTicketStore();
    }

    /**
     * Redis 分布式票据存储配置。类级 {@code @ConditionalOnClass(StringRedisTemplate.class)}：无 Redis 时
     * 整个类被跳过、其 {@code @Bean} 方法不被内省，从而外层配置在无 spring-data-redis 环境下也能正常启动。
     *
     * <p>类级 {@code @ConditionalOnClass} 只判断 classpath 是否有该类，不代表容器里真有可用的
     * {@link StringRedisTemplate} Bean（如未配置 Redis 连接、或测试环境只引入了依赖未装配自动配置）。
     * 叠加 {@code @ConditionalOnBean(StringRedisTemplate.class)}，两者都满足才展开本配置，避免 Redis Bean
     * 因缺少注入源在启动期抛 {@code UnsatisfiedDependencyException}。</p>
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnBean(StringRedisTemplate.class)
    static class RedisTicketStoreConfiguration {

        /**
         * Redis 分布式票据存储：多节点共享、Lua 原子消费，存在 Redis 时优先装配。
         */
        @Bean
        @ConditionalOnMissingBean(SseTicketStore.class)
        public SseTicketStore redisSseTicketStore(StringRedisTemplate redisTemplate) {
            return new RedisSseTicketStore(redisTemplate);
        }
    }

    /**
     * 内置订阅端点。仅当容器内存在 {@link SseUserIdResolver}（如引入 security 模块）时注册——没有鉴权来源
     * 就不暴露端点，避免无鉴权订阅。用户标识由端点从登录态或一次性票据解析，不接收前端传入的 userId。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(SseUserIdResolver.class)
    @ConditionalOnProperty(prefix = "ypbin.sse", name = "register-endpoint", havingValue = "true", matchIfMissing = true)
    public SseSubscribeController sseSubscribeController(
        SseEmitterManager sseEmitterManager, SseUserIdResolver sseUserIdResolver, SseTicketStore sseTicketStore) {
        return new SseSubscribeController(sseEmitterManager, sseUserIdResolver, sseTicketStore);
    }

    /**
     * 一次性票据签发端点。与订阅端点同条件注册（存在鉴权来源 + register-endpoint）。用于 Header 令牌鉴权
     * 场景：{@code EventSource} 不能带 Authorization 头，前端先用带令牌的普通请求换票，再用 ticket 订阅。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(SseUserIdResolver.class)
    @ConditionalOnProperty(prefix = "ypbin.sse", name = "register-endpoint", havingValue = "true", matchIfMissing = true)
    public SseTicketController sseTicketController(
        SseTicketStore sseTicketStore, SseUserIdResolver sseUserIdResolver, SseProperties properties) {
        return new SseTicketController(
            sseTicketStore, sseUserIdResolver, Duration.ofSeconds(properties.getTicketTtlSeconds()));
    }
}

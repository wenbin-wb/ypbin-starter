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
package cn.ypbin.starter.sign.autoconfigure;

import cn.ypbin.starter.sign.core.DefaultSignAppProvider;
import cn.ypbin.starter.sign.core.InMemoryNonceStore;
import cn.ypbin.starter.sign.core.NonceStore;
import cn.ypbin.starter.sign.core.RedisNonceStore;
import cn.ypbin.starter.sign.core.SignAppProvider;
import cn.ypbin.starter.sign.core.SignChecker;
import cn.ypbin.starter.sign.interceptor.SignInterceptor;
import org.springframework.beans.factory.ObjectProvider;
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
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.ObjectMapper;

/**
 * 接口签名自动配置。
 *
 * <p>仅在 Servlet Web 环境且 {@code ypbin.sign.enabled=true} 时生效。装配 nonce 存储
 * （Redis 优先、内存兜底）、签名校验器与拦截器。签名校验需读取请求体，因此依赖 web 模块的
 * 可重复读过滤器——请在配置中同时开启 {@code ypbin.web.repeatable-read.enabled=true}。</p>
 *
 * <p>Redis nonce 存储装配在嵌套的 {@link RedisNonceStoreConfiguration} 中，用类级
 * {@code @ConditionalOnClass(StringRedisTemplate.class)}：无 spring-data-redis 时整个嵌套类
 * 被跳过、其 {@code @Bean} 方法不被内省——方法级 {@code @ConditionalOnClass} 阻止不了 Spring
 * 对方法签名的内省，签名里含 {@code StringRedisTemplate} 而无该依赖时会先抛
 * NoClassDefFoundError。经 {@code @Import} 先于本类内存兜底注册，存在 Redis 时优先生效、
 * 内存兜底退让。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "ypbin.sign", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(SignProperties.class)
@Import(SignAutoConfiguration.RedisNonceStoreConfiguration.class)
public class SignAutoConfiguration {

    /**
     * 内存 nonce 存储：兜底。无 Redis 或无自定义实现时装配；存在 Redis 时由
     * {@link RedisNonceStoreConfiguration} 先注册的 Redis 实现接管，本 Bean 自动退让。
     */
    @Bean
    @ConditionalOnMissingBean(NonceStore.class)
    public NonceStore inMemoryNonceStore() {
        return new InMemoryNonceStore();
    }

    /**
     * 默认开放应用来源：读取 ypbin.sign.apps 配置。业务方提供自定义实现即可从数据库接管。
     */
    @Bean
    @ConditionalOnMissingBean
    public SignAppProvider signAppProvider(SignProperties properties) {
        return new DefaultSignAppProvider(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public SignChecker signChecker(SignProperties properties, NonceStore nonceStore,
        ObjectProvider<ObjectMapper> objectMapper, SignAppProvider appProvider) {
        return new SignChecker(properties, nonceStore, objectMapper.getIfAvailable(ObjectMapper::new), appProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public SignInterceptor signInterceptor(SignProperties properties, SignChecker signChecker,
        ObjectProvider<ObjectMapper> objectMapper) {
        return new SignInterceptor(properties, signChecker, objectMapper.getIfAvailable(ObjectMapper::new));
    }

    @Bean
    @ConditionalOnMissingBean
    public WebMvcConfigurer signWebMvcConfigurer(SignInterceptor signInterceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(signInterceptor).addPathPatterns("/**");
            }
        };
    }

    /**
     * Redis nonce 存储配置。
     *
     * <p>类级 {@code @ConditionalOnClass} 只判断 classpath 是否有该类，不代表容器里真有可用的
     * {@link StringRedisTemplate} Bean（如未配置 Redis 连接、或测试环境只引入了依赖未装配自动配置）。
     * 叠加 {@code @ConditionalOnBean(StringRedisTemplate.class)}，两者都满足才展开本配置，避免 Redis Bean
     * 因缺少注入源在启动期抛 {@code UnsatisfiedDependencyException}。</p>
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnBean(StringRedisTemplate.class)
    static class RedisNonceStoreConfiguration {

        @Bean
        @ConditionalOnMissingBean(NonceStore.class)
        public NonceStore redisNonceStore(StringRedisTemplate redisTemplate) {
            return new RedisNonceStore(redisTemplate);
        }
    }
}

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

import cn.ypbin.starter.sign.core.InMemoryNonceStore;
import cn.ypbin.starter.sign.core.NonceStore;
import cn.ypbin.starter.sign.core.RedisNonceStore;
import cn.ypbin.starter.sign.core.SignChecker;
import cn.ypbin.starter.sign.interceptor.SignInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 接口签名自动配置。
 *
 * <p>仅在 Servlet Web 环境且 {@code ypbin.sign.enabled=true} 时生效。装配 nonce 存储
 * （Redis 优先、内存兜底）、签名校验器与拦截器。签名校验需读取请求体，因此依赖 web 模块的
 * 可重复读过滤器——请在配置中同时开启 {@code ypbin.web.repeatable-read.enabled=true}。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "ypbin.sign", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(SignProperties.class)
public class SignAutoConfiguration {

    /**
     * Redis nonce 存储：存在 Redis 时优先（声明在内存兜底之前，顺序保证可靠）。
     */
    @Bean
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnMissingBean(NonceStore.class)
    public NonceStore redisNonceStore(ObjectProvider<StringRedisTemplate> redisTemplate) {
        StringRedisTemplate template = redisTemplate.getIfAvailable();
        return template != null ? new RedisNonceStore(template) : new InMemoryNonceStore();
    }

    /**
     * 内存 nonce 存储：兜底。
     */
    @Bean
    @ConditionalOnMissingBean(NonceStore.class)
    public NonceStore inMemoryNonceStore() {
        return new InMemoryNonceStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public SignChecker signChecker(SignProperties properties, NonceStore nonceStore,
        ObjectProvider<ObjectMapper> objectMapper) {
        return new SignChecker(properties, nonceStore, objectMapper.getIfAvailable(ObjectMapper::new));
    }

    @Bean
    @ConditionalOnMissingBean
    public SignInterceptor signInterceptor(SignProperties properties, SignChecker signChecker,
        ObjectProvider<ObjectMapper> objectMapper) {
        return new SignInterceptor(properties, signChecker, objectMapper.getIfAvailable(ObjectMapper::new));
    }

    @Bean
    public WebMvcConfigurer signWebMvcConfigurer(SignInterceptor signInterceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(signInterceptor).addPathPatterns("/**");
            }
        };
    }
}

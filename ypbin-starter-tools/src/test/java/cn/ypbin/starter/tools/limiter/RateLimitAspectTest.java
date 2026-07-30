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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * {@link RateLimitAspect} 真实 AOP 代理织入测试。
 *
 * @author wenbin
 * @since 2026-07-30
 */
class RateLimitAspectTest {

    private AnnotationConfigApplicationContext ctx;
    private LimitedService service;

    @BeforeEach
    void setUp() {
        ctx = new AnnotationConfigApplicationContext(Config.class);
        service = ctx.getBean(LimitedService.class);
    }

    @AfterEach
    void tearDown() {
        if (ctx != null) {
            ctx.close();
        }
    }

    @Test
    void allowsWithinLimit_thenRejects() {
        // count=3：前 3 次放行，第 4 次触发限流
        assertThat(service.call()).isEqualTo("ok");
        assertThat(service.call()).isEqualTo("ok");
        assertThat(service.call()).isEqualTo("ok");
        assertThatThrownBy(() -> service.call())
            .isInstanceOf(RateLimitException.class)
            .hasMessageContaining("频繁");
    }

    @Test
    void spelKey_isolatesByArgument() {
        // 按用户 ID 维度限流：不同用户各自独立计数
        assertThat(service.callByUser(1L)).isEqualTo("ok");
        assertThat(service.callByUser(1L)).isEqualTo("ok");
        assertThat(service.callByUser(2L)).isEqualTo("ok"); // 不同 key，不受用户 1 影响
        assertThatThrownBy(() -> service.callByUser(1L))
            .isInstanceOf(RateLimitException.class);
    }

    @EnableAspectJAutoProxy
    static class Config {
        @Bean
        RateLimiterStore rateLimiterStore() {
            return new InMemoryRateLimiterStore();
        }

        @Bean
        RateLimitAspect rateLimitAspect(RateLimiterStore store) {
            return new RateLimitAspect(store);
        }

        @Bean
        LimitedService limitedService() {
            return new LimitedService();
        }
    }

    static class LimitedService {
        @RateLimit(window = 60, count = 3, byIp = false, message = "请求过于频繁")
        public String call() {
            return "ok";
        }

        @RateLimit(key = "#userId", window = 60, count = 2, byIp = false)
        public String callByUser(Long userId) {
            return "ok";
        }
    }
}

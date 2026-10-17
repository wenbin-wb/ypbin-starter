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

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
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
    private CountingStore store;

    @BeforeEach
    void setUp() {
        ctx = new AnnotationConfigApplicationContext(Config.class);
        service = ctx.getBean(LimitedService.class);
        store = ctx.getBean(CountingStore.class);
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

    @Test
    void zeroWindow_failsFastAndNeverCounts() {
        // window=0 ⇒ Duration 为零 ⇒ Redis 侧 EXPIRE key 0 立即删键、内存侧窗口立即过期，
        // 每次调用都是新窗口，限流形同虚设。必须在首次调用就拒绝，且不得进入计数存储。
        assertThatThrownBy(() -> service.zeroWindow())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("window")
            .hasMessageContaining(">= 1");
        assertThat(store.calls)
            .as("非法窗口不得进入计数存储（否则等于静默放行全部请求）")
            .hasValue(0);
    }

    @Test
    void negativeWindow_failsFastAndNeverCounts() {
        assertThatThrownBy(() -> service.negativeWindow())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("当前 -5");
        assertThat(store.calls).hasValue(0);
    }

    @Test
    void minimumLegalWindow_stillCounts() {
        // 边界值 window=1 必须放行（避免 fail-fast 校验写宽把合法配置也拒了）
        assertThat(service.minWindow()).isEqualTo("ok");
        assertThat(store.calls).hasValue(1);
    }

    @EnableAspectJAutoProxy
    static class Config {
        @Bean
        RateLimiterStore rateLimiterStore() {
            return new CountingStore();
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

    /** 记录 {@code incrementAndGet} 调用次数的内存存储：用于断言非法配置不会进入计数环节。 */
    static class CountingStore implements RateLimiterStore {

        private final RateLimiterStore delegate = new InMemoryRateLimiterStore();

        final AtomicInteger calls = new AtomicInteger();

        @Override
        public long incrementAndGet(String key, Duration window) {
            calls.incrementAndGet();
            return delegate.incrementAndGet(key, window);
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

        @RateLimit(window = 0, count = 3, byIp = false)
        public String zeroWindow() {
            return "ok";
        }

        @RateLimit(window = -5, count = 3, byIp = false)
        public String negativeWindow() {
            return "ok";
        }

        @RateLimit(window = 1, count = 3, byIp = false)
        public String minWindow() {
            return "ok";
        }
    }
}

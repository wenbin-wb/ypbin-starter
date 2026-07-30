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
package cn.ypbin.starter.tools.idempotent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * {@link IdempotentAspect} 真实 AOP 代理织入测试。
 *
 * @author wenbin
 * @since 2026-07-30
 */
class IdempotentAspectTest {

    private AnnotationConfigApplicationContext ctx;
    private OrderService service;

    @BeforeEach
    void setUp() {
        ctx = new AnnotationConfigApplicationContext(Config.class);
        service = ctx.getBean(OrderService.class);
    }

    @AfterEach
    void tearDown() {
        if (ctx != null) {
            ctx.close();
        }
    }

    @Test
    void sameKey_secondCallRejected() {
        assertThat(service.submit("order-1")).isEqualTo("done");
        assertThatThrownBy(() -> service.submit("order-1"))
            .isInstanceOf(IdempotentException.class)
            .hasMessageContaining("重复");
    }

    @Test
    void differentKeys_bothPass() {
        assertThat(service.submit("order-A")).isEqualTo("done");
        assertThat(service.submit("order-B")).isEqualTo("done");
    }

    @EnableAspectJAutoProxy
    static class Config {
        @Bean
        IdempotentStore idempotentStore() {
            return new InMemoryIdempotentStore();
        }

        @Bean
        IdempotentAspect idempotentAspect(IdempotentStore store) {
            return new IdempotentAspect(store);
        }

        @Bean
        OrderService orderService() {
            return new OrderService();
        }
    }

    static class OrderService {
        @Idempotent(key = "#orderNo", interval = 60, message = "请勿重复提交")
        public String submit(String orderNo) {
            return "done";
        }
    }
}

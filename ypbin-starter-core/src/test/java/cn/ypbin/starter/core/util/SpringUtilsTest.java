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
package cn.ypbin.starter.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.test.context.TestPropertySource;

/**
 * SpringUtils 集成测试：容器装配后静态工具应能读取上下文、取 Bean、发事件、读环境。
 *
 * @author wenbin
 * @since 2026-08-31
 */
@SpringBootTest(classes = {SpringUtils.class, SpringUtilsTest.TestEventListener.class})
@TestPropertySource(properties = "ypbin.test.key=hello")
class SpringUtilsTest {

    @Autowired
    private ApplicationContext context;

    @Component
    static class TestEventListener {

        final AtomicBoolean received = new AtomicBoolean(false);

        @EventListener
        void on(String event) {
            received.set(true);
        }
    }

    @Test
    void shouldExposeApplicationContext() {
        assertThat(SpringUtils.getApplicationContext()).isSameAs(context);
        assertThat(SpringUtils.containsBean("springUtils")).isTrue();
        assertThat(SpringUtils.getBean(SpringUtils.class)).isNotNull();
        assertThat(SpringUtils.getBean("springUtils", SpringUtils.class)).isNotNull();
    }

    @Test
    void shouldReadEnvironment() {
        assertThat(SpringUtils.getEnvironment()).isNotNull();
        assertThat(SpringUtils.getProperty("ypbin.test.key")).isEqualTo("hello");
        assertThat(SpringUtils.getActiveProfiles()).isNotNull();
    }

    @Test
    void shouldPublishEventToListener() {
        SpringUtils.publishEvent("spring-utils-test-event");
        TestEventListener listener = context.getBean(TestEventListener.class);
        assertThat(listener.received).isTrue();
    }

    @Test
    void shouldReturnEventPublisher() {
        assertThat(SpringUtils.getEventPublisher()).isNotNull();
    }

    @Test
    void contextShouldExposeExpectedBeans() {
        // 上下文应含 SpringUtils 自身与测试监听器
        assertThat(context.getBeansOfType(SpringUtils.class)).hasSize(1);
        assertThat(context.getBeansOfType(TestEventListener.class)).hasSize(1);
    }

    @Test
    void getActiveProfilesShouldNotBeNull() {
        List<String> profiles = List.of(SpringUtils.getActiveProfiles());
        assertThat(profiles).isNotNull();
    }
}

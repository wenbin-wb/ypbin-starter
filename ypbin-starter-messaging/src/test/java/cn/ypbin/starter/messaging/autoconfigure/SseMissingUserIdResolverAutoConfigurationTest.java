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

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.ypbin.starter.messaging.sse.SseUserIdResolver;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

/**
 * SSE「已开启但无 SseUserIdResolver」告警测试。
 *
 * @author wenbin
 * @since 2026-09-17
 */
class SseMissingUserIdResolverAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(SseMissingUserIdResolverAutoConfiguration.class))
        .withPropertyValues("ypbin.sse.enabled=true");

    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        appender = new ListAppender<>();
        appender.start();
        logger().addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger().detachAppender(appender);
    }

    private static Logger logger() {
        return (Logger) LoggerFactory.getLogger(SseMissingUserIdResolverAutoConfiguration.class);
    }

    private List<String> warnMessages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void shouldWarnWhenResolverMissing() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(warnMessages()).anySatisfy(message ->
                assertThat(message).contains("内置 SSE 订阅/换票端点不会注册"));
        });
    }

    @Test
    void shouldNotWarnWhenResolverPresent() {
        runner.withBean(SseUserIdResolver.class, () -> Mockito.mock(SseUserIdResolver.class))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(warnMessages()).isEmpty();
            });
    }

    @Test
    void shouldNotWarnWhenEndpointRegistrationDisabled() {
        new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SseMissingUserIdResolverAutoConfiguration.class))
            .withPropertyValues("ypbin.sse.enabled=true", "ypbin.sse.register-endpoint=false")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(SseMissingUserIdResolverAutoConfiguration.class);
            });
    }
}

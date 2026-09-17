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
package cn.ypbin.starter.gateway.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.ypbin.starter.gateway.auth.GatewayAuthProvider;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;

/**
 * 网关统一认证「已开启但无 GatewayAuthProvider」告警测试。
 *
 * @author wenbin
 * @since 2026-09-17
 */
class GatewayAuthMissingProviderAutoConfigurationTest {

    private final ReactiveWebApplicationContextRunner runner = new ReactiveWebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(GatewayAuthMissingProviderAutoConfiguration.class))
        .withPropertyValues("ypbin.gateway.auth.enabled=true");

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
        return (Logger) LoggerFactory.getLogger(GatewayAuthMissingProviderAutoConfiguration.class);
    }

    private List<String> warnMessages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void shouldWarnWhenProviderMissing() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(warnMessages()).anySatisfy(message ->
                assertThat(message).contains("网关鉴权过滤器不会注册"));
        });
    }

    @Test
    void shouldNotWarnWhenProviderPresent() {
        runner.withBean(GatewayAuthProvider.class, () -> Mockito.mock(GatewayAuthProvider.class))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(warnMessages()).isEmpty();
            });
    }

    @Test
    void shouldNotActivateWhenAuthDisabled() {
        new ReactiveWebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GatewayAuthMissingProviderAutoConfiguration.class))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(GatewayAuthMissingProviderAutoConfiguration.class);
            });
    }
}

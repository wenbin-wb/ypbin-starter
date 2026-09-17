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
package cn.ypbin.starter.datapermission.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.ypbin.starter.datapermission.core.DataScopeHandler;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 数据权限「已开启但无 DataScopeHandler」告警测试。
 *
 * @author wenbin
 * @since 2026-09-17
 */
class DataPermissionMissingHandlerAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(DataPermissionMissingHandlerAutoConfiguration.class))
        .withPropertyValues("ypbin.data-permission.enabled=true");

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
        return (Logger) LoggerFactory.getLogger(DataPermissionMissingHandlerAutoConfiguration.class);
    }

    private List<String> warnMessages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void shouldWarnWhenHandlerMissing() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(warnMessages()).anySatisfy(message ->
                assertThat(message).contains("数据权限拦截不会生效"));
        });
    }

    @Test
    void shouldNotWarnWhenHandlerPresent() {
        runner.withBean(DataScopeHandler.class, () -> Mockito.mock(DataScopeHandler.class))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(warnMessages()).isEmpty();
            });
    }

    @Test
    void shouldNotActivateWhenFeatureDisabled() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataPermissionMissingHandlerAutoConfiguration.class))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(DataPermissionMissingHandlerAutoConfiguration.class);
            });
    }
}

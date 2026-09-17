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
package cn.ypbin.starter.apicrypto.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.ypbin.starter.apicrypto.core.ApiCryptoProvider;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

/**
 * 接口加解密「已启用但无加解密器」告警测试。
 *
 * @author wenbin
 * @since 2026-09-17
 */
class ApiCryptoMissingProviderAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ApiCryptoMissingProviderAutoConfiguration.class))
        .withPropertyValues("ypbin.api-crypto.enabled=true");

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
        return (Logger) LoggerFactory.getLogger(ApiCryptoMissingProviderAutoConfiguration.class);
    }

    private List<String> warnMessages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void shouldWarnWhenProviderMissing() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(warnMessages()).anySatisfy(message ->
                assertThat(message).contains("@ApiEncrypt/@ApiDecrypt 不会生效"));
        });
    }

    @Test
    void shouldNotWarnWhenProviderPresent() {
        runner.withBean(ApiCryptoProvider.class, () -> Mockito.mock(ApiCryptoProvider.class))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(warnMessages()).isEmpty();
            });
    }
}

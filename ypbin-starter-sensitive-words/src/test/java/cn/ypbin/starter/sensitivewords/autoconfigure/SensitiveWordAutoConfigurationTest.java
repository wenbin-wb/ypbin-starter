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
package cn.ypbin.starter.sensitivewords.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.ypbin.starter.sensitivewords.core.SensitiveWordProvider;
import cn.ypbin.starter.sensitivewords.core.SensitiveWordService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * {@link SensitiveWordAutoConfiguration} 词库缺失告警测试。
 *
 * <p>本模块默认开启：未配词库也未提供 Provider 时过滤等同无操作，必须有一条启动期 WARN 让人知道
 * 「这个能力没生效、以及怎么启用」；反之配了词库或提供了 Provider 时不得误报。</p>
 *
 * @author wenbin
 * @since 2026-09-17
 */
class SensitiveWordAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(SensitiveWordAutoConfiguration.class));

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
        return (Logger) LoggerFactory.getLogger(SensitiveWordAutoConfiguration.class);
    }

    private List<String> warnMessages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void shouldWarnWhenNoProviderAndEmptyWords() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(SensitiveWordService.class);
            assertThat(warnMessages()).anySatisfy(message ->
                assertThat(message).contains("敏感词过滤不会命中任何词"));
        });
    }

    @Test
    void shouldNotWarnWhenWordsConfigured() {
        runner.withPropertyValues("ypbin.sensitive-words.words=违规词,另一个词")
            .run(context -> {
                assertThat(context).hasSingleBean(SensitiveWordService.class);
                assertThat(warnMessages()).isEmpty();
            });
    }

    @Test
    void shouldNotWarnWhenProviderSuppliesWords() {
        runner.withBean(SensitiveWordProvider.class, () -> () -> List.of("违规词"))
            .run(context -> {
                assertThat(context).hasSingleBean(SensitiveWordService.class);
                assertThat(warnMessages()).isEmpty();
            });
    }
}

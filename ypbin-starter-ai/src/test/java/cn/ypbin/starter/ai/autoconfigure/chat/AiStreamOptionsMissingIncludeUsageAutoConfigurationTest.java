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
package cn.ypbin.starter.ai.autoconfigure.chat;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 「自定义 stream-options 但未开启 include-usage」启动期告警测试。
 *
 * <p>三条基本行为：配了但未设 {@code include-usage} ⇒ 有 WARN；配了且设为 {@code true} ⇒ 无 WARN；
 * 完全没配 ⇒ 无 WARN（默认安静，框架会替宿主默认请求用量）。</p>
 *
 * @author wenbin
 * @since 2026-09-18
 */
class AiStreamOptionsMissingIncludeUsageAutoConfigurationTest {

    private static final String INCLUDE_USAGE = "spring.ai.openai.chat.options.stream-options.include-usage";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AiStreamOptionsMissingIncludeUsageAutoConfiguration.class));

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
        return (Logger) LoggerFactory.getLogger(AiStreamOptionsMissingIncludeUsageAutoConfiguration.class);
    }

    private List<String> warnMessages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void shouldWarnWhenStreamOptionsConfiguredWithoutIncludeUsage() {
        runner.withPropertyValues("spring.ai.openai.chat.options.stream-options.include-obfuscation=true")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(warnMessages()).anySatisfy(message -> assertThat(message)
                    // ① 现象：用量无法统计、token 记为「未知」
                    .contains("AI 用量将无法统计")
                    .contains("null")
                    // ② 原因：自定义 stream-options 后框架不再默认请求 include_usage
                    .contains("stream_options.include_usage=true")
                    // ③ 动作：给出完整配置键
                    .contains(INCLUDE_USAGE));
            });
    }

    @Test
    void shouldWarnWhenIncludeUsageExplicitlyFalse() {
        runner.withPropertyValues(INCLUDE_USAGE + "=false")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(warnMessages()).anySatisfy(message -> assertThat(message)
                    .contains("AI 用量将无法统计"));
            });
    }

    @Test
    void shouldWarnWhenOnlyAdditionalPropertiesConfigured() {
        // 「任意一项」都要覆盖：additional-properties 是 stream-options 下的非固定子键
        runner.withPropertyValues(
                "spring.ai.openai.chat.options.stream-options.additional-properties.x-beta-header=1")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(warnMessages()).anySatisfy(message -> assertThat(message)
                    .contains("AI 用量将无法统计"));
            });
    }

    @Test
    void shouldNotWarnWhenIncludeUsageTrue() {
        runner.withPropertyValues(INCLUDE_USAGE + "=true")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(warnMessages()).isEmpty();
            });
    }

    @Test
    void shouldNotWarnWhenIncludeUsageTrueInRelaxedForm() {
        // 宿主用驼峰写法同样要被认为「已显式开启」，否则会误报
        runner.withPropertyValues("spring.ai.openai.chat.options.streamOptions.includeUsage=true")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(warnMessages()).isEmpty();
            });
    }

    @Test
    void shouldNotWarnWhenStreamOptionsAbsent() {
        // 默认情形：框架会自行请求 stream_options.include_usage=true，必须完全安静（否则是噪音/误报）
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(warnMessages()).isEmpty();
        });
    }
}

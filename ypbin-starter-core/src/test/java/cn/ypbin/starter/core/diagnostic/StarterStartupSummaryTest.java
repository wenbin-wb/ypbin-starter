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
package cn.ypbin.starter.core.diagnostic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import cn.ypbin.starter.core.autoconfigure.CoreAutoConfiguration;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Condition;
import org.springframework.context.support.GenericApplicationContext;

/**
 * {@link StarterStartupSummary} 启动自省清单测试。
 *
 * <p>守护三件事：只统计「条件全匹配」的 ypbin starter 自动配置（classpath 有 jar ≠ 装配成功）、
 * 按模块聚合输出单行、以及<b>任何异常都不得中断应用启动</b>。</p>
 *
 * @author wenbin
 * @since 2026-10-17
 */
class StarterStartupSummaryTest {

    /** 条件表达式占位：本测试只关心报告里的匹配结果，不关心条件本身。 */
    private static final Condition ALWAYS = (context, metadata) -> true;

    private static ConditionEvaluationReport newReport() {
        // ConditionEvaluationReport 的构造器不可见，官方入口是 get(beanFactory)（不存在时会创建并注册）
        return ConditionEvaluationReport.get(new DefaultListableBeanFactory());
    }

    @Test
    @DisplayName("只统计条件全匹配的 ypbin starter 自动配置（未匹配/第三方均排除）")
    void onlyFullMatchStarterAutoConfigurations() {
        ConditionEvaluationReport report = newReport();
        report.recordConditionEvaluation("cn.ypbin.starter.cache.autoconfigure.MultiLevelCacheAutoConfiguration",
            ALWAYS, ConditionOutcome.match());
        report.recordConditionEvaluation("cn.ypbin.starter.security.autoconfigure.SecurityAutoConfiguration",
            ALWAYS, ConditionOutcome.match());
        report.recordConditionEvaluation("cn.ypbin.starter.tools.autoconfigure.ToolsAutoConfiguration",
            ALWAYS, ConditionOutcome.noMatch("测试：条件不满足"));
        report.recordConditionEvaluation("org.springframework.boot.autoconfigure.web.WebMvcAutoConfiguration",
            ALWAYS, ConditionOutcome.match());

        Set<String> active = StarterStartupSummary.positiveAutoConfigurations(report);

        assertThat(active).containsExactly(
            "cn.ypbin.starter.cache.autoconfigure.MultiLevelCacheAutoConfiguration",
            "cn.ypbin.starter.security.autoconfigure.SecurityAutoConfiguration");
    }

    @Test
    @DisplayName("单行汇总按模块聚合、模块名字典序，并带 Boot/Java 版本")
    void summaryFormatAggregatesByModule() {
        Set<String> active = Set.of(
            "cn.ypbin.starter.security.autoconfigure.SecurityAutoConfiguration",
            "cn.ypbin.starter.security.autoconfigure.SecuritySseAutoConfiguration",
            "cn.ypbin.starter.cache.autoconfigure.CacheAutoConfiguration");

        String summary = StarterStartupSummary.formatSummary(active);

        assertThat(summary).startsWith("[ypbin-starter] Activated: cache(1), security(2)");
        assertThat(summary).contains("| starter auto-configurations=3");
        assertThat(summary).contains("Spring Boot").contains("Java");
        assertThat(summary).doesNotContain("\n");
    }

    @Test
    @DisplayName("无激活模块时输出 none，不抛异常")
    void summaryHandlesEmptySet() {
        String summary = StarterStartupSummary.formatSummary(Set.of());

        assertThat(summary).startsWith("[ypbin-starter] Activated: none");
        assertThat(summary).contains("starter auto-configurations=0");
    }

    @Test
    @DisplayName("模块名取包段近似；非 starter 命名空间归入 other")
    void moduleNameExtraction() {
        assertThat(StarterStartupSummary.moduleOf(
            "cn.ypbin.starter.cache.autoconfigure.CacheAutoConfiguration")).isEqualTo("cache");
        assertThat(StarterStartupSummary.moduleOf(
            "cn.ypbin.starter.extension.tenant.autoconfigure.TenantAutoConfiguration")).isEqualTo("extension");
        assertThat(StarterStartupSummary.moduleOf(
            "org.springframework.boot.autoconfigure.web.WebMvcAutoConfiguration")).isEqualTo("other");
    }

    @Test
    @DisplayName("★ 条件报告缺失时不得中断启动（观测能力不影响运行）")
    void listenerNeverPropagatesFailure() {
        StarterStartupSummary summary = new StarterStartupSummary();
        GenericApplicationContext context = new GenericApplicationContext();
        ApplicationReadyEvent event = new ApplicationReadyEvent(
            new SpringApplication(), new String[0], context, Duration.ZERO);

        assertThatCode(() -> summary.onApplicationEvent(event)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("默认装配自省清单；显式关闭后不注册")
    void beanRegisteredByDefaultAndCanBeDisabled() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CoreAutoConfiguration.class))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(StarterStartupSummary.class);
                assertThat(context).hasSingleBean(StarterDiagnosticsProperties.class);
                assertThat(context.getBean(StarterDiagnosticsProperties.class).isStartupSummaryEnabled()).isTrue();
            });

        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CoreAutoConfiguration.class))
            .withPropertyValues(StarterDiagnosticsProperties.PREFIX + ".startup-summary-enabled=false")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(StarterStartupSummary.class);
            });
    }
}

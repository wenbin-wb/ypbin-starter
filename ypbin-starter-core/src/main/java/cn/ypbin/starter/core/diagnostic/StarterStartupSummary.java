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

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

/**
 * Starter 启动自省清单：应用就绪时打印<strong>一行</strong>「已激活的 ypbin starter 模块」汇总。
 *
 * <p><strong>解决什么问题</strong>：starter 的装配日志分散在几十个自动配置类里，接入方（尤其新同事）
 * 无法一眼确认"我引的模块到底生效了没有"。本监听器从 Boot 的 {@link ConditionEvaluationReport}
 * 读取<strong>条件全匹配</strong>的自动配置类，按模块聚合后输出单行，例如：</p>
 * <pre>{@code
 * [ypbin-starter] Activated: cache(2), core(1), security(6), tools(3) | starter auto-configurations=12 | Spring Boot 4.1.1 / Java 21
 * }</pre>
 *
 * <p><strong>为什么取「条件全匹配」而不是扫描 classpath</strong>：classpath 上有 jar ≠ 实际装配成功
 * （条件可能因缺 Bean/缺配置而为假，这正是本仓反复出现的"静默恒假"形态）。条件报告反映的是
 * 真实求值结果，因此它能直接暴露"引了但没装配"。</p>
 *
 * <p><strong>模块名是包段近似</strong>：{@code cn.ypbin.starter.<module>.*} 取第四段作为模块名
 * （如 {@code cache}、{@code security}）；{@code extension.*} 这类多级包取到 {@code extension}。
 * 该值只用于日志可读性，不作为任何逻辑判据。</p>
 *
 * <p><strong>失败不影响启动</strong>：自省清单是纯观测能力，任何异常都只记 WARN（带完整堆栈）并返回，
 * 绝不因此中断应用启动——但也绝不静默吞掉（否则清单不出现时无人能判断原因）。</p>
 *
 * <p>装配开关见 {@link StarterDiagnosticsProperties#isStartupSummaryEnabled()}（默认开启）。</p>
 *
 * @author wenbin
 * @since 2026-10-17
 */
public class StarterStartupSummary implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(StarterStartupSummary.class);

    /** ypbin starter 的根包（自动配置类命名空间） */
    private static final String STARTER_PACKAGE_PREFIX = "cn.ypbin.starter";

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            ConditionEvaluationReport report =
                ConditionEvaluationReport.find(event.getApplicationContext().getBeanFactory());
            Set<String> active = report == null ? Set.of() : positiveAutoConfigurations(report);
            log.info(formatSummary(active));
        } catch (RuntimeException ex) {
            // 观测能力不得影响启动：记 WARN + 完整堆栈，便于排查"清单为何没出现"
            log.warn("[ypbin-starter] 启动自省清单生成失败（不影响应用运行）", ex);
        }
    }

    /**
     * 从条件评估报告中取出条件<strong>全匹配</strong>的 ypbin starter 自动配置类。
     *
     * @param report 条件评估报告
     * @return 已激活的自动配置类名（按字典序）
     */
    static Set<String> positiveAutoConfigurations(ConditionEvaluationReport report) {
        TreeSet<String> active = new TreeSet<>();
        report.getConditionAndOutcomesBySource().forEach((source, outcomes) -> {
            if (source != null
                && source.startsWith(STARTER_PACKAGE_PREFIX + ".")
                && outcomes != null
                && outcomes.isFullMatch()) {
                active.add(source);
            }
        });
        return active;
    }

    /**
     * 生成单行汇总文案（时间戳由日志框架补，便于 greps 定位）。
     *
     * @param activeAutoConfigurations 已激活的自动配置类
     * @return 单行汇总
     */
    static String formatSummary(Set<String> activeAutoConfigurations) {
        Map<String, Long> byModule = activeAutoConfigurations.stream()
            .collect(Collectors.groupingBy(StarterStartupSummary::moduleOf, TreeMap::new, Collectors.counting()));
        String modules = byModule.isEmpty()
            ? "none"
            : byModule.entrySet().stream()
                .map(entry -> entry.getKey() + "(" + entry.getValue() + ")")
                .collect(Collectors.joining(", "));
        return "[ypbin-starter] Activated: " + modules
            + " | starter auto-configurations=" + activeAutoConfigurations.size()
            + " | Spring Boot " + SpringBootVersion.getVersion()
            + " / Java " + System.getProperty("java.version");
    }

    /**
     * 取模块名（包段近似，仅用于日志可读性）。
     *
     * @param autoConfigurationClass 自动配置类全限定名
     * @return 模块名；非 ypbin starter 命名空间返回 {@code other}
     */
    static String moduleOf(String autoConfigurationClass) {
        String prefix = STARTER_PACKAGE_PREFIX + ".";
        if (!autoConfigurationClass.startsWith(prefix)) {
            return "other";
        }
        String rest = autoConfigurationClass.substring(prefix.length());
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }
}

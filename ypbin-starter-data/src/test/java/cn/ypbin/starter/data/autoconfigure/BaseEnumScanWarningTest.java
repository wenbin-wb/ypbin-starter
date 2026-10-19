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
package cn.ypbin.starter.data.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.ypbin.starter.core.enums.BaseEnum;
import cn.ypbin.starter.data.enums.BaseEnumScanner;
import cn.ypbin.starter.data.enums.BaseEnumTypeHandlerRegistrar;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * 「扫描 0 命中」的日志语义测试（技术债 1）。
 *
 * <p><b>要防的是静默失效</b>：{@code scan-packages} 默认 {@code cn.ypbin}，宿主包名不同（或拼错）时
 * 扫描 0 个枚举、一个 WARN 都没有，所有 {@code BaseEnum} 仍按 {@code name()} 落库——与本能力
 * 「按 value 存储」的契约不符，且与其它服务/前端按 value 约定的数据不一致。违反「禁静默降级」。</p>
 *
 * <p><b>同时要防「告警噪音」</b>：宿主根本没用这个能力时若也 WARN，绝大多数无关宿主每次启动都被骚扰，
 * 久而久之所有人都会忽略这条告警——那等于没有告警。故两条分支必须分开断言。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
class BaseEnumScanWarningTest {

    /** 本测试类内部的枚举实现：让「classpath 上存在 BaseEnum 实现」成为既定事实。 */
    enum ProbeStatus implements BaseEnum<Integer> {
        OFF(0, "关"),
        ON(1, "开");

        private final Integer value;
        private final String description;

        ProbeStatus(Integer value, String description) {
            this.value = value;
            this.description = description;
        }

        @Override
        public Integer getValue() {
            return value;
        }

        @Override
        public String getDescription() {
            return description;
        }
    }

    private ListAppender<ILoggingEvent> appender;

    private Logger configLogger;

    private Logger scannerLogger;

    @BeforeEach
    void attachAppender() {
        appender = new ListAppender<>();
        appender.start();
        configLogger = (Logger) LoggerFactory.getLogger(DataAutoConfiguration.class);
        scannerLogger = (Logger) LoggerFactory.getLogger(BaseEnumScanner.class);
        configLogger.addAppender(appender);
        scannerLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        configLogger.detachAppender(appender);
        scannerLogger.detachAppender(appender);
    }

    private List<String> warnMessages() {
        return appender.list.stream()
            .filter(event -> event.getLevel() == Level.WARN)
            .map(ILoggingEvent::getFormattedMessage)
            .toList();
    }

    // ------------------------------------------------------------------ (a) 命中：不 WARN

    @Test
    @DisplayName("(a) scan-packages 命中时注册数正确，且不产生任何 WARN")
    void matchingScanPackagesShouldRegisterWithoutWarning() {
        // 本测试类所在的包（ProbeStatus 在此），命中即验证「包在范围内 → 能扫到并注册」
        List<Class<?>> scanned = BaseEnumScanner.scan(List.of("cn.ypbin.starter.data.autoconfigure"));

        assertThat(scanned).contains(ProbeStatus.class);
        Configuration configuration = new Configuration();
        List<Class<?>> registered = BaseEnumTypeHandlerRegistrar.register(configuration, scanned);

        assertThat(registered).contains(ProbeStatus.class);
        assertThat(BaseEnumTypeHandlerRegistrar.isRegistered(configuration, ProbeStatus.class)).isTrue();
        // 命中的正常路径根本不进入 reportEmptyScan，不应产生 WARN
        assertThat(warnMessages()).isEmpty();
    }

    // ------------------------------------------------------------------ (b) 有实现但没扫到：WARN

    @Test
    @DisplayName("(b) classpath 有 BaseEnum 实现但 scan-packages 指向不存在的包 ⇒ WARN 且注册 0 个")
    void baseEnumOutsideScanPackagesShouldWarn() {
        Set<String> wrongPackages = Set.of("com.example.notexisting");
        List<Class<?>> scanned = BaseEnumScanner.scan(wrongPackages);

        // 前置事实：配错包名时确实扫不到
        assertThat(scanned).isEmpty();

        Configuration configuration = new Configuration();
        List<Class<?>> registered = BaseEnumTypeHandlerRegistrar.register(configuration, scanned);
        assertThat(registered).isEmpty();

        // 装配层的判别逻辑（传入「classpath 上确实存在实现」的真实事实）
        List<Class<?>> allBaseEnums = BaseEnumScanner.scanAll();
        assertThat(allBaseEnums).as("测试 classpath 上必须存在 BaseEnum 实现").contains(ProbeStatus.class);
        DataAutoConfiguration.reportEmptyScan(wrongPackages, allBaseEnums);

        List<String> warns = warnMessages();
        assertThat(warns).hasSize(1);
        String warn = warns.get(0);
        // 告警必须包含：配置值（可据此定位）、后果（按 name 落库）、修正动作
        assertThat(warn)
            .contains("com.example.notexisting")
            .contains("scan-packages")
            .contains("name()")
            .contains("ProbeStatus");
    }

    @Test
    @DisplayName("(b2) WARN 文案必须给出可直接照做的配置项名，而不只是「出错了」")
    void warnShouldContainActionableConfigKey() {
        DataAutoConfiguration.reportEmptyScan(Set.of("com.example.wrong"), List.of(ProbeStatus.class));

        assertThat(warnMessages()).singleElement().satisfies(warn ->
            assertThat(warn).contains("ypbin.data.base-enum.scan-packages"));
    }

    @Test
    @DisplayName("(b3) 实现数量很多时告警只展示限量示例，不刷屏")
    void warnShouldLimitSampleList() {
        DataAutoConfiguration.reportEmptyScan(Set.of("com.example.wrong"),
            List.of(ProbeStatus.class, ProbeStatus.class, ProbeStatus.class,
                ProbeStatus.class, ProbeStatus.class, ProbeStatus.class, ProbeStatus.class));

        assertThat(warnMessages()).singleElement()
            .satisfies(warn -> assertThat(warn).contains("存在 7 个 BaseEnum 实现"));
    }

    // ------------------------------------------------------------------ (c) 完全没实现：不 WARN

    @Test
    @DisplayName("(c) classpath 完全没有 BaseEnum 实现时只记 INFO，不产生 WARN（防噪音回归）")
    void noBaseEnumImplementationShouldNotWarn() {
        DataAutoConfiguration.reportEmptyScan(Set.of("cn.ypbin"), List.of());

        assertThat(warnMessages()).as("宿主未使用该能力属正常情况，不得 WARN").isEmpty();
        assertThat(appender.list)
            .filteredOn(event -> event.getLevel() == Level.INFO)
            .anySatisfy(event -> assertThat(event.getFormattedMessage())
                .contains("未发现任何 BaseEnum 实现"));
    }
}

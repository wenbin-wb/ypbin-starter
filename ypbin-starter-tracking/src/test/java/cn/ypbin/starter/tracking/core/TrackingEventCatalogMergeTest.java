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
package cn.ypbin.starter.tracking.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.ypbin.starter.tracking.core.TrackingCatalogMerger.MergeResult;
import cn.ypbin.starter.tracking.core.TrackingEventCatalog.EventSchema;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 事件目录两层合并测试：无宿主目录时与改动前完全一致；有宿主目录时新增/覆盖/保留三条语义齐备，
 * 且覆盖必定打印逐字段差异；多份宿主目录、损坏宿主文件、不支持的 schemaVersion、事件的重复码都直接失败。
 *
 * @author wenbin
 * @since 2026-09-16
 */
class TrackingEventCatalogMergeTest {

    /** 单测夹具目录（刻意不放在 META-INF/ypbin 下，避免被类路径扫描当成真实宿主目录） */
    private static final String FIXTURE_DIR = "catalogs/";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ListAppender<ILoggingEvent> appender;
    private Logger mergerLogger;
    private Logger loaderLogger;

    @BeforeEach
    void attachLogAppender() {
        appender = new ListAppender<>();
        appender.start();
        mergerLogger = (Logger) LoggerFactory.getLogger(TrackingCatalogMerger.class);
        loaderLogger = (Logger) LoggerFactory.getLogger(TrackingCatalogLoader.class);
        mergerLogger.addAppender(appender);
        loaderLogger.addAppender(appender);
    }

    @AfterEach
    void detachLogAppender() {
        mergerLogger.detachAppender(appender);
        loaderLogger.detachAppender(appender);
    }

    @Test
    void shouldKeepPureBaseWhenHostCatalogAbsent() {
        TrackingEventCatalog catalog = new TrackingEventCatalog(objectMapper);

        assertThat(catalog.codes()).containsExactlyInAnyOrderElementsOf(TrackingEventCodes.ALL);
        assertThat(catalog.overriddenCodes()).isEmpty();
        // 无宿主目录时不产生任何新日志：与改动前的可观测行为一致
        assertThat(appender.list).isEmpty();
    }

    @Test
    void shouldMergeHostCatalogOverBase() {
        MergeResult merged = TrackingCatalogLoader.classifyAndMerge(objectMapper,
            List.of(baseCatalog(), projectCatalog("project-merge.json")));

        // 新增事件 + base 独有事件保留
        assertThat(merged.schemas().keySet()).contains("admin.report.download", "admin.cache.clear",
            TrackingEventCodes.UI_CLICK_ACTION);
        TrackingEventCatalog pureBase = new TrackingEventCatalog(objectMapper);
        assertThat(merged.schemas().get(TrackingEventCodes.UI_CLICK_ACTION))
            .isEqualTo(pureBase.schema(TrackingEventCodes.UI_CLICK_ACTION));

        // 覆盖以 project 为准
        assertThat(merged.overriddenCodes()).containsExactlyInAnyOrder("ui.page.view", "api.request.end");
        EventSchema view = merged.schemas().get(TrackingEventCodes.UI_PAGE_VIEW);
        assertThat(view.description()).isEqualTo("页面浏览（项目自定义说明）");
        assertThat(view.properties()).containsOnlyKeys("routeKey", "deviceId");
        assertThat(view.properties().get("routeKey").maxLength()).isEqualTo(256);
        assertThat(merged.schemas().get("admin.cache.clear").properties()).isEmpty();
        assertThat(merged.schemas().get("admin.report.download").properties().get("rowCount").maxLength()).isZero();

        // 覆盖必须打印差异：description / 属性新增 / 属性删除 / type 与 maxLength 变化
        assertThat(warnMessages()).anySatisfy(message -> assertThat(message)
            .contains("ui.page.view")
            .contains("description: \"页面浏览：路由进入后上报一次\" -> \"页面浏览（项目自定义说明）\"")
            .contains("properties.added: [deviceId]")
            .contains("properties.removed: [routeTitle]")
            .contains("properties.routeKey: maxLength 128 -> 256"));
        assertThat(warnMessages()).anySatisfy(message -> assertThat(message)
            .contains("api.request.end")
            .contains("properties.bizCode: type integer -> string, maxLength 0 -> 32"));
    }

    @Test
    void shouldNotReportDiffWhenProjectSchemaIdenticalToBase() {
        MergeResult merged = TrackingCatalogLoader.classifyAndMerge(objectMapper,
            List.of(baseCatalog(), projectCatalog("project-identical.json")));

        assertThat(merged.schemas()).containsKey(TrackingEventCodes.UI_PAGE_VIEW);
        assertThat(merged.overrides()).isEmpty();
        assertThat(merged.overriddenCodes()).isEmpty();
        assertThat(warnMessages()).isEmpty();
    }

    @Test
    void shouldRejectMultipleProjectCatalogs() {
        assertThatThrownBy(() -> TrackingCatalogLoader.classifyAndMerge(objectMapper,
            List.of(baseCatalog(), projectCatalog("project-merge.json"), projectCatalog("project-second.json"))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("multiple project tracking event catalogs");
    }

    @Test
    void shouldRejectWhenBaseCatalogAbsent() {
        assertThatThrownBy(() -> TrackingCatalogLoader.classifyAndMerge(objectMapper,
            List.of(projectCatalog("project-merge.json"))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("tracking event catalog resource not found")
            .hasMessageContaining("export-tracking-events.mjs");
    }

    @Test
    void shouldRejectBrokenProjectCatalog() {
        assertThatThrownBy(() -> TrackingCatalogLoader.classifyAndMerge(objectMapper,
            List.of(baseCatalog(), projectCatalog("project-broken.json"))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("failed to parse tracking event catalog")
            .hasMessageContaining("project-broken.json")
            .hasRootCauseInstanceOf(JacksonException.class);
    }

    @Test
    void shouldRejectUnsupportedSchemaVersion() {
        assertThatThrownBy(() -> TrackingCatalogLoader.classifyAndMerge(objectMapper,
            List.of(baseCatalog(), projectCatalog("project-bad-version.json"))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("unsupported tracking catalog schemaVersion")
            .hasMessageContaining("99");
    }

    @Test
    void shouldRejectDuplicatedEventCodeInsideOneCatalog() {
        assertThatThrownBy(() -> TrackingCatalogLoader.classifyAndMerge(objectMapper,
            List.of(baseCatalog(), projectCatalog("project-duplicate.json"))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("duplicated tracking event code: admin.dup.event");
    }

    @Test
    void shouldTolerateDuplicatedBaseResourceInSameArchive() {
        MergeResult merged = TrackingCatalogLoader.classifyAndMerge(objectMapper,
            List.of(baseCatalog(), baseCatalog()));

        assertThat(merged.schemas().keySet()).containsExactlyInAnyOrderElementsOf(TrackingEventCodes.ALL);
        assertThat(merged.overriddenCodes()).isEmpty();
        assertThat(warnMessages()).anySatisfy(message -> assertThat(message)
            .contains("duplicated tracking event catalog resource in starter archive"));
    }

    @Test
    void shouldMergeHostCatalogFoundOnRealClasspath(@TempDir Path tempDir) throws IOException {
        // 端到端：把宿主目录放进「另一个类路径位置」（临时目录 = 模拟宿主 jar），走真实的
        // findAll（classpath*: 全量枚举） + 归档归属判定，验证两份同名资源确实都被读到并合并
        Path hostCatalog = tempDir.resolve(TrackingEventCatalog.RESOURCE_PATH);
        Files.createDirectories(hostCatalog.getParent());
        try (InputStream source = projectCatalog("project-merge.json").getInputStream()) {
            Files.copy(source, hostCatalog);
        }
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader hostLoader =
                 new URLClassLoader(new URL[] {tempDir.toUri().toURL()}, TrackingEventCatalogMergeTest.class.getClassLoader())) {
            Thread.currentThread().setContextClassLoader(hostLoader);
            try {
                TrackingEventCatalog catalog = new TrackingEventCatalog(objectMapper);

                assertThat(catalog.overriddenCodes()).containsExactlyInAnyOrder("ui.page.view", "api.request.end");
                assertThat(catalog.codes()).contains("admin.report.download", TrackingEventCodes.UI_CLICK_ACTION);
                assertThat(catalog.schema(TrackingEventCodes.UI_PAGE_VIEW).properties())
                    .containsOnlyKeys("routeKey", "deviceId");
                assertThat(warnMessages()).isNotEmpty();
            } finally {
                Thread.currentThread().setContextClassLoader(previous);
            }
        }
    }

    private static Resource baseCatalog() {
        return new ClassPathResource(TrackingEventCatalog.RESOURCE_PATH);
    }

    private static Resource projectCatalog(String fileName) {
        return new ClassPathResource(FIXTURE_DIR + fileName);
    }

    private List<String> warnMessages() {
        return appender.list.stream()
            .filter(event -> event.getLevel() == Level.WARN)
            .map(ILoggingEvent::getFormattedMessage)
            .toList();
    }
}

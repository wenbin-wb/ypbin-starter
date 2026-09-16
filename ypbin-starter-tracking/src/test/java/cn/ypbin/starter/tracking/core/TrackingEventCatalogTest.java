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

import cn.ypbin.starter.tracking.core.TrackingEventCatalog.EventSchema;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * 事件目录注册表测试：目录来自运行时资源，必须与生成的常量集合一致且能回答白名单查询。
 *
 * @author wenbin
 * @since 2026-09-15
 */
class TrackingEventCatalogTest {

    private final TrackingEventCatalog catalog = new TrackingEventCatalog(new ObjectMapper());

    @Test
    void shouldLoadAllRegisteredCodesFromResource() {
        assertThat(catalog.codes()).hasSize(TrackingEventCodes.ALL.size());
        assertThat(catalog.codes()).containsExactlyInAnyOrderElementsOf(TrackingEventCodes.ALL);
    }

    @Test
    void shouldExposePropertyWhitelist() {
        EventSchema schema = catalog.schema(TrackingEventCodes.UI_PAGE_VIEW);

        assertThat(schema).isNotNull();
        assertThat(schema.description()).isEqualTo("页面浏览：路由进入后上报一次");
        assertThat(schema.properties()).containsOnlyKeys("routeKey", "routeTitle");
        assertThat(schema.properties().get("routeKey").type()).isEqualTo("string");
        assertThat(schema.properties().get("routeKey").maxLength()).isEqualTo(128);
    }

    @Test
    void shouldTreatUnknownCodeAsUnregistered() {
        assertThat(catalog.isRegistered("ui.page.unknown")).isFalse();
        assertThat(catalog.schema("ui.page.unknown")).isNull();
        assertThat(catalog.isRegistered(TrackingEventCodes.WEB_ERROR_JS)).isTrue();
    }
}

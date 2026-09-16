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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * 事件目录与运行时资源一致性测试。
 *
 * <p>脚本门禁（{@code node tools/export-tracking-events.mjs --check}）校验的是「生成物与事实源一致」，
 * 本测试校验的是另一件事：<strong>运行时资源确实被打包到约定路径、且与生成的常量集合一致</strong>——
 * 资源路径写错或漏打包时，脚本门禁不会报错，只有运行期才发现。</p>
 *
 * @author wenbin
 * @since 2026-09-15
 */
class TrackingEventCodesTest {

    private static final String RESOURCE_PATH = "/META-INF/ypbin/tracking-events.json";

    private static final Pattern CODE_PATTERN = Pattern.compile("\"code\"\\s*:\\s*\"([^\"]+)\"");

    @Test
    void shouldExposeAllRegisteredCodes() {
        assertThat(TrackingEventCodes.ALL).isNotEmpty().hasSize(TrackingEventCodes.DESCRIPTIONS.size());
        assertThat(TrackingEventCodes.ALL).contains("ui.page.view", "web.error.js", "auth.user.login");
    }

    @Test
    void shouldKeepConstantsInSyncWithResource() throws IOException {
        List<String> resourceCodes = readResourceCodes();

        assertThat(resourceCodes).isNotEmpty();
        assertThat(TrackingEventCodes.ALL).containsExactlyInAnyOrderElementsOf(resourceCodes);
    }

    private List<String> readResourceCodes() throws IOException {
        try (InputStream stream = TrackingEventCodesTest.class.getResourceAsStream(RESOURCE_PATH)) {
            assertThat(stream).as("运行时资源必须存在于 %s", RESOURCE_PATH).isNotNull();
            String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            List<String> codes = new ArrayList<>();
            Matcher matcher = CODE_PATTERN.matcher(content);
            while (matcher.find()) {
                codes.add(matcher.group(1));
            }
            return codes;
        }
    }
}

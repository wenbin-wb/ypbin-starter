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
package cn.ypbin.starter.messaging.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

/**
 * WebSocket 来源校验配置用例。
 *
 * @author wenbin
 * @since 2026-11-06
 */
class WebSocketStompConfigurerTest {

    private static WebSocketStompConfigurer configurer(WebSocketProperties properties) {
        return new WebSocketStompConfigurer(properties, mock(TaskScheduler.class));
    }

    @Test
    @DisplayName("默认仅同源：不配置来源模式时不放开任何跨域来源")
    void defaultsToSameOriginOnly() {
        assertThat(configurer(new WebSocketProperties()).originPatterns()).isEmpty();
    }

    @Test
    @DisplayName("逗号分隔的来源模式去空白、丢空项")
    void parsesCommaSeparatedPatterns() {
        WebSocketProperties properties = new WebSocketProperties();
        properties.setAllowedOriginPatterns(" https://a.example.com , ,https://*.example.com ");

        assertThat(configurer(properties).originPatterns())
            .containsExactly("https://a.example.com", "https://*.example.com");
    }
}

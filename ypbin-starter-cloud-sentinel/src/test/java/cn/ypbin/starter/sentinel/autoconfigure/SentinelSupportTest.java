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
package cn.ypbin.starter.sentinel.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.sentinel.handler.RBlockExceptionHandler;
import org.junit.jupiter.api.Test;

/**
 * Sentinel 配置与装配测试。
 *
 * @author wenbin
 * @since 2026-08-31
 */
class SentinelSupportTest {

    @Test
    void propertiesShouldExposeDefaults() {
        SentinelProperties props = new SentinelProperties();
        assertThat(props.isEnabled()).isTrue();
        assertThat(props.getBlockMessage()).isNotBlank();
        props.setEnabled(false);
        assertThat(props.isEnabled()).isFalse();
    }

    @Test
    void autoConfigurationShouldBuildHandler() {
        SentinelAutoConfiguration config = new SentinelAutoConfiguration();
        SentinelProperties props = new SentinelProperties();
        assertThat(config.blockExceptionHandler(
            new com.fasterxml.jackson.databind.ObjectMapper(), props))
            .isInstanceOf(RBlockExceptionHandler.class);
    }
}

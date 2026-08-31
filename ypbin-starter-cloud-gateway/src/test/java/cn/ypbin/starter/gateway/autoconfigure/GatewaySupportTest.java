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
package cn.ypbin.starter.gateway.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.gateway.auth.GatewayAuthResult;
import org.junit.jupiter.api.Test;

/**
 * 网关认证结果与配置测试。
 *
 * @author wenbin
 * @since 2026-08-31
 */
class GatewaySupportTest {

    @Test
    void authResultShouldCarryState() {
        GatewayAuthResult success = GatewayAuthResult.success();
        assertThat(success.isAuthenticated()).isTrue();
        GatewayAuthResult failure = GatewayAuthResult.failure("拒绝");
        assertThat(failure.isAuthenticated()).isFalse();
        assertThat(failure.getMessage()).isEqualTo("拒绝");
        GatewayAuthResult withHeaders = GatewayAuthResult.success(java.util.Map.of("X-User", "1"));
        assertThat(withHeaders.isAuthenticated()).isTrue();
    }

    @Test
    void propertiesShouldExposeDefaults() {
        GatewayProperties props = new GatewayProperties();
        assertThat(props.isEnabled()).isTrue();
        assertThat(props.getRequestIdHeader()).isEqualTo("X-Request-Id");
        assertThat(props.getAuth()).isNotNull();
        assertThat(props.getCors()).isNotNull();
        assertThat(props.getHeaderSanitize()).isNotNull();
    }
}

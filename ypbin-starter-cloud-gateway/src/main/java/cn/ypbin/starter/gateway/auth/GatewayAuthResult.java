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
package cn.ypbin.starter.gateway.auth;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 网关认证结果。
 *
 * <p>认证成功时可携带由可信认证器签发的身份请求头，供网关写入下游请求。客户端原始身份头会先被
 * {@code HeaderSanitizeGlobalFilter} 清理，避免伪造身份信息。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class GatewayAuthResult {

    private final boolean authenticated;

    private final String message;

    private final Map<String, String> trustedHeaders;

    private GatewayAuthResult(boolean authenticated, String message, Map<String, String> trustedHeaders) {
        this.authenticated = authenticated;
        this.message = message;
        this.trustedHeaders = trustedHeaders;
    }

    public static GatewayAuthResult success() {
        return success(Collections.emptyMap());
    }

    public static GatewayAuthResult success(Map<String, String> trustedHeaders) {
        return new GatewayAuthResult(true, null, new LinkedHashMap<>(trustedHeaders));
    }

    public static GatewayAuthResult failure(String message) {
        return new GatewayAuthResult(false, message, Collections.emptyMap());
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, String> getTrustedHeaders() {
        return trustedHeaders;
    }
}

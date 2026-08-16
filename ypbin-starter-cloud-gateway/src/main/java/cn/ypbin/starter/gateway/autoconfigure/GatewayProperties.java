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

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关配置项。
 *
 * @author wenbin
 * @since 2026-07-30
 */
@ConfigurationProperties(prefix = GatewayProperties.PREFIX)
public class GatewayProperties {

    public static final String PREFIX = "ypbin.gateway";

    /** 是否启用网关增强，默认开启 */
    private boolean enabled = true;

    /** 请求 ID 请求头名称 */
    private String requestIdHeader = "X-Request-Id";

    /** 跨域配置 */
    private Cors cors = new Cors();

    /** 入口身份头清洗配置 */
    private HeaderSanitize headerSanitize = new HeaderSanitize();

    /** 统一认证配置 */
    private Auth auth = new Auth();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRequestIdHeader() {
        return requestIdHeader;
    }

    public void setRequestIdHeader(String requestIdHeader) {
        this.requestIdHeader = requestIdHeader;
    }

    public Cors getCors() {
        return cors;
    }

    public void setCors(Cors cors) {
        this.cors = cors;
    }

    public HeaderSanitize getHeaderSanitize() {
        return headerSanitize;
    }

    public void setHeaderSanitize(HeaderSanitize headerSanitize) {
        this.headerSanitize = headerSanitize;
    }

    public Auth getAuth() {
        return auth;
    }

    public void setAuth(Auth auth) {
        this.auth = auth;
    }

    /**
     * WebFlux 跨域配置。
     */
    public static class Cors {

        /** 是否启用跨域，默认关闭 */
        private boolean enabled = false;

        /** 允许的来源模式 */
        private List<String> allowedOriginPatterns = new ArrayList<>(List.of("*"));

        /** 允许的请求方法 */
        private List<String> allowedMethods = new ArrayList<>(List.of("*"));

        /** 允许的请求头 */
        private List<String> allowedHeaders = new ArrayList<>(List.of("*"));

        /** 暴露给浏览器的响应头 */
        private List<String> exposedHeaders = new ArrayList<>(List.of("X-Request-Id"));

        /** 是否允许携带凭证 */
        private boolean allowCredentials = true;

        /** 预检请求缓存时间（秒） */
        private long maxAge = 3600L;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getAllowedOriginPatterns() {
            return allowedOriginPatterns;
        }

        public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
            this.allowedOriginPatterns = allowedOriginPatterns;
        }

        public List<String> getAllowedMethods() {
            return allowedMethods;
        }

        public void setAllowedMethods(List<String> allowedMethods) {
            this.allowedMethods = allowedMethods;
        }

        public List<String> getAllowedHeaders() {
            return allowedHeaders;
        }

        public void setAllowedHeaders(List<String> allowedHeaders) {
            this.allowedHeaders = allowedHeaders;
        }

        public List<String> getExposedHeaders() {
            return exposedHeaders;
        }

        public void setExposedHeaders(List<String> exposedHeaders) {
            this.exposedHeaders = exposedHeaders;
        }

        public boolean isAllowCredentials() {
            return allowCredentials;
        }

        public void setAllowCredentials(boolean allowCredentials) {
            this.allowCredentials = allowCredentials;
        }

        public long getMaxAge() {
            return maxAge;
        }

        public void setMaxAge(long maxAge) {
            this.maxAge = maxAge;
        }
    }

    /**
     * 入口身份头清洗配置。
     */
    public static class HeaderSanitize {

        /** 是否清洗客户端传入的身份类请求头，默认开启 */
        private boolean enabled = true;

        /** 客户端不可直接传入、需由可信网关签发的请求头 */
        private List<String> headers = new ArrayList<>(List.of(
            "X-User-Id", "X-User-Name", "X-Tenant-Id", "X-Dept-Id", "X-Roles"));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getHeaders() {
            return headers;
        }

        public void setHeaders(List<String> headers) {
            this.headers = headers;
        }
    }

    /**
     * 统一认证配置。
     */
    public static class Auth {

        /** 是否启用统一认证，默认关闭；需同时提供 GatewayAuthProvider Bean */
        private boolean enabled = false;

        /** 放行路径 */
        private List<String> excludePaths = new ArrayList<>(List.of(
            "/actuator/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getExcludePaths() {
            return excludePaths;
        }

        public void setExcludePaths(List<String> excludePaths) {
            this.excludePaths = excludePaths;
        }
    }
}

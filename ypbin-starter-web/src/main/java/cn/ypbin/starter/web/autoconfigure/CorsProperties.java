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
package cn.ypbin.starter.web.autoconfigure;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS 跨域配置项。
 *
 * <p>默认关闭。生产环境应显式配置 {@link #allowedOriginPatterns} 白名单，
 * 避免使用通配 {@code *} 造成安全隐患。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@ConfigurationProperties(prefix = CorsProperties.PREFIX)
public class CorsProperties {

    public static final String PREFIX = "ypbin.web.cors";

    /** 是否启用跨域，默认关闭 */
    private boolean enabled = false;

    /** 允许的来源模式（支持 {@code https://*.example.com} 形式） */
    private List<String> allowedOriginPatterns = List.of("*");

    /** 允许的请求方法 */
    private List<String> allowedMethods = List.of("*");

    /** 允许的请求头 */
    private List<String> allowedHeaders = List.of("*");

    /** 暴露给浏览器的响应头 */
    private List<String> exposedHeaders = List.of();

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

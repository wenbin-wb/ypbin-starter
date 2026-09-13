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

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * API 版本管理配置项（Spring 7 原生 {@code @RequestMapping(version=...)} 支持）。
 *
 * <p><strong>默认关闭</strong>：开启后 Spring MVC 会按版本条件匹配请求，未声明 {@code version}
 * 的既有映射仍可正常访问（由 {@link #defaultVersion} 决定无版本请求的归属），但为避免影响既有路由
 * 行为，需业务方显式开启。</p>
 *
 * @author wenbin
 * @since 2026-09-13
 */
@ConfigurationProperties(prefix = ApiVersionProperties.PREFIX)
public class ApiVersionProperties {

    public static final String PREFIX = "ypbin.web.api-version";

    /** 是否启用 API 版本管理，默认关闭 */
    private boolean enabled = false;

    /** 版本解析方式：HEADER（默认）/ QUERY_PARAM / PATH_SEGMENT */
    private Resolver resolver = Resolver.HEADER;

    /** HEADER 方式下的请求头名 */
    private String headerName = "X-Api-Version";

    /** QUERY_PARAM 方式下的查询参数名 */
    private String queryParam = "version";

    /** PATH_SEGMENT 方式下的路径段下标（从 1 开始，1 表示首段） */
    private int pathSegmentIndex = 1;

    /** 是否要求请求必须携带版本；false 时缺省使用 {@link #defaultVersion} */
    private boolean versionRequired = false;

    /** 缺省版本：请求未携带版本时使用；为空表示无缺省版本（仅匹配未声明版本的映射） */
    private String defaultVersion = "1.0";

    /** 显式支持的版本清单，为空表示自动探测（由已注册映射的 version 声明推导） */
    private List<String> supportedVersions = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Resolver getResolver() {
        return resolver;
    }

    public void setResolver(Resolver resolver) {
        this.resolver = resolver;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public String getQueryParam() {
        return queryParam;
    }

    public void setQueryParam(String queryParam) {
        this.queryParam = queryParam;
    }

    public int getPathSegmentIndex() {
        return pathSegmentIndex;
    }

    public void setPathSegmentIndex(int pathSegmentIndex) {
        this.pathSegmentIndex = pathSegmentIndex;
    }

    public boolean isVersionRequired() {
        return versionRequired;
    }

    public void setVersionRequired(boolean versionRequired) {
        this.versionRequired = versionRequired;
    }

    public String getDefaultVersion() {
        return defaultVersion;
    }

    public void setDefaultVersion(String defaultVersion) {
        this.defaultVersion = defaultVersion;
    }

    public List<String> getSupportedVersions() {
        return supportedVersions;
    }

    public void setSupportedVersions(List<String> supportedVersions) {
        this.supportedVersions = supportedVersions;
    }

    /**
     * 版本解析方式。
     */
    public enum Resolver {

        /** 从请求头解析（推荐，语义清晰且不污染 URL） */
        HEADER,

        /** 从查询参数解析 */
        QUERY_PARAM,

        /** 从路径段解析 */
        PATH_SEGMENT
    }
}

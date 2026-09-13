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
package cn.ypbin.starter.loadbalancer.core;

import cn.ypbin.starter.loadbalancer.autoconfigure.LoadBalancerProperties;
import java.util.List;
import org.springframework.cloud.client.loadbalancer.DefaultRequestContext;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.RequestData;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

/**
 * 请求灰度版本解析器。
 *
 * <p>按配置的头名顺序读取灰度版本；配置了 {@code ypbin.cloud.loadbalancer.allowed-versions}
 * 白名单时，仅采纳白名单内的值——请求头由客户端可控，不限制则可被用于把流量导向任意灰度实例。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
public class VersionRequestContextResolver {

    private final LoadBalancerProperties properties;

    public VersionRequestContextResolver(LoadBalancerProperties properties) {
        this.properties = properties;
    }

    public String resolve(Request<?> request) {
        HttpHeaders headers = resolveHeaders(request);
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        for (String headerName : properties.getVersionHeaders()) {
            String value = headers.getFirst(headerName);
            if (isAcceptable(value)) {
                return value.trim();
            }
        }
        return null;
    }

    /**
     * 判断请求头中的版本值是否可被采纳：非空且（未配置白名单或命中白名单）。
     *
     * @param value 请求头版本值
     * @return 可采纳返回 true
     */
    private boolean isAcceptable(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        List<String> allowed = properties.getAllowedVersions();
        if (allowed == null || allowed.isEmpty()) {
            return true;
        }
        String trimmed = value.trim();
        return allowed.stream().anyMatch(trimmed::equals);
    }

    private HttpHeaders resolveHeaders(Request<?> request) {
        Object context = request == null ? null : request.getContext();
        if (context instanceof DefaultRequestContext defaultRequestContext) {
            Object clientRequest = defaultRequestContext.getClientRequest();
            if (clientRequest instanceof RequestData requestData) {
                return requestData.getHeaders();
            }
        }
        if (context instanceof RequestData requestData) {
            return requestData.getHeaders();
        }
        return null;
    }
}

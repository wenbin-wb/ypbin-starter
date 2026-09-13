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

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.loadbalancer.autoconfigure.LoadBalancerProperties;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.loadbalancer.DefaultRequest;
import org.springframework.cloud.client.loadbalancer.DefaultRequestContext;
import org.springframework.cloud.client.loadbalancer.RequestData;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

/**
 * {@link VersionRequestContextResolver} 单元测试。
 *
 * @author wenbin
 * @since 2026-07-31
 */
class VersionRequestContextResolverTest {

    @Test
    void shouldResolveFirstNonBlankVersionHeader() {
        LoadBalancerProperties properties = new LoadBalancerProperties();
        VersionRequestContextResolver resolver = new VersionRequestContextResolver(properties);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Version", " ");
        headers.add("version", "gray");
        RequestData requestData = new RequestData(
            HttpMethod.GET, URI.create("http://demo/test"), headers, null, Map.of());
        DefaultRequest<DefaultRequestContext> request = new DefaultRequest<>(new DefaultRequestContext(requestData));

        String version = resolver.resolve(request);

        assertThat(version).isEqualTo("gray");
    }

    @Test
    void shouldReturnNullWhenHeaderMissing() {
        LoadBalancerProperties properties = new LoadBalancerProperties();
        VersionRequestContextResolver resolver = new VersionRequestContextResolver(properties);
        RequestData requestData = new RequestData(
            HttpMethod.GET, URI.create("http://demo/test"), new HttpHeaders(), null, Map.of());
        DefaultRequest<DefaultRequestContext> request = new DefaultRequest<>(new DefaultRequestContext(requestData));

        String version = resolver.resolve(request);

        assertThat(version).isNull();
    }

    @Test
    void shouldRejectVersionOutsideAllowList() {
        LoadBalancerProperties properties = new LoadBalancerProperties();
        properties.setAllowedVersions(List.of("gray-v1"));
        VersionRequestContextResolver resolver = new VersionRequestContextResolver(properties);

        // 未在白名单内的版本头由客户端伪造，须忽略并按正式实例路由
        assertThat(resolver.resolve(requestWithVersion("internal-canary"))).isNull();
    }

    @Test
    void shouldAcceptVersionInsideAllowList() {
        LoadBalancerProperties properties = new LoadBalancerProperties();
        properties.setAllowedVersions(List.of("gray-v1"));
        VersionRequestContextResolver resolver = new VersionRequestContextResolver(properties);

        assertThat(resolver.resolve(requestWithVersion("gray-v1"))).isEqualTo("gray-v1");
    }

    @Test
    void shouldSkipRejectedHeaderAndTryNextHeader() {
        LoadBalancerProperties properties = new LoadBalancerProperties();
        properties.setAllowedVersions(List.of("gray-v1"));
        VersionRequestContextResolver resolver = new VersionRequestContextResolver(properties);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Version", "forged");
        headers.add("version", "gray-v1");

        assertThat(resolver.resolve(requestWithHeaders(headers))).isEqualTo("gray-v1");
    }

    private static DefaultRequest<DefaultRequestContext> requestWithVersion(String version) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Version", version);
        return requestWithHeaders(headers);
    }

    private static DefaultRequest<DefaultRequestContext> requestWithHeaders(HttpHeaders headers) {
        RequestData requestData = new RequestData(
            HttpMethod.GET, URI.create("http://demo/test"), headers, null, Map.of());
        return new DefaultRequest<>(new DefaultRequestContext(requestData));
    }
}

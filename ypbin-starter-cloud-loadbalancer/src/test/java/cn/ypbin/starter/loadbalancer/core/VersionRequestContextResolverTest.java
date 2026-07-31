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
}

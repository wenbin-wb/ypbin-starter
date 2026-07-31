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
package cn.ypbin.starter.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

/**
 * {@link HeaderSanitizeGlobalFilter} 单元测试。
 *
 * @author wenbin
 * @since 2026-07-31
 */
class HeaderSanitizeGlobalFilterTest {

    @Test
    void shouldRemoveConfiguredHeaders() {
        HeaderSanitizeGlobalFilter filter = new HeaderSanitizeGlobalFilter(
            List.of("X-User-Id", "X-Tenant-Id"));
        MockServerHttpRequest request = MockServerHttpRequest.get("/test")
            .header("X-User-Id", "malicious-id")
            .header("X-Tenant-Id", "evil-tenant")
            .header("Authorization", "Bearer token")
            .build();
        var exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain -> {
            var headers = chain.getRequest().getHeaders();
            assertThat(headers.getFirst("X-User-Id")).isNull();
            assertThat(headers.getFirst("X-Tenant-Id")).isNull();
            assertThat(headers.getFirst("Authorization")).isEqualTo("Bearer token");
            return reactor.core.publisher.Mono.empty();
        }).block();
    }

    @Test
    void shouldHaveOrderAfterRequestIdFilter() {
        HeaderSanitizeGlobalFilter filter = new HeaderSanitizeGlobalFilter(List.of());

        assertThat(filter.getOrder()).isEqualTo(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 1);
    }
}

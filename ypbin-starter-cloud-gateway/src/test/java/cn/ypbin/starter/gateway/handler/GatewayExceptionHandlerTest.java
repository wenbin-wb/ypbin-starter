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
package cn.ypbin.starter.gateway.handler;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.core.exception.GlobalErrorCode;
import cn.ypbin.starter.core.model.R;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@link GatewayExceptionHandler} 单元测试。
 *
 * @author wenbin
 * @since 2026-07-31
 */
class GatewayExceptionHandlerTest {

    private GatewayExceptionHandler handler;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        handler = new GatewayExceptionHandler(objectMapper);
    }

    @Test
    void shouldResolveNotFoundFor404() {
        R<Void> result = handler.resolveBody(new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertThat(result.getCode()).isEqualTo(GlobalErrorCode.NOT_FOUND.getCode());
    }

    @Test
    void shouldResolveUnauthorizedFor401() {
        R<Void> result = handler.resolveBody(new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        assertThat(result.getCode()).isEqualTo(GlobalErrorCode.UNAUTHORIZED.getCode());
    }

    @Test
    void shouldResolveInternalErrorForUnknown() {
        R<Void> result = handler.resolveBody(new RuntimeException("bomb"));

        assertThat(result.getCode()).isEqualTo(GlobalErrorCode.INTERNAL_ERROR.getCode());
    }

    @Test
    void shouldReturnHttp200ForNotFound() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/not-found"));
        handler.handle(exchange, new ResponseStatusException(HttpStatus.NOT_FOUND)).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}

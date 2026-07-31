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
package cn.ypbin.starter.sentinel.handler;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.core.exception.GlobalErrorCode;
import cn.ypbin.starter.core.model.R;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * {@link RBlockExceptionHandler} 单元测试。
 *
 * @author wenbin
 * @since 2026-07-31
 */
class RBlockExceptionHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldWriteUnifiedRJsonOnBlock() throws Exception {
        RBlockExceptionHandler handler = new RBlockExceptionHandler(objectMapper, "请求过于频繁，请稍后重试");
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, "GET:/api/demo", new FlowException("blocked"));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).startsWith("application/json");
        R<?> body = objectMapper.readValue(response.getContentAsString(), R.class);
        assertThat(body.getCode()).isEqualTo(GlobalErrorCode.TOO_MANY_REQUESTS.getCode());
        assertThat(body.isSuccess()).isFalse();
        assertThat(body.getMessage()).isEqualTo("请求过于频繁，请稍后重试");
    }

    @Test
    void shouldUseCustomBlockMessage() throws Exception {
        RBlockExceptionHandler handler = new RBlockExceptionHandler(objectMapper, "系统繁忙");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(new MockHttpServletRequest(), response, "GET:/api/x", new FlowException("blocked"));

        R<?> body = objectMapper.readValue(response.getContentAsString(), R.class);
        assertThat(body.getMessage()).isEqualTo("系统繁忙");
    }
}

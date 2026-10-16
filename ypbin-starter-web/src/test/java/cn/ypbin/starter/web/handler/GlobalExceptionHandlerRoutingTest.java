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
package cn.ypbin.starter.web.handler;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 客户端请求异常的路由验证。
 *
 * <p>{@link GlobalExceptionHandlerTest} 直接调用 handler 方法，只能证明方法本身返回业务码（前三类 400、
 * 上传超限 413）；
 * 本类经 MockMvc 走真实异常解析链，证明这 4 类异常被 {@code @ExceptionHandler} 命中，
 * <strong>不会</strong>跌入 {@code Exception.class} 兜底分支（ERROR 日志 + 业务码 500）。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
class GlobalExceptionHandlerRoutingTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    /** JSON 语法错误 → HttpMessageNotReadableException。 */
    @Test
    void malformedJsonBodyShouldReturnBadRequestEnvelope() throws Exception {
        mockMvc.perform(post("/test/body")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.success").value(false));
    }

    /** 路径参数类型转换失败 → MethodArgumentTypeMismatchException。 */
    @Test
    void parameterTypeMismatchShouldReturnBadRequestEnvelope() throws Exception {
        mockMvc.perform(get("/test/orders/not-a-number"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400));
    }

    /** 必填查询参数缺失 → MissingServletRequestParameterException。 */
    @Test
    void missingRequiredParameterShouldReturnBadRequestEnvelope() throws Exception {
        mockMvc.perform(get("/test/search"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400));
    }

    /** 上传超限 → MaxUploadSizeExceededException（413，与 Spring 7 的 getStatusCode 口径一致）。 */
    @Test
    void maxUploadSizeExceededShouldReturnPayloadTooLargeEnvelope() throws Exception {
        mockMvc.perform(get("/test/upload"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(413));
    }

    /**
     * 测试用控制器：分别触发 4 类客户端异常。
     */
    @RestController
    private static class TestController {

        @PostMapping("/test/body")
        public String body(@RequestBody Map<String, Object> payload) {
            return String.valueOf(payload);
        }

        @GetMapping("/test/orders/{id}")
        public String order(@PathVariable Long id) {
            return String.valueOf(id);
        }

        @GetMapping("/test/search")
        public String search(@RequestParam String keyword) {
            return keyword;
        }

        @GetMapping("/test/upload")
        public String upload() {
            throw new MaxUploadSizeExceededException(1024L);
        }
    }
}

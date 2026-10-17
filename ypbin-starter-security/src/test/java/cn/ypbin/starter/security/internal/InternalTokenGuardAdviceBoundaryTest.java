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
package cn.ypbin.starter.security.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.exception.GlobalErrorCode;
import cn.ypbin.starter.core.model.R;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 架构约束回归测试：<b>ENVELOPE 语义只能由 {@code HandlerInterceptor} 承担，不能由 {@code Filter} 承担</b>。
 *
 * <p>本组件的两种拒绝语义必须由两种组件承载，这不是风格选择而是容器语义决定的：</p>
 * <ul>
 *     <li>{@code @RestControllerAdvice} <b>捕获不到 Filter 抛出的异常</b>——Filter 位于
 *     {@code DispatcherServlet} 之外，异常会原样外逸（本测试实证）；</li>
 *     <li>因此若有人把 ENVELOPE 的守卫"简化"成 Filter，失败将<b>不再是</b>
 *     「HTTP 200 + {@code R.code=401}」，而是 500 / 容器错误页——这是静默破坏对外契约；</li>
 *     <li>RAW_401 则相反：它要求<b>不进入异常处理链</b>、直接写状态码，所以必须是 Filter。</li>
 * </ul>
 *
 * <p>用探针（而非本组件自身）来钉这条约束：约束成立与否只取决于 Spring MVC 的行为，
 * 与本组件的实现细节无关。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
class InternalTokenGuardAdviceBoundaryTest {

    private static MockMvc mockMvcWith(java.util.function.Consumer<org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder> customizer) {
        org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder builder =
            MockMvcBuilders.standaloneSetup(new ProbeController()).setControllerAdvice(new ProbeAdvice());
        customizer.accept(builder);
        return builder.build();
    }

    @Test
    @DisplayName("★ Filter 抛业务异常不被 @RestControllerAdvice 捕获（故 ENVELOPE 不能用 Filter）")
    void filterExceptionEscapesControllerAdvice() {
        MockMvc mockMvc = mockMvcWith(builder -> builder.addFilters(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                FilterChain filterChain) {
                throw new BusinessException(GlobalErrorCode.UNAUTHORIZED, "filter-rejected");
            }
        }));

        assertThatThrownBy(() -> mockMvc.perform(MockMvcRequestBuilders.get("/internal/x")))
            .as("Filter 在 DispatcherServlet 之外，其异常必须外逸：不会被 advice 转成 R.code=401")
            .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("★ HandlerInterceptor 抛业务异常被 advice 转 HTTP 200 + R.code=401（故 ENVELOPE 必须用拦截器）")
    void interceptorExceptionBecomesEnvelope() throws Exception {
        MockMvc mockMvc = mockMvcWith(builder -> builder.addInterceptors(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                Object handler) {
                throw new BusinessException(GlobalErrorCode.UNAUTHORIZED, "interceptor-rejected");
            }
        }));

        MockHttpServletResponse response =
            mockMvc.perform(MockMvcRequestBuilders.get("/internal/x")).andReturn().getResponse();

        assertThat(response.getStatus())
            .as("信封语义：HTTP 状态码必须是 200，业务错误由 R.code 表达")
            .isEqualTo(200);
        assertThat(response.getContentAsString())
            .contains("\"code\":401")
            .as("探针 advice 会回显策略自身的 message：证明请求确实被 advice 处理（而非容器兜底）")
            .contains("interceptor-rejected");
    }

    @RestController
    static class ProbeController {

        @GetMapping("/internal/x")
        public R<String> get() {
            return R.ok("ok");
        }
    }

    @RestControllerAdvice
    static class ProbeAdvice {

        @ExceptionHandler(BusinessException.class)
        public R<Void> handle(BusinessException e) {
            return R.fail(e.getCode(), e.getMessage());
        }
    }
}

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

import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.model.R;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

/**
 * ENVELOPE 语义的<b>端到端装配测试</b>：真实请求分派 + 真实拦截器注册 + 真实 Spring MVC 路径匹配。
 *
 * <p>单元测试单独调用 {@code preHandle} 无法证明"路径范围真的生效"——通配匹配由 Spring MVC 在请求
 * 分派时求值。本测试的做法是：<b>注册</b>仍由被测组件
 * {@link InternalTokenGuardWebMvcConfigurer} 完成（不复制其逻辑，避免假绿），随后把注册结果交给
 * Spring MVC 的 {@code MappedInterceptor} 做真实匹配。锁定：</p>
 * <ul>
 *     <li>目标路径（{@code /internal/**}）无凭证/凭证错误 ⇒ HTTP 200 + {@code R.code=401}；
 *     带正确凭证 ⇒ 放行到控制器；</li>
 *     <li>非目标路径（{@code /api/devices}、{@code /internalish}）<b>无凭证也必须放行</b>——
 *     守卫不得误伤全站；</li>
 *     <li>响应体不回显任何输入值。</li>
 * </ul>
 *
 * @author wenbin
 * @since 2026-10-16
 */
class InternalTokenGuardMockMvcTest {

    private static final String SECRET = "s3cr3t-internal-token";
    private static final String HEADER = "X-Internal-Token";

    private static MockMvc mockMvc(List<String> envelopePatterns) {
        return MockMvcBuilders.standaloneSetup(new ProbeController())
            .setControllerAdvice(new ProbeAdvice())
            .addInterceptors(registeredInterceptors(envelopePatterns))
            .build();
    }

    /**
     * 让被测组件在真实 {@link InterceptorRegistry} 上完成注册，并取回注册结果
     * （{@code MappedInterceptor} 携带组件写入的路径模式）。
     *
     * <p>{@code InterceptorRegistry#getInterceptors()} 是 {@code protected}，故用子类暴露；
     * 这样"注册了什么"完全来自被测组件，测试不复制其路径逻辑。</p>
     *
     * @param envelopePatterns ENVELOPE 生效路径
     * @return 已注册的拦截器（含路径模式）
     */
    private static HandlerInterceptor[] registeredInterceptors(List<String> envelopePatterns) {
        InternalTokenProperties properties = new InternalTokenProperties();
        properties.setGuardEnabled(true);
        properties.setToken(SECRET);
        properties.setEnvelopePathPatterns(envelopePatterns);
        properties.setRaw401PathPatterns(List.of());

        CapturingRegistry registry = new CapturingRegistry();
        new InternalTokenGuardWebMvcConfigurer(properties).addInterceptors(registry);
        return registry.captured().toArray(HandlerInterceptor[]::new);
    }

    /** 暴露 {@link InterceptorRegistry} 注册结果的测试子类。 */
    static class CapturingRegistry extends InterceptorRegistry {

        /**
         * 取回注册结果。
         *
         * @return 已注册拦截器（含 {@code MappedInterceptor} 携带的路径模式）
         */
        List<Object> captured() {
            return new ArrayList<>(getInterceptors());
        }
    }

    @Test
    @DisplayName("目标路径 /internal/**：无凭证 ⇒ 200 + R.code=401（信封语义）")
    void protectedPathWithoutTokenIsRejectedWithEnvelope() throws Exception {
        MvcResult result = mockMvc(List.of("/internal/**"))
            .perform(MockMvcRequestBuilders.get("/internal/secret"))
            .andReturn();

        assertThat(result.getResponse().getStatus())
            .as("信封语义：HTTP 200 而非 401，业务错误由 R.code 表达")
            .isEqualTo(200);
        assertThat(result.getResponse().getContentAsString()).contains("\"code\":401");
        assertThat(result.getResponse().getContentAsString())
            .as("未放行到控制器")
            .doesNotContain("controller-reached");
    }

    @Test
    @DisplayName("目标路径 /internal/**：凭证错误 ⇒ 200 + R.code=401，且响应不含输入值")
    void protectedPathWithWrongTokenIsRejectedWithoutEcho() throws Exception {
        String malicious = "evil-\"><script>alert(1)</script>";
        MvcResult result = mockMvc(List.of("/internal/**"))
            .perform(MockMvcRequestBuilders.get("/internal/secret").header(HEADER, malicious))
            .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body).contains("\"code\":401");
        assertThat(body).doesNotContain("evil-").doesNotContain("<script>").doesNotContain(SECRET);
    }

    @Test
    @DisplayName("目标路径 /internal/**：凭证正确 ⇒ 放行到控制器")
    void protectedPathWithCorrectTokenReachesController() throws Exception {
        MvcResult result = mockMvc(List.of("/internal/**"))
            .perform(MockMvcRequestBuilders.get("/internal/secret").header(HEADER, SECRET))
            .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString()).contains("controller-reached");
    }

    @Test
    @DisplayName("★ 非目标路径不受影响：/api/devices 与 /internalish 无凭证也必须放行")
    void nonTargetPathsAreUnaffected() throws Exception {
        MockMvc mockMvc = mockMvc(List.of("/internal/**"));

        for (String path : List.of("/api/devices", "/internalish")) {
            MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get(path)).andReturn();
            assertThat(result.getResponse().getStatus())
                .as("非目标路径 %s 不得被守卫拦截", path)
                .isEqualTo(200);
            assertThat(result.getResponse().getContentAsString())
                .as("非目标路径 %s 必须真正到达控制器", path)
                .contains("controller-reached");
        }
    }

    @Test
    @DisplayName("自定义生效路径：只拦配置的路径，其余放行")
    void customPatternsScopeTheGuard() throws Exception {
        MockMvc mockMvc = mockMvc(List.of("/ops/**"));

        MvcResult guarded = mockMvc.perform(MockMvcRequestBuilders.get("/ops/secret")).andReturn();
        assertThat(guarded.getResponse().getContentAsString())
            .as("配置的 /ops/** 必须被拦")
            .contains("\"code\":401");

        MvcResult notGuarded =
            mockMvc.perform(MockMvcRequestBuilders.get("/internal/secret")).andReturn();
        assertThat(notGuarded.getResponse().getContentAsString())
            .as("未纳入生效路径的请求不得被判为凭证失败")
            .doesNotContain("\"code\":401")
            .contains("controller-reached");
    }

    @RestController
    static class ProbeController {

        @GetMapping("/internal/secret")
        public R<String> secret() {
            return R.ok("controller-reached");
        }

        @GetMapping("/ops/secret")
        public R<String> opsSecret() {
            return R.ok("controller-reached");
        }

        @GetMapping("/api/devices")
        public R<String> devices() {
            return R.ok("controller-reached");
        }

        @GetMapping("/internalish")
        public R<String> internalish() {
            return R.ok("controller-reached");
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

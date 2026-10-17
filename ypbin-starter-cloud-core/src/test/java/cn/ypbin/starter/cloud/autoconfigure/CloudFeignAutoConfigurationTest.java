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
package cn.ypbin.starter.cloud.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.cloud.feign.FeignHeaderInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * {@link CloudFeignAutoConfiguration} 装配条件测试（GitHub issue #96 的独立验收补漏）。
 *
 * <p>缺陷形态：请求头透传拦截器 Bean 曾被 {@code @ConditionalOnWebApplication(SERVLET)} 限制在
 * Servlet Web 应用内，⇒ {@code spring.main.web-application-type=none} 的纯后台/定时任务应用即使配置了
 * {@code ypbin.cloud.feign.internal-token} 也不装配该拦截器，Feign 调用不带内部凭证，下游一旦开启
 * 内部端点守卫（fail-closed）即 401。</p>
 *
 * <p>{@link ApplicationContextRunner} 默认创建<b>非 Web</b> 上下文（无 {@code WebApplicationContext}，
 * 即 {@code spring.main.web-application-type=none} 的等价装配形态），
 * {@link WebApplicationContextRunner} 则创建带 mock {@code ServletContext} 的 Servlet Web 上下文。</p>
 *
 * @author wenbin
 * @since 2026-10-17
 */
class CloudFeignAutoConfigurationTest {

    /** 内部凭证测试值：一次性假值，仅用于断言注入行为，不对任何真实服务生效。 */
    private static final String INTERNAL_TOKEN = "internal-token-8f3a1c";

    private static final String INTERNAL_HEADER = FeignHeaderInterceptor.DEFAULT_INTERNAL_TOKEN_HEADER;

    private final ApplicationContextRunner nonWebRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CloudFeignAutoConfiguration.class));

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    /**
     * ① 非 Web 上下文（无 Web 相关上下文）：拦截器 Bean 必须存在，且内部凭证真的被注入。
     */
    @Test
    void nonWebApplicationShouldStillInjectInternalToken() {
        nonWebRunner
            .withPropertyValues("ypbin.cloud.feign.internal-token=" + INTERNAL_TOKEN)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getSourceApplicationContext())
                    .as("前置条件：本上下文确实是『非 Web 应用』形态，而非被 @ConditionalOnWebApplication 放行")
                    .isNotInstanceOf(WebApplicationContext.class);
                assertThat(context).hasSingleBean(FeignHeaderInterceptor.class);

                RequestTemplate template = new RequestTemplate();
                context.getBean(FeignHeaderInterceptor.class).apply(template);

                assertThat(template.headers().get(INTERNAL_HEADER))
                    .as("非 Web 应用（定时任务/后台线程）也必须携带内部调用凭证，否则下游守卫会 401")
                    .containsExactly(INTERNAL_TOKEN);
            });
    }

    /**
     * ② 无请求上下文时，身份头自然跳过（不得凭空造出身份，也不得抛异常），但凭证照常注入。
     */
    @Test
    void identityHeadersShouldNotPropagateWithoutRequestContext() {
        nonWebRunner
            .withPropertyValues(
                "ypbin.cloud.feign.internal-token=" + INTERNAL_TOKEN,
                "ypbin.cloud.feign.propagate-headers[0]=X-User-Id",
                "ypbin.cloud.feign.identity-headers[0]=X-User-Id",
                "ypbin.cloud.feign.trusted-source-token=s3cret")
            .run(context -> {
                assertThat(context).hasSingleBean(FeignHeaderInterceptor.class);

                RequestTemplate template = new RequestTemplate();
                context.getBean(FeignHeaderInterceptor.class).apply(template);

                assertThat(template.headers())
                    .as("无请求上下文时身份头必须自然跳过")
                    .doesNotContainKey("X-User-Id");
                assertThat(template.headers().get(INTERNAL_HEADER)).containsExactly(INTERNAL_TOKEN);
            });
    }

    /**
     * ③ 既有 Servlet Web 应用行为不变：Bean 照常装配，入站请求头照常透传（含凭证）。
     */
    @Test
    void servletWebApplicationShouldStillPropagateRequestHeaders() {
        new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CloudFeignAutoConfiguration.class))
            .withPropertyValues("ypbin.cloud.feign.internal-token=" + INTERNAL_TOKEN)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getSourceApplicationContext())
                    .as("前置条件：本上下文是 Servlet Web 上下文")
                    .isInstanceOf(WebApplicationContext.class);
                assertThat(context).hasSingleBean(FeignHeaderInterceptor.class);

                MockHttpServletRequest request = new MockHttpServletRequest();
                request.addHeader("X-Request-Id", "req-1");
                RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

                RequestTemplate template = new RequestTemplate();
                context.getBean(FeignHeaderInterceptor.class).apply(template);

                assertThat(template.headers().get("X-Request-Id")).containsExactly("req-1");
                assertThat(template.headers().get(INTERNAL_HEADER)).containsExactly(INTERNAL_TOKEN);
            });
    }
}

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

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 内部调用凭证守卫自动配置。
 *
 * <p>宿主显式开启 {@code ypbin.security.internal-token.guard-enabled=true} 时装配两种拒绝语义
 * （同为 opt-in，与 {@link cn.ypbin.starter.security.management.ManagementEndpointGuardAutoConfiguration}
 * 同一约定，不破坏未配置宿主）：</p>
 * <ul>
 *     <li>{@link InternalTokenGuardWebMvcConfigurer}（{@code WebMvcConfigurer} +
 *     {@link InternalTokenGuardInterceptor}）→ <b>ENVELOPE</b>：抛业务异常由全局处理器转
 *     HTTP 200 + {@code R.code=401}；</li>
 *     <li>{@link InternalTokenGuard}（{@link FilterRegistrationBean}）→ <b>RAW_401</b>：直接写原始
 *     401，供 EMQX HTTP 动作等只看状态码的对端使用。</li>
 * </ul>
 *
 * <p><strong>为什么不共用 @ConditionalOnMissingBean 写法</strong>：{@code WebMvcConfigurer} 的
 * 返回类型是本组件自己的具体类，按类型判定等价于"宿主显式覆盖本类"；而
 * {@link FilterRegistrationBean} 是全仓通用容器类型，按类型判定会命中宿主自己的任意过滤器注册 ⇒
 * 本守卫被静默挤掉。因此后者按 <b>Bean 名</b> 判定（与 {@code TrackingIngestAutoConfiguration}
 * 同一处理）。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = InternalTokenProperties.PREFIX, name = "guard-enabled",
    havingValue = "true", matchIfMissing = false)
// Servlet 专属：WebFlux 应用（网关）既无 HandlerInterceptor 语义也不需要该入站凭证守卫
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(InternalTokenProperties.class)
public class InternalTokenGuardAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(InternalTokenGuardAutoConfiguration.class);

    /** RAW_401 过滤器注册的 Bean 名（按名判定可覆盖，避免按类型命中宿主其它注册 Bean） */
    static final String RAW_401_REGISTRATION_BEAN_NAME = "internalTokenGuardRaw401FilterRegistration";

    /** RAW_401 过滤器自身的注册名（便于排查日志与注册表定位） */
    private static final String RAW_401_FILTER_NAME = "internalTokenGuardRaw401Filter";

    /**
     * ENVELOPE 语义：把凭证拦截器挂到配置的路径（默认 {@code /internal/**}）。
     *
     * @param properties 守卫配置
     * @return 拦截器注册配置
     */
    @Bean
    @ConditionalOnClass({HandlerInterceptor.class, WebMvcConfigurer.class})
    @ConditionalOnMissingBean
    public InternalTokenGuardWebMvcConfigurer internalTokenGuardWebMvcConfigurer(
            InternalTokenProperties properties) {
        logEnabledPaths(properties);
        return new InternalTokenGuardWebMvcConfigurer(properties);
    }

    /**
     * RAW_401 语义：把凭证过滤器挂到配置的路径（默认空，即不启用）。
     *
     * <p><strong>空清单必须显式 {@code setEnabled(false)}</strong>：Boot 的
     * {@code AbstractFilterRegistrationBean} 在既无 servlet 名也无 URL 模式时会把过滤器映射到
     * {@code /*}（{@code DEFAULT_URL_MAPPINGS}）——那会让守卫拦下应用的全部请求（可用性事故）。
     * {@code RegistrationBean#onStartup} 在 {@code isEnabled()==false} 时直接返回，可确保不注册。</p>
     *
     * @param properties 守卫配置
     * @return 过滤器注册
     */
    @Bean
    @ConditionalOnClass({OncePerRequestFilter.class, HandlerInterceptor.class})
    @ConditionalOnMissingBean(name = RAW_401_REGISTRATION_BEAN_NAME)
    public FilterRegistrationBean<InternalTokenGuard> internalTokenGuardRaw401FilterRegistration(
            InternalTokenProperties properties) {
        List<String> patterns = properties.getRaw401PathPatterns().stream()
            .filter(StringUtils::hasText)
            .toList();
        FilterRegistrationBean<InternalTokenGuard> registration = new FilterRegistrationBean<>(
            new InternalTokenGuard(properties.getToken(), properties.getHeaderName()));
        registration.setName(RAW_401_FILTER_NAME);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        if (patterns.isEmpty()) {
            // 未配置 RAW_401 路径：显式停用，避免被 Boot 兜底映射到 /* 而拦下全应用
            registration.setEnabled(false);
            return registration;
        }
        registration.addUrlPatterns(patterns.toArray(String[]::new));
        log.info("[ypbin-starter] 内部调用凭证守卫 RAW_401 语义已启用（对端仅看 HTTP 状态码，如 EMQX）：paths={}",
            patterns);
        return registration;
    }

    /**
     * 打印 ENVELOPE 语义的启用范围。
     *
     * <p>两种语义都未配置任何路径时给出显式告警：守卫"已开启但不拦任何请求"属静默失效形态，
     * 必须让接入方在启动日志里看得见。</p>
     *
     * @param properties 守卫配置
     */
    private static void logEnabledPaths(InternalTokenProperties properties) {
        List<String> envelope = properties.getEnvelopePathPatterns().stream()
            .filter(StringUtils::hasText)
            .toList();
        List<String> raw401 = properties.getRaw401PathPatterns().stream()
            .filter(StringUtils::hasText)
            .toList();
        if (envelope.isEmpty() && raw401.isEmpty()) {
            log.warn("[ypbin-starter] {} 已开启，但两种拒绝语义的路径清单都为空 ⇒ 本守卫不拦截任何请求"
                    + "（请配置 {}.envelope-path-patterns 或 raw401-path-patterns）",
                InternalTokenProperties.PREFIX + ".guard-enabled", InternalTokenProperties.PREFIX);
            return;
        }
        if (!envelope.isEmpty()) {
            log.info("[ypbin-starter] 内部调用凭证守卫 ENVELOPE 语义已启用（拒绝为 HTTP 200 + R.code=401）：paths={}",
                envelope);
        }
    }
}

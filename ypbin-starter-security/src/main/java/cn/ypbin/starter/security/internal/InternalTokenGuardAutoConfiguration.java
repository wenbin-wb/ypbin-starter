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

import cn.ypbin.starter.security.satoken.SecurityExcludePathProvider;
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
 * <p><strong>两条额外的静默失效防线</strong>：</p>
 * <ul>
 *     <li>RAW_401 路径按 Servlet 规范（12.2）归一化，Ant 风格 {@code /xxx/**} 会被容器当作字面量
 *     匹配而完全失效，故统一归一化为 {@code /xxx/*}；无法安全解释的通配写法启动即失败
 *     （见 {@link InternalTokenGuardPatterns}）；</li>
 *     <li>守卫接管的路径通过 {@link SecurityExcludePathProvider} 从 Sa-Token 全局登录拦截中豁免，
 *     否则服务间 Feign 直连（只有 {@code X-Internal-Token}）在守卫执行前就抛
 *     {@code NotLoginException}，凭证校验形同虚设（见 {@link InternalTokenGuardExcludePathProvider}）。</li>
 * </ul>
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
     * Sa-Token 放行清单贡献者的 Bean 名。
     *
     * <p>必须按名判定：返回类型 {@link SecurityExcludePathProvider} 是仓内通用贡献接口，
     * 按类型判定会被其它模块（如 SSE）的同类型 Bean 静默挤掉。</p>
     */
    static final String EXCLUDE_PATH_PROVIDER_BEAN_NAME = "internalTokenGuardExcludePathProvider";

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
        List<String> configured = properties.getRaw401PathPatterns().stream()
            .filter(StringUtils::hasText)
            .toList();
        FilterRegistrationBean<InternalTokenGuard> registration = new FilterRegistrationBean<>(
            new InternalTokenGuard(properties.getToken(), properties.getHeaderName()));
        registration.setName(RAW_401_FILTER_NAME);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        if (configured.isEmpty()) {
            // 未配置 RAW_401 路径：显式停用，避免被 Boot 兜底映射到 /* 而拦下全应用
            registration.setEnabled(false);
            return registration;
        }
        // Servlet 规范只认 '/*' 与 '*.ext'：Ant 风格 '/xxx/**' 会被容器当字面量匹配而静默失效，
        // 这里统一归一化；无法安全解释的写法启动即失败（见 InternalTokenGuardPatterns）
        List<String> patterns = InternalTokenGuardPatterns.toServletPatterns(configured);
        if (!patterns.equals(configured)) {
            log.info("[ypbin-starter] 内部调用凭证守卫 RAW_401 路径已按 Servlet 规范归一化：{} -> {}",
                configured, patterns);
        }
        registration.addUrlPatterns(patterns.toArray(String[]::new));
        log.info("[ypbin-starter] 内部调用凭证守卫 RAW_401 语义已启用（对端仅看 HTTP 状态码，如 EMQX）：paths={}",
            patterns);
        return registration;
    }

    /**
     * 把守卫接管的路径贡献给 Sa-Token 登录拦截放行清单（{@link SecurityExcludePathProvider}）。
     *
     * <p><strong>为什么必须有</strong>：ENVELOPE 语义是 MVC 拦截器，若 Sa-Token 仍按 {@code /**}
     * 要求登录态，服务间 Feign 直连（只有内部凭证、没有用户会话）会在守卫执行前就抛
     * {@code NotLoginException}，内部凭证校验永远不会生效——守卫"配了却拦不住"的另一种静默失效。</p>
     *
     * <p><strong>按 Bean 名判定不可省</strong>：本 Bean 的返回类型 {@link SecurityExcludePathProvider}
     * 是仓内通用贡献接口，{@code SecuritySseAutoConfiguration} 等也会注册同类型 Bean；用
     * {@code @ConditionalOnMissingBean} 按类型判定会被它们中的任意一个静默挤掉（与 RAW_401
     * 过滤器注册同一陷阱）。</p>
     *
     * @param properties 守卫配置
     * @return 放行路径贡献者
     */
    @Bean
    @ConditionalOnMissingBean(name = EXCLUDE_PATH_PROVIDER_BEAN_NAME)
    public SecurityExcludePathProvider internalTokenGuardExcludePathProvider(InternalTokenProperties properties) {
        InternalTokenGuardExcludePathProvider provider = new InternalTokenGuardExcludePathProvider(properties);
        log.info("[ypbin-starter] 内部调用凭证守卫接管路径已从 Sa-Token 登录拦截中豁免"
            + "（安全性由守卫的凭证校验接管）：paths={}", provider.excludePaths());
        return provider;
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
        if (!StringUtils.hasText(properties.getToken())) {
            // 关键配置漏配的**启动期**提示：否则接入方只在第一个内部请求被拒时才发现
            // （fail-closed 拒绝是对的，但"配了开关没配凭证"应在启动日志里一眼可见）
            log.warn("[ypbin-starter] {} 已开启但未配置凭证（{}.token 为空）：受保护路径将一律拒绝"
                    + "（fail-closed）。请为各服务配置同一凭证值并经环境变量注入。",
                InternalTokenProperties.PREFIX + ".guard-enabled", InternalTokenProperties.PREFIX);
        }
        List<String> envelope = properties.getEnvelopePathPatterns().stream()
            .filter(StringUtils::hasText)
            // trim 口径与 InternalTokenGuardWebMvcConfigurer / ExcludePathProvider 保持一致
            .map(String::trim)
            .toList();
        List<String> raw401 = properties.getRaw401PathPatterns().stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
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

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
package cn.ypbin.starter.security.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

/**
 * 微服务下游身份头自动配置。
 *
 * <p>为业务服务装配 {@link IdentityHeaderFilter}：解析网关签发的内部身份头构建
 * {@link IdentityContext}。<strong>默认关闭</strong>：仅当服务位于可信网关之后、且网关负责
 * 清洗外部 {@code X-User-Id/X-Roles} 等头并签发内部身份头时，宿主显式开启
 * {@code ypbin.security.identity.enabled=true} 才装配本过滤器。</p>
 *
 * <p><strong>来源校验强制项（SF-5，fail-closed）</strong>：开启身份模式必须同步配置
 * {@code ypbin.security.identity.trusted-source-token}（与网关
 * {@code ypbin.gateway.auth.trusted-source-token} 一致的随机串）。未配置时<strong>启动失败</strong>，
 * 不允许"身份头来源不可校验"的 fail-open 状态；配置后 {@link IdentityHeaderFilter} 对每个携带
 * 身份头的请求校验该标记，缺失或不匹配一律拒绝。</p>
 *
 * <p>身份头信任是安全敏感开关：缺省不装配可避免外部请求伪造 {@code X-User-Id} 等头直达业务服务
 * 时被当作已认证用户。装配与否的状态在启动日志中可见（见 {@code identityHeaderFilterRegistration}）。</p>
 *
 * @author wenbin
 * @since 2026-09-01
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = IdentityProperties.PREFIX, name = "enabled", havingValue = "true",
    // 安全默认：不显式开启即不信任外部身份头；仅位于可信网关之后且网关负责清洗/签发身份头时才显式开启
    matchIfMissing = false)
// Servlet 专属（IdentityHeaderFilter 是 Servlet Filter）：WebFlux 应用（网关）由
// SaTokenGatewayAuthProvider 完成鉴权与身份头签发，不装配本过滤器
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(IdentityProperties.class)
public class IdentityAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(IdentityAutoConfiguration.class);

    /**
     * 装配身份头信任过滤器。
     *
     * <p>开启身份模式但未配置 {@code trusted-source-token} 时抛
     * {@link IllegalStateException} 使应用启动失败（fail-closed）：身份模式把请求携带的身份头
     * 当作当前登录用户，若无法校验其来源，就等于把"伪造身份头"开放给任何能直连服务端口的人
     * （SF-5）。</p>
     *
     * @param properties   身份模式配置
     * @param objectMapper 拒绝响应序列化器（宿主 Web 应用由 spring-boot-jackson 提供）
     * @return 过滤器注册
     */
    @Bean
    public FilterRegistrationBean<IdentityHeaderFilter> identityHeaderFilterRegistration(
            IdentityProperties properties, ObjectMapper objectMapper) {
        if (!StringUtils.hasText(properties.getTrustedSourceToken())) {
            throw new IllegalStateException(
                "[ypbin-starter] ypbin.security.identity.enabled=true 必须同步配置 "
                    + "ypbin.security.identity.trusted-source-token（与网关 ypbin.gateway.auth.trusted-source-token "
                    + "配置一致的随机串），否则无法校验身份头来源，拒绝以 fail-open 状态启动");
        }
        FilterRegistrationBean<IdentityHeaderFilter> registration = new FilterRegistrationBean<>(
            new IdentityHeaderFilter(properties.getTrustedSourceHeader(),
                properties.getTrustedSourceToken(), objectMapper));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        // 条件装配日志：出现本行说明身份头信任过滤器已启用（未出现即默认停用，外部身份头不会被信任）
        log.info("[ypbin-starter] identity header trust filter ENABLED (ypbin.security.identity.enabled=true) — "
            + "校验来源标记头 {}；仅当服务位于可信网关之后且网关负责清洗外部 X-User-Id/X-Roles 头 "
            + "并签发内部身份头时方可开启", properties.getTrustedSourceHeader());
        return registration;
    }
}

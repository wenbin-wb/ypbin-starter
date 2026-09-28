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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 微服务下游身份头模式配置项（{@code ypbin.security.identity.*}）。
 *
 * <p><strong>安全前提（SF-5 修复后为强制项）</strong>：身份模式把「请求携带的身份头」当作当前登录用户，
 * 因此必须能校验这些头确实来自可信网关——由 {@link #trustedSourceToken}（与网关
 * {@code ypbin.gateway.auth.trusted-source-token} 一致的随机串）承载。
 * 未配置该值而开启 {@code enabled=true} 时 <strong>启动失败</strong>（fail-closed），
 * 杜绝「配了 token 才校验、不配就裸奔」的 fail-open 状态。</p>
 *
 * @author wenbin
 * @since 2026-09-28
 */
@ConfigurationProperties(prefix = IdentityProperties.PREFIX)
public class IdentityProperties {

    public static final String PREFIX = "ypbin.security.identity";

    /** 是否启用身份头模式（网关签发身份头、下游以身份头为登录态），默认关闭 */
    private boolean enabled = false;

    /**
     * 身份头来源标记头名（由可信网关在清洗外部身份头后签发）。
     *
     * <p>需与网关侧 {@code ypbin.gateway.auth.trusted-source-header} 一致；默认
     * {@value IdentityHeaders#GATEWAY_SIGNED}。</p>
     */
    private String trustedSourceHeader = IdentityHeaders.GATEWAY_SIGNED;

    /**
     * 身份头来源标记期望值。
     *
     * <p>必须与网关侧 {@code ypbin.gateway.auth.trusted-source-token} 配置为同一随机串；
     * 未配置时开启身份模式会导致启动失败（见类注释）。</p>
     */
    private String trustedSourceToken = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTrustedSourceHeader() {
        return trustedSourceHeader;
    }

    public void setTrustedSourceHeader(String trustedSourceHeader) {
        this.trustedSourceHeader = trustedSourceHeader;
    }

    public String getTrustedSourceToken() {
        return trustedSourceToken;
    }

    public void setTrustedSourceToken(String trustedSourceToken) {
        this.trustedSourceToken = trustedSourceToken;
    }
}

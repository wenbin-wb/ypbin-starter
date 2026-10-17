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

import cn.ypbin.starter.cloud.feign.FeignHeaderInterceptor;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Feign 增强配置项。
 *
 * <p><strong>内部调用凭证</strong>（{@link #internalToken}）：服务侧
 * {@code ypbin.security.internal-token.guard-enabled=true} 开启守卫后，调用其
 * {@code /internal/**} 端点必须携带 {@link #internalTokenHeader} 头（守卫侧 fail-closed，缺凭证一律拒绝）。
 * 本项是<b>真实凭据</b>，只由 {@code FeignHeaderInterceptor} 写进请求头，绝不进入日志、异常消息或
 * {@code toString}（本类不覆写 {@code toString}，默认实现不输出任何字段值）。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@ConfigurationProperties(prefix = FeignProperties.PREFIX)
public class FeignProperties {

    public static final String PREFIX = "ypbin.cloud.feign";

    /** 是否启用 Feign 增强（请求头透传等），默认开启 */
    private boolean enabled = true;

    /** 是否启用统一错误解码，默认开启 */
    private boolean errorDecoderEnabled = true;

    /**
     * 是否默认开启 OpenFeign circuitbreaker，默认开启。
     * 开启时同时注入 resilience4j 默认熔断/超时参数（见 {@code FeignDefaultsEnvironmentPostProcessor}：
     * TimeLimiter 10s、20 次滑动窗口、50% 失败率阈值等），业务可通过 resilience4j.* 配置整体或逐项覆盖。
     */
    private boolean circuitbreakerEnabled = true;

    /**
     * 需要透传到下游服务的请求头名单（大小写不敏感）。
     * 默认透传认证、链路追踪与身份头（身份头由可信网关统一清洗/签发，二次 RPC 时保证
     * 下游仍能识别调用者身份；若需自定义可在配置中覆盖）。
     */
    private List<String> propagateHeaders = new ArrayList<>(List.of(
        "Authorization", "X-Request-Id", "X-Trace-Id",
        "X-User-Id", "X-User-Name", "X-Tenant-Id", "X-Dept-Id", "X-Roles",
        // 网关身份头来源标记：必须透传，否则二次 RPC（如 auth→system）会因缺少标记
        // 被下游丢弃身份头，导致下游拿不到当前用户
        "X-Gateway-Signed"));

    /**
     * 身份类请求头名单（大小写不敏感）。这些头只应由可信网关签发，故当配置了
     * {@link #trustedSourceToken} 时，仅当入站请求携带匹配的 {@link #trustedSourceHeader}
     * 才会二次透传，避免直连服务伪造身份后经 Feign 调用放大越权。
     */
    private List<String> identityHeaders = new ArrayList<>(List.of(
        "X-User-Id", "X-User-Name", "X-Tenant-Id", "X-Dept-Id", "X-Roles"));

    /** 可信来源标记头名（由可信网关在清洗外部头后签发） */
    private String trustedSourceHeader = "X-Gateway-Signed";

    /**
     * 可信来源标记期望值；<strong>为空表示不启用来源校验</strong>（保持既有透传行为）。
     * 生产环境建议在网关与各下游服务统一配置同一随机串后启用。
     */
    private String trustedSourceToken = "";

    /**
     * 是否要求必须配置可信来源标记，默认 {@code false}（仅启动告警）。
     *
     * <p>置为 {@code true} 时，若未配置 {@link #trustedSourceToken} 则**启动失败**，
     * 用于强制新项目/生产环境开启身份头来源校验，避免忘记配置而长期处于「不校验」状态。</p>
     */
    private boolean requireTrustedSource = false;

    /** 内部调用凭证请求头名，默认 X-Internal-Token（必须与 security 守卫侧逐字一致，见常量注释）。 */
    private String internalTokenHeader = FeignHeaderInterceptor.DEFAULT_INTERNAL_TOKEN_HEADER;

    /** 内部调用凭证（密钥），默认空即不注入；真实凭据，绝不进入日志、异常消息或 toString。 */
    private String internalToken = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isErrorDecoderEnabled() {
        return errorDecoderEnabled;
    }

    public void setErrorDecoderEnabled(boolean errorDecoderEnabled) {
        this.errorDecoderEnabled = errorDecoderEnabled;
    }

    public boolean isCircuitbreakerEnabled() {
        return circuitbreakerEnabled;
    }

    public void setCircuitbreakerEnabled(boolean circuitbreakerEnabled) {
        this.circuitbreakerEnabled = circuitbreakerEnabled;
    }

    public List<String> getPropagateHeaders() {
        return propagateHeaders;
    }

    public void setPropagateHeaders(List<String> propagateHeaders) {
        this.propagateHeaders = propagateHeaders;
    }

    public List<String> getIdentityHeaders() {
        return identityHeaders;
    }

    public void setIdentityHeaders(List<String> identityHeaders) {
        this.identityHeaders = identityHeaders;
    }

    public String getTrustedSourceHeader() {
        return trustedSourceHeader;
    }

    public void setTrustedSourceHeader(String trustedSourceHeader) {
        this.trustedSourceHeader = trustedSourceHeader;
    }

    public boolean isRequireTrustedSource() {
        return requireTrustedSource;
    }

    public void setRequireTrustedSource(boolean requireTrustedSource) {
        this.requireTrustedSource = requireTrustedSource;
    }

    public String getTrustedSourceToken() {
        return trustedSourceToken;
    }

    public void setTrustedSourceToken(String trustedSourceToken) {
        this.trustedSourceToken = trustedSourceToken;
    }

    public String getInternalTokenHeader() {
        return internalTokenHeader;
    }

    public void setInternalTokenHeader(String internalTokenHeader) {
        this.internalTokenHeader = internalTokenHeader;
    }

    public String getInternalToken() {
        return internalToken;
    }

    public void setInternalToken(String internalToken) {
        this.internalToken = internalToken;
    }
}

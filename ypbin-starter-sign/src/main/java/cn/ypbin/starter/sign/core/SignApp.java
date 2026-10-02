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
package cn.ypbin.starter.sign.core;

import java.time.LocalDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 开放应用信息。
 *
 * <p>签名校验运行时使用的应用模型：{@link #accessKey} 为公开标识、{@link #secretKey} 为参与签名的私有密钥。
 * 由 {@link SignAppProvider} 提供，可来自配置文件或数据库。</p>
 *
 * <p><b>租户 / 作用域 / 配额（新增，全部可选）</b>：三个维度均为<b>可选</b>，缺省时保持
 * <b>旧行为</b>（不做该维度校验），因此既有接入方升级后不会因新增字段而集体被拒——
 * 这是刻意的向后兼容取舍，见各字段注释。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public class SignApp {

    /** Access Key（访问密钥，公开标识） */
    private String accessKey;

    /** Secret Key（私有密钥，参与签名） */
    private String secretKey;

    /** 应用名称（可选） */
    @Nullable
    private String appName;

    /** 失效时间，为空表示永不过期 */
    @Nullable
    private LocalDateTime expireTime;

    /** 是否启用 */
    private boolean enabled = true;

    /**
     * 所属租户 ID（可选）。
     *
     * <p>为空表示<b>不做租户校验</b>（保持旧行为）。非空时，{@link SignChecker} 会校验它
     * 与当前请求的租户上下文一致，用于防"跨租户用同一把 Key"。</p>
     */
    @Nullable
    private Long tenantId;

    /**
     * 作用域集合（可选）。
     *
     * <p>为空表示<b>不限制</b>（保持旧行为）。非空时由 {@link SignChecker} 交给调用方的
     * 作用域校验回调判定——starter 只负责"传出去"，<b>具体白名单语义属业务</b>（不同业务
     * 的权限码体系不同，starter 不预设）。</p>
     */
    private List<String> scopes = List.of();

    /**
     * 应用级 QPS 配额（可选）。
     *
     * <p>为空或 {@code <= 0} 表示<b>不限</b>。</p>
     */
    @Nullable
    private Integer rateLimitQps;

    /**
     * 应用级日调用配额（可选）。
     *
     * <p>为空或 {@code <= 0} 表示<b>不限</b>。</p>
     */
    @Nullable
    private Integer dailyQuota;

    /**
     * 来源 IP 白名单（CIDR 列表，逗号分隔；可选）。
     *
     * <p>为空表示<b>不限来源</b>（保持旧行为）。</p>
     */
    @Nullable
    private String ipWhitelist;

    /**
     * Secret Key 的哈希形态（可选，与 {@link #secretKey} <b>二选一</b>）。
     *
     * <p>用于"库内只存哈希"的场景（如 iot 的 OpenApiKey 体系）：{@link #secretKey} 为空、
     * 本字段非空时，{@link SignChecker} 以"调用方提供的校验回调"判定密钥是否匹配，
     * <b>starter 自身不做哈希算法假设</b>（各业务的 pepper / 算法可能不同）。</p>
     */
    @Nullable
    private String secretHash;

    /** 供框架反序列化/绑定使用的无参构造：字段随后由 setter 填充 */
    @SuppressWarnings("NullAway.Init")
    public SignApp() {
    }

    public SignApp(String accessKey, String secretKey) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
    }

    /**
     * 是否已过期。
     *
     * @return true 已过期
     */
    public boolean isExpired() {
        return expireTime != null && LocalDateTime.now().isAfter(expireTime);
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    @Nullable
    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    @Nullable
    public LocalDateTime getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(LocalDateTime expireTime) {
        this.expireTime = expireTime;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Nullable
    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public List<String> getScopes() {
        return scopes;
    }

    public void setScopes(List<String> scopes) {
        this.scopes = (scopes == null) ? List.of() : List.copyOf(scopes);
    }

    @Nullable
    public Integer getRateLimitQps() {
        return rateLimitQps;
    }

    public void setRateLimitQps(Integer rateLimitQps) {
        this.rateLimitQps = rateLimitQps;
    }

    @Nullable
    public Integer getDailyQuota() {
        return dailyQuota;
    }

    public void setDailyQuota(Integer dailyQuota) {
        this.dailyQuota = dailyQuota;
    }

    @Nullable
    public String getIpWhitelist() {
        return ipWhitelist;
    }

    public void setIpWhitelist(String ipWhitelist) {
        this.ipWhitelist = ipWhitelist;
    }

    @Nullable
    public String getSecretHash() {
        return secretHash;
    }

    public void setSecretHash(String secretHash) {
        this.secretHash = secretHash;
    }

    /**
     * 是否配置了 IP 白名单。
     *
     * @return 非空白白名单返回 {@code true}
     */
    public boolean hasIpWhitelist() {
        return ipWhitelist != null && !ipWhitelist.isBlank();
    }

    /**
     * 是否启用配额/限流判定。
     *
     * @return QPS 或日配额任一为正数返回 {@code true}
     */
    public boolean hasQuota() {
        return (rateLimitQps != null && rateLimitQps > 0) || (dailyQuota != null && dailyQuota > 0);
    }
}

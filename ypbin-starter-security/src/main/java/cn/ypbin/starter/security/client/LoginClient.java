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
package cn.ypbin.starter.security.client;

import cn.dev33.satoken.stp.parameter.enums.SaLogoutMode;
import cn.dev33.satoken.stp.parameter.enums.SaReplacedLoginExitMode;
import cn.dev33.satoken.stp.parameter.enums.SaReplacedRange;
import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 登录客户端配置。
 *
 * <p>用于描述后台、App、小程序、开放 API 等不同入口的登录策略。starter 只定义运行时模型与校验规则，
 * 配置来源由 {@link LoginClientProvider} 决定：可来自配置文件，也可由 admin 从数据库读取后提供。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public class LoginClient implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 客户端 ID，如 web-admin、app、miniapp */
    private String clientId;

    /** 客户端密钥，浏览器端可为空，服务端/开放平台场景可启用 */
    private String clientSecret;

    /** 客户端类型，如 WEB、APP、MINI、API */
    private String clientType = "WEB";

    /** 支持的认证方式，如 ACCOUNT、PHONE、EMAIL、SOCIAL */
    private Set<String> authTypes = new LinkedHashSet<>();

    /** Token 有效期（秒），为空时使用 Sa-Token 全局配置 */
    private Long timeout;

    /** Token 活跃超时（秒），为空时使用 Sa-Token 全局配置 */
    private Long activeTimeout;

    /** 是否允许同一账号多端同时登录，空则使用 Sa-Token 全局配置 */
    private Boolean concurrent;

    /** 多端登录时是否共享同一 token，空则使用 Sa-Token 全局配置 */
    private Boolean share;

    /** 同一账号最大登录数量，空则使用 Sa-Token 全局配置 */
    private Integer maxLoginCount;

    /** 顶人下线范围，空则使用 Sa-Token 全局配置 */
    private SaReplacedRange replacedRange;

    /** 并发关闭时新旧设备谁放弃会话，空则使用 Sa-Token 全局配置 */
    private SaReplacedLoginExitMode replacedLoginExitMode;

    /** 超出最大登录数量时的下线方式，空则使用 Sa-Token 全局配置 */
    private SaLogoutMode overflowLogoutMode;

    /** 是否持久 Cookie，空则使用 Sa-Token 全局配置 */
    private Boolean lastingCookie;

    /** 是否登录后写入响应头，空则使用 Sa-Token 全局配置 */
    private Boolean writeHeader;

    /** 是否启用 */
    private boolean enabled = true;

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getClientType() {
        return clientType;
    }

    public void setClientType(String clientType) {
        this.clientType = clientType;
    }

    public Set<String> getAuthTypes() {
        return authTypes;
    }

    public void setAuthTypes(Set<String> authTypes) {
        this.authTypes = authTypes;
    }

    public Long getTimeout() {
        return timeout;
    }

    public void setTimeout(Long timeout) {
        this.timeout = timeout;
    }

    public Long getActiveTimeout() {
        return activeTimeout;
    }

    public void setActiveTimeout(Long activeTimeout) {
        this.activeTimeout = activeTimeout;
    }

    public Boolean getConcurrent() {
        return concurrent;
    }

    public void setConcurrent(Boolean concurrent) {
        this.concurrent = concurrent;
    }

    public Boolean getShare() {
        return share;
    }

    public void setShare(Boolean share) {
        this.share = share;
    }

    public Integer getMaxLoginCount() {
        return maxLoginCount;
    }

    public void setMaxLoginCount(Integer maxLoginCount) {
        this.maxLoginCount = maxLoginCount;
    }

    public SaReplacedRange getReplacedRange() {
        return replacedRange;
    }

    public void setReplacedRange(SaReplacedRange replacedRange) {
        this.replacedRange = replacedRange;
    }

    public SaReplacedLoginExitMode getReplacedLoginExitMode() {
        return replacedLoginExitMode;
    }

    public void setReplacedLoginExitMode(SaReplacedLoginExitMode replacedLoginExitMode) {
        this.replacedLoginExitMode = replacedLoginExitMode;
    }

    public SaLogoutMode getOverflowLogoutMode() {
        return overflowLogoutMode;
    }

    public void setOverflowLogoutMode(SaLogoutMode overflowLogoutMode) {
        this.overflowLogoutMode = overflowLogoutMode;
    }

    public Boolean getLastingCookie() {
        return lastingCookie;
    }

    public void setLastingCookie(Boolean lastingCookie) {
        this.lastingCookie = lastingCookie;
    }

    public Boolean getWriteHeader() {
        return writeHeader;
    }

    public void setWriteHeader(Boolean writeHeader) {
        this.writeHeader = writeHeader;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}

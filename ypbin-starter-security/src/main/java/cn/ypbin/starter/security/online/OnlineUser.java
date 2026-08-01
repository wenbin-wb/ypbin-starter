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
package cn.ypbin.starter.security.online;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 在线用户信息。
 *
 * <p>基于 Sa-Token 会话枚举得到的一条在线登录记录：一个 token 对应一条（同一用户多端登录会有多条）。
 * 用户名/昵称等业务字段来自登录时写入会话的 {@link cn.ypbin.starter.security.core.LoginUser}，
 * 客户端/设备/登录时间等来自登录终端信息。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public class OnlineUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 租户 ID */
    private Long tenantId;

    /** 令牌值 */
    private String token;

    /** 客户端 ID */
    private String clientId;

    /** 客户端类型/设备类型 */
    private String deviceType;

    /** 登录 IP */
    private String ip;

    /** IP 归属地 */
    private String location;

    /** 浏览器 */
    private String browser;

    /** 操作系统 */
    private String os;

    /** 登录时间 */
    private LocalDateTime loginTime;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getBrowser() {
        return browser;
    }

    public void setBrowser(String browser) {
        this.browser = browser;
    }

    public String getOs() {
        return os;
    }

    public void setOs(String os) {
        this.os = os;
    }

    public LocalDateTime getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(LocalDateTime loginTime) {
        this.loginTime = loginTime;
    }
}

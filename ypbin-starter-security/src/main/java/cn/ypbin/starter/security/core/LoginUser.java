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
package cn.ypbin.starter.security.core;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

/**
 * 登录用户信息。
 *
 * <p>封装当前登录人的常用信息，登录成功后由业务方构造并通过 {@link UserContext#setLoginUser} 存入会话，
 * 之后任意层用 {@link UserContext#getLoginUser} 一次性取全，免去逐字段读取。</p>
 *
 * <p>starter 只约定通用字段；业务自有字段（如岗位、数据范围）用 {@link UserContext#setAttribute} 另存。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
public class LoginUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private Long id;

    /** 登录账号 */
    private String username;

    /** 昵称/显示名 */
    private String nickname;

    /** 租户 ID */
    private Long tenantId;

    /** 部门 ID */
    private Long deptId;

    /** 角色标识集合 */
    private Set<String> roles;

    /** 客户端 ID */
    private String clientId;

    /** 客户端类型 */
    private String clientType;

    /** 认证方式 */
    private String authType;

    public LoginUser() {
    }

    public LoginUser(Long id, String username) {
        this.id = id;
        this.username = username;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientType() {
        return clientType;
    }

    public void setClientType(String clientType) {
        this.clientType = clientType;
    }

    public String getAuthType() {
        return authType;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
    }
}

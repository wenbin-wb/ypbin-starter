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
package cn.ypbin.starter.security.autoconfigure;

import cn.ypbin.starter.security.client.LoginClient;
import cn.ypbin.starter.security.password.policy.PasswordPolicy;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 安全模块配置项。
 *
 * @author wenbin
 * @since 2026-07-30
 */
@ConfigurationProperties(prefix = SecurityProperties.PREFIX)
public class SecurityProperties {

    public static final String PREFIX = "ypbin.security";

    /** 是否启用安全模块，默认开启 */
    private boolean enabled = true;

    /** 是否注册全局登录校验拦截器（SaInterceptor），默认开启 */
    private boolean interceptor = true;

    /** 拦截路径，默认拦截全部 */
    private List<String> includes = new ArrayList<>(List.of("/**"));

    /** 放行路径（无需登录即可访问），支持 Ant 风格 */
    private List<String> excludes = new ArrayList<>();

    /** 检测到 api-doc 时是否自动放行 Swagger/文档相关路径，默认开启 */
    private boolean excludeApiDoc = true;

    /** 是否启用客户端校验，默认开启 */
    private boolean clientEnabled = true;

    /** 默认客户端 ID，登录请求未传 clientId 时使用 */
    private String defaultClientId = "web-admin";

    /** 配置文件客户端列表；业务方提供 LoginClientProvider 后可由数据库接管 */
    private List<LoginClient> clients = new ArrayList<>(List.of(defaultClient()));

    /** 密码安全策略；业务方提供 PasswordPolicyProvider 后可由配置中心/数据库接管 */
    private PasswordPolicy password = new PasswordPolicy();

    private static LoginClient defaultClient() {
        LoginClient client = new LoginClient();
        client.setClientId("web-admin");
        client.setClientType("WEB");
        client.setAuthTypes(new LinkedHashSet<>(Set.of("ACCOUNT", "PHONE", "EMAIL", "SOCIAL")));
        client.setEnabled(true);
        return client;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isInterceptor() {
        return interceptor;
    }

    public void setInterceptor(boolean interceptor) {
        this.interceptor = interceptor;
    }

    public List<String> getIncludes() {
        return includes;
    }

    public void setIncludes(List<String> includes) {
        this.includes = includes;
    }

    public List<String> getExcludes() {
        return excludes;
    }

    public void setExcludes(List<String> excludes) {
        this.excludes = excludes;
    }

    public boolean isExcludeApiDoc() {
        return excludeApiDoc;
    }

    public void setExcludeApiDoc(boolean excludeApiDoc) {
        this.excludeApiDoc = excludeApiDoc;
    }

    public boolean isClientEnabled() {
        return clientEnabled;
    }

    public void setClientEnabled(boolean clientEnabled) {
        this.clientEnabled = clientEnabled;
    }

    public String getDefaultClientId() {
        return defaultClientId;
    }

    public void setDefaultClientId(String defaultClientId) {
        this.defaultClientId = defaultClientId;
    }

    public List<LoginClient> getClients() {
        return clients;
    }

    public void setClients(List<LoginClient> clients) {
        this.clients = clients;
    }

    public PasswordPolicy getPassword() {
        return password;
    }

    public void setPassword(PasswordPolicy password) {
        this.password = password;
    }
}

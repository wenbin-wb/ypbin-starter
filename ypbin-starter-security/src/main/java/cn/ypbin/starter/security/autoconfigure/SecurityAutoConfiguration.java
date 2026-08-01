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

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpInterface;
import cn.ypbin.starter.security.client.DefaultLoginClientProvider;
import cn.ypbin.starter.security.client.DefaultLoginClientService;
import cn.ypbin.starter.security.client.LoginClientHolder;
import cn.ypbin.starter.security.client.LoginClientProvider;
import cn.ypbin.starter.security.client.LoginClientService;
import cn.ypbin.starter.security.core.PermissionProvider;
import cn.ypbin.starter.security.handler.SaTokenExceptionHandler;
import cn.ypbin.starter.security.online.DefaultOnlineUserService;
import cn.ypbin.starter.security.online.OnlineUserService;
import cn.ypbin.starter.security.password.lock.InMemoryPasswordAttemptStore;
import cn.ypbin.starter.security.password.lock.PasswordAttemptLimiter;
import cn.ypbin.starter.security.password.lock.PasswordAttemptStore;
import cn.ypbin.starter.security.password.lock.RedisPasswordAttemptStore;
import cn.ypbin.starter.security.password.policy.DefaultPasswordPolicyProvider;
import cn.ypbin.starter.security.password.policy.PasswordExpiration;
import cn.ypbin.starter.security.password.policy.PasswordPolicyProvider;
import cn.ypbin.starter.security.password.policy.PasswordValidator;
import cn.ypbin.starter.security.satoken.SaTokenWebConfigurer;
import cn.ypbin.starter.security.satoken.StpPermissionAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 安全模块自动配置。
 *
 * <p>装配权限数据源默认实现与 Sa-Token 适配器。当业务方未提供
 * {@link PermissionProvider} 时使用返回空权限的默认实现，保证零配置可启动；
 * 提供实现后即接管注解鉴权的数据来源。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@AutoConfiguration
@ConditionalOnClass(StpInterface.class)
@ConditionalOnProperty(prefix = "ypbin.security", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SecurityAutoConfiguration.class);

    /**
     * 默认权限数据源：返回空权限/角色。业务方提供自定义实现即可覆盖。
     */
    @Bean
    @ConditionalOnMissingBean
    public PermissionProvider permissionProvider() {
        log.warn("[ypbin-starter] 使用默认空权限数据源，注解鉴权将始终无权限。请提供 PermissionProvider 实现。");
        return new PermissionProvider() {
        };
    }

    /**
     * 默认客户端配置来源：读取 ypbin.security.clients。业务方提供自定义实现即可从数据库接管。
     */
    @Bean
    @ConditionalOnMissingBean
    public LoginClientProvider loginClientProvider(SecurityProperties properties) {
        return new DefaultLoginClientProvider(properties);
    }

    /**
     * 客户端登录运行时服务。
     */
    @Bean
    @ConditionalOnMissingBean
    public LoginClientService loginClientService(LoginClientProvider provider, SecurityProperties properties) {
        return new DefaultLoginClientService(provider, properties);
    }

    /**
     * 绑定客户端登录运行时服务，供 LoginHelper 静态方法使用。
     */
    @Bean
    public LoginClientHolderInitializer loginClientHolderInitializer(LoginClientService service) {
        LoginClientHolder.bind(service);
        return new LoginClientHolderInitializer();
    }

    /**
     * 默认密码策略来源：读取 ypbin.security.password。业务方做成后台可配置时提供自定义实现覆盖。
     */
    @Bean
    @ConditionalOnMissingBean
    public PasswordPolicyProvider passwordPolicyProvider(SecurityProperties properties) {
        return new DefaultPasswordPolicyProvider(properties);
    }

    /**
     * 密码复杂度校验器。
     */
    @Bean
    @ConditionalOnMissingBean
    public PasswordValidator passwordValidator(PasswordPolicyProvider policyProvider) {
        return new PasswordValidator(policyProvider);
    }

    /**
     * 密码有效期判定工具。
     */
    @Bean
    @ConditionalOnMissingBean
    public PasswordExpiration passwordExpiration(PasswordPolicyProvider policyProvider) {
        return new PasswordExpiration(policyProvider);
    }

    /**
     * 密码错误计数存储：存在 Redis 时用 Redis（多节点共享），否则用内存。
     */
    @Bean
    @ConditionalOnMissingBean
    public PasswordAttemptStore passwordAttemptStore(ObjectProvider<StringRedisTemplate> redisTemplate) {
        StringRedisTemplate template = redisTemplate.getIfAvailable();
        return template != null ? new RedisPasswordAttemptStore(template) : new InMemoryPasswordAttemptStore();
    }

    /**
     * 密码错误锁定限制器。
     */
    @Bean
    @ConditionalOnMissingBean
    public PasswordAttemptLimiter passwordAttemptLimiter(PasswordAttemptStore store,
        PasswordPolicyProvider policyProvider) {
        return new PasswordAttemptLimiter(store, policyProvider);
    }

    /**
     * 在线用户服务：基于 Sa-Token 会话枚举在线用户、强制下线。
     */
    @Bean
    @ConditionalOnMissingBean
    public OnlineUserService onlineUserService() {
        return new DefaultOnlineUserService();
    }

    /**
     * Sa-Token 权限接口适配器。
     */
    @Bean
    @ConditionalOnMissingBean
    public StpInterface stpInterface(PermissionProvider permissionProvider) {
        return new StpPermissionAdapter(permissionProvider);
    }

    /**
     * 全局登录校验拦截器配置。
     *
     * <p>仅在 Servlet Web 环境、类路径存在 {@link SaInterceptor} 与 {@link WebMvcConfigurer}、且
     * {@code ypbin.security.interceptor=true}（默认）时装配。业务方提供自定义 {@link WebMvcConfigurer}
     * 或关闭该开关即可覆盖/停用。</p>
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass({SaInterceptor.class, WebMvcConfigurer.class})
    @ConditionalOnProperty(prefix = "ypbin.security", name = "interceptor", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(SaTokenWebConfigurer.class)
    public SaTokenWebConfigurer saTokenWebConfigurer(SecurityProperties properties) {
        return new SaTokenWebConfigurer(properties);
    }

    /**
     * Sa-Token 认证/鉴权异常处理器。
     *
     * <p>把未登录/无权限/无角色等异常转为统一 R 响应（401/403），避免落入 web 兜底而返回 500。
     * 仅 Servlet Web 环境装配，业务方提供自定义同类处理器可覆盖。</p>
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    public SaTokenExceptionHandler saTokenExceptionHandler() {
        return new SaTokenExceptionHandler();
    }

    /**
     * 空标记 Bean，仅用于触发 {@link LoginClientHolder#bind(LoginClientService)}。
     */
    public static final class LoginClientHolderInitializer {
    }
}

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
import cn.dev33.satoken.listener.SaTokenEventCenter;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import cn.ypbin.starter.security.client.DefaultLoginClientProvider;
import cn.ypbin.starter.security.client.DefaultLoginClientService;
import cn.ypbin.starter.security.client.LoginClientHolder;
import cn.ypbin.starter.security.client.LoginClientProvider;
import cn.ypbin.starter.security.client.LoginClientService;
import cn.ypbin.starter.security.core.LoginVerifyProvider;
import cn.ypbin.starter.security.core.PermissionProvider;
import cn.ypbin.starter.security.handler.SaTokenExceptionHandler;
import cn.ypbin.starter.security.identity.IdentityStpLogic;
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
import cn.ypbin.starter.security.satoken.LoginVerifyListener;
import cn.ypbin.starter.security.satoken.SaTokenWebConfigurer;
import cn.ypbin.starter.security.satoken.SecurityExcludePathProvider;
import cn.ypbin.starter.security.satoken.StpPermissionAdapter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
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
// Servlet 专属（登录拦截器/SaInterceptor/WebMvcConfigurer）：WebFlux 应用（如网关）不装配，
// 避免无 spring-webmvc 时类加载 NoClassDefFoundError；网关只需 UserContext/IdentityContext 等静态核心类
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(SecurityProperties.class)
@Import(SecurityAutoConfiguration.RedisAttemptStoreConfiguration.class)
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
     * 密码错误计数存储（内存兜底）：无 Redis 或无自定义实现时装配；存在 Redis 时由
     * {@link RedisAttemptStoreConfiguration} 先注册的 Redis 实现接管，本 Bean 自动退让。
     */
    @Bean
    @ConditionalOnMissingBean(PasswordAttemptStore.class)
    public PasswordAttemptStore passwordAttemptStore() {
        return new InMemoryPasswordAttemptStore();
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
     * 登录回验监听器。
     *
     * <p>收集容器中所有 {@link LoginVerifyProvider}，在每次登录成功后逐个回验（如授权 License 回验），
     * 并注册进 Sa-Token 事件中心。</p>
     *
     * <p>不使用 {@code @ConditionalOnBean(LoginVerifyProvider.class)}：那会依赖自动配置的处理顺序，若
     * 贡献 Provider 的模块晚于本配置装配，条件会误判为无 Provider 而静默不注册。改为始终创建本 Bean，用
     * {@link ObjectProvider} 在上下文刷新时惰性收集全部 Provider（不受装配顺序影响），仅当确有 Provider
     * 时才注册监听器，避免注册空转监听器。业务方提供自定义同名 Bean 可覆盖。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public LoginVerifyListener loginVerifyListener(ObjectProvider<LoginVerifyProvider> providers) {
        List<LoginVerifyProvider> list = providers.orderedStream().toList();
        LoginVerifyListener listener = new LoginVerifyListener(list);
        if (!list.isEmpty()) {
            SaTokenEventCenter.registerListener(listener);
            log.info("[ypbin-starter] 已启用登录回验监听器（{} 个回验源），登录成功后将执行平台级回验。", list.size());
        }
        return listener;
    }

    /**
     * Sa-Token 拦截器配置（登录校验与注解鉴权由两个相互独立的开关控制）。
     *
     * <p>Servlet Web 环境、类路径存在 {@link SaInterceptor} 与 {@link WebMvcConfigurer} 时装配，
     * <strong>装配与否不看开关值</strong>——由 {@link SaTokenWebConfigurer} 在注册阶段按
     * {@code ypbin.security.interceptor} / {@code ypbin.security.annotation-check} 决定注册内容，
     * 两个开关都为 {@code false} 时不注册任何拦截器。这里刻意不用 {@code @ConditionalOnExpression}
     * 做装配条件：SpEL 对 {@code yes}/{@code on}/{@code 1} 这类非布尔字面量会抛异常并让应用**启动失败**，
     * 而 {@code @ConditionalOnProperty} 的既有语义是「值不匹配即不装配」（不影响启动）。</p>
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass({SaInterceptor.class, WebMvcConfigurer.class})
    @ConditionalOnMissingBean(SaTokenWebConfigurer.class)
    public SaTokenWebConfigurer saTokenWebConfigurer(SecurityProperties properties,
        ObjectProvider<SecurityExcludePathProvider> excludePathProviders) {
        return new SaTokenWebConfigurer(properties, excludePathProviders.orderedStream().toList());
    }

    /**
     * 微服务下游的身份头账号体系桥。
     *
     * <p>仅当 {@code ypbin.security.identity.enabled=true}（宿主显式声明自己位于可信网关之后、网关负责
     * 清洗并签发身份头）时装配，并把 {@link IdentityStpLogic} 注册为 Sa-Token 默认账号体系实现：注解鉴权
     * （{@code @SaCheckPermission} 等）因此以网关签发的身份头为账号来源，无需 Sa-Token 会话即可工作。
     * 这是「下游关掉登录拦截后仍保留注解鉴权」的另一半（拦截器侧见 {@link SaTokenWebConfigurer}）。</p>
     *
     * <p>按类型让位：宿主自定义了任意 {@link StpLogic} Bean 时本 Bean 不装配（Sa-Token 的 Bean 注入机制
     * 会把宿主的实现装进 {@code StpUtil}，此时身份头桥由宿主自行决定是否保留）。这里显式调用
     * {@code StpUtil.setStpLogic(...)} 而不是只依赖 Sa-Token 的自动注入：注册是注解鉴权能否生效的前提，
     * 不允许因自动注入缺席而静默退化成「注解不生效」。</p>
     *
     * <p>注册是进程级静态状态，因此该模式下无 token-session，token 活跃度冻结与自动续期不生效，
     * token 生命周期由网关侧承担。</p>
     */
    @Bean
    @ConditionalOnProperty(prefix = "ypbin.security.identity", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(StpLogic.class)
    public IdentityStpLogic identityStpLogic() {
        IdentityStpLogic stpLogic = new IdentityStpLogic();
        StpUtil.setStpLogic(stpLogic);
        log.info("[ypbin-starter] 已启用身份头账号体系（IdentityStpLogic）：注解鉴权以身份头为账号来源，"
            + "token 活跃度校验与自动续期在该模式下不生效");
        return stpLogic;
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

    /**
     * Redis 密码错误计数存储配置。
     *
     * <p>类级 {@code @ConditionalOnClass(StringRedisTemplate.class)}：无 spring-data-redis 时整个类被跳过、
     * 其 {@code @Bean} 方法不被内省——方法级 {@code @ConditionalOnClass} 阻止不了 Spring 对方法签名的内省，
     * 签名里含 {@code StringRedisTemplate} 而无该依赖时会先抛 NoClassDefFoundError。经 {@code @Import}
     * 先于外层内存兜底注册，存在 Redis 时优先生效、内存兜底退让。</p>
     *
     * <p>类级 {@code @ConditionalOnClass} 只判断 classpath 是否有该类，不代表容器里真有可用的
     * {@link StringRedisTemplate} Bean（如未配置 Redis 连接、或测试环境只引入了依赖未装配自动配置）。
     * 叠加 {@code @ConditionalOnBean(StringRedisTemplate.class)}，两者都满足才展开本配置，避免 Redis Bean
     * 因缺少注入源在启动期抛 {@code UnsatisfiedDependencyException}。</p>
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnBean(StringRedisTemplate.class)
    static class RedisAttemptStoreConfiguration {

        @Bean
        @ConditionalOnMissingBean(PasswordAttemptStore.class)
        public PasswordAttemptStore passwordAttemptStore(StringRedisTemplate redisTemplate) {
            return new RedisPasswordAttemptStore(redisTemplate);
        }
    }
}

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

import cn.ypbin.starter.log.autoconfigure.LogAutoConfiguration;
import cn.ypbin.starter.log.core.LogClientProvider;
import cn.ypbin.starter.log.core.LogUserProvider;
import cn.ypbin.starter.security.core.UserContext;
import cn.ypbin.starter.security.log.SessionLogClientProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 安全 - 操作日志客户端桥接自动配置。
 *
 * <p>仅当 log 模块存在于 classpath 时生效，把当前登录信息桥接给操作日志：
 * <ul>
 *     <li>{@link LogUserProvider} —— 操作人 userId，让日志能记录「谁做的」；</li>
 *     <li>{@link LogClientProvider} —— 客户端信息（clientId/clientType/authType），
 *     让日志能记录「从哪个客户端、用什么方式登录」。</li>
 * </ul>
 * 两者均 {@code @ConditionalOnMissingBean}，业务方可覆盖。</p>
 *
 * <p><strong>两个默认实现都必须走「自适应入口」，不能只读 Sa-Token 会话</strong>：本 starter 明确支持
 * 微服务身份头模式（网关签发内部身份头、下游服务无 Sa-Token 会话）。若这里只读会话，
 * 身份头模式下操作日志的 {@code userId} 与三个客户端字段<strong>恒为空</strong>——数据静默丢失、
 * 却查不到任何报错，正是本类要消除的失效形态。</p>
 *
 * <p><strong>为什么新 Bean 必须留在本类（类级 {@code @ConditionalOnClass(LogClientProvider.class)} 内）</strong>：
 * {@code ypbin-starter-log} 在本模块 pom 里是 {@code <optional>true</optional>}。若为身份头模式另起一个
 * 不带该条件的自动配置类，未引入 log 的宿主会在配置类内省阶段加载不到 {@code LogClientProvider}
 * 而 {@code NoClassDefFoundError} 启动崩溃（见仓库 PITFALL 4）。类级条件在内省{@code @Bean} 方法签名
 * 之前求值，因此这里既安全又不需要新增配置类。</p>
 *
 * <p><strong>与 log 模块默认实现的注册顺序</strong>：log 的 {@code LogAutoConfiguration} 也提供
 * {@code LogUserProvider}/{@code LogClientProvider} 的「返回空」默认实现，两者都用
 * {@code @ConditionalOnMissingBean}。跨自动配置类使用该注解时必须声明顺序，否则存在注册竞态
 * （历史上实测过 log 默认实现先注册、桥接被跳过 ⇒ userId 恒空）。故本类用
 * {@link AutoConfigureBefore} 排到 {@code LogAutoConfiguration} 之前。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
@AutoConfiguration
@AutoConfigureBefore(LogAutoConfiguration.class)
@ConditionalOnClass(LogClientProvider.class)
@ConditionalOnProperty(prefix = SecurityProperties.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
public class SecurityLogClientAutoConfiguration {

    /**
     * 操作人提供者：委托 {@link UserContext#getUserIdSafely()} 的自适应入口。
     *
     * <p><strong>这是行为变更，不是等价替换</strong>：{@code UserContext.getUserIdSafely()} 优先取微服务
     * 身份头（{@code IdentityContext}）、无身份头时回退 Sa-Token 会话，且把「无 Web 上下文/会话异常」
     * 收敛为空 Optional。与旧实现（直接 {@code LoginHelper::getUserIdSafely}，只读会话）相比：</p>
     * <ul>
     *     <li>身份头模式（微服务下游、无 Sa-Token 会话）由「恒为空」变为能取到真实 userId；</li>
     *     <li>身份头与会话<strong>同时存在</strong>时，返回<strong>身份头</strong>的 userId（旧实现返回会话 userId）。
     *     该优先级是契约，由 {@code SecurityLogClientAutoConfigurationTest} 的三条用例钉死
     *     （仅身份头 / 仅会话 / 两者共存取身份头）。</li>
     * </ul>
     *
     * @return 操作人提供者
     */
    @Bean
    @ConditionalOnMissingBean
    public LogUserProvider securityLogUserProvider() {
        return UserContext::getUserIdSafely;
    }

    /**
     * 操作日志客户端信息提供者（身份头模式 + 会话回退）。
     *
     * <p>网关签发的身份头只有 id/username/tenantId/deptId/roles 五个字段，
     * <strong>不包含</strong> clientId/clientType/authType；这三个值由 auth 在登录收尾时写入
     * 账号会话里的 {@code LoginUser}。故实现按 userId（自适应入口）读账号会话，身份头与单体两种
     * 形态共用同一份实现，详见 {@link SessionLogClientProvider}。</p>
     *
     * @return 客户端信息提供者
     */
    @Bean
    @ConditionalOnMissingBean
    public LogClientProvider securityLogClientProvider() {
        return new SessionLogClientProvider();
    }
}

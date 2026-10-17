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
package cn.ypbin.starter.tenant.autoconfigure;

import cn.ypbin.starter.security.identity.IdentityContext;
import cn.ypbin.starter.tenant.core.TenantProvider;
import cn.ypbin.starter.tenant.provider.IdentityTenantProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 身份上下文租户桥接自动配置。
 *
 * <p>把网关/身份层注入的 {@link IdentityContext} 接为 {@link TenantProvider}，使微服务下游服务
 * 无需在业务工程里重复编写（更不该违规注册进业务工程的 {@code AutoConfiguration.imports}）胶水代码：
 * 引入本模块即自动具备该租户来源。</p>
 *
 * <p><b>顺序</b>：显式用 {@link AutoConfigureBefore} 声明排到 {@link TenantAutoConfiguration} 之前——后者自带
 * {@code @ConditionalOnMissingBean} 的默认空租户提供者，只有本提供者先注册，默认实现才会退让。当前类名字典序
 * （{@code Identity} &lt; {@code Tenant}）恰好也给出同样结果，但这属偶然：一旦类改名或插入新自动配置即可能反转，
 * 顺序反转则默认空实现抢先注册、桥接静默失效。故保留显式声明，并以字典序不利（{@code Zzz} 前缀）的正反对照用例
 * 证明该注解确实生效，而非靠字典序侥幸。</p>
 *
 * <p><b>条件</b>：类级 {@link ConditionalOnClass}（而非 {@code @ConditionalOnBean}）——security 是本模块的
 * optional 依赖，不在 classpath 时整类跳过、既有行为不变；且 {@link IdentityContext} 是静态工具类
 * （无对应 Bean），类级判定无需顺序配合，正好避开本仓 {@code @ConditionalOnBean} 顺序陷阱。</p>
 *
 * <p><b>开关</b>：与 {@link TenantAutoConfiguration} 使用**同一个总开关** {@code ypbin.tenant.enabled=true}
 * （默认关闭，与既有行为一致）——租户特性关闭时不注册任何 {@link TenantProvider} Bean，避免"特性关了却
 * 多出一个无人消费的桥接 Bean"。启用租户后本桥接自动生效；若宿主想用别的租户来源，自定义
 * {@link TenantProvider} Bean 始终优先（{@code @ConditionalOnMissingBean(TenantProvider.class)}）。</p>
 *
 * @author wenbin
 * @since 2026-10-17
 */
@AutoConfiguration
@AutoConfigureBefore(TenantAutoConfiguration.class)
@ConditionalOnClass(IdentityContext.class)
@ConditionalOnProperty(prefix = TenantProperties.PREFIX, name = "enabled", havingValue = "true")
public class IdentityTenantAutoConfiguration {

    /**
     * 基于身份上下文取租户 ID 的提供者；无身份或身份未携带租户时返回空（见 {@link IdentityTenantProvider}）。
     */
    @Bean
    @ConditionalOnMissingBean(TenantProvider.class)
    public IdentityTenantProvider identityTenantProvider() {
        return new IdentityTenantProvider();
    }
}

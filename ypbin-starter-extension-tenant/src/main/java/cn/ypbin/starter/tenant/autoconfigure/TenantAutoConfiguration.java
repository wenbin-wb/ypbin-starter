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

import cn.ypbin.starter.core.context.ContextPropagator;
import cn.ypbin.starter.data.core.InnerInterceptorProvider;
import cn.ypbin.starter.tenant.aspect.TenantIgnoreAspect;
import cn.ypbin.starter.tenant.core.TenantContext;
import cn.ypbin.starter.tenant.core.TenantContextPropagator;
import cn.ypbin.starter.tenant.core.TenantProvider;
import cn.ypbin.starter.tenant.core.TenantThreadLocalAccessor;
import cn.ypbin.starter.tenant.handler.DefaultTenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import io.micrometer.context.ThreadLocalAccessor;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 多租户自动配置。
 *
 * <p>仅在 {@code ypbin.tenant.enabled=true} 时生效。通过 {@link InnerInterceptorProvider}
 * 以租户专用 order 向 data 模块贡献 {@link TenantLineInnerInterceptor}，确保其排在分页之前。
 * 租户来源由 {@link TenantProvider} 提供，默认返回空（不追加租户条件），业务方覆盖以对接实际来源。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@AutoConfiguration
@ConditionalOnClass(TenantLineInnerInterceptor.class)
@ConditionalOnProperty(prefix = "ypbin.tenant", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(TenantProperties.class)
public class TenantAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TenantAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public TenantProvider tenantProvider() {
        log.warn("[ypbin-starter] 使用默认租户提供者（始终返回空租户）。请提供 TenantProvider 实现以启用真正的租户隔离。");
        return Optional::empty;
    }

    @Bean
    @ConditionalOnMissingBean(name = "tenantInnerInterceptorProvider")
    public InnerInterceptorProvider tenantInnerInterceptorProvider(TenantProvider tenantProvider,
                                                                   TenantProperties properties) {
        return new InnerInterceptorProvider() {
            @Override
            public InnerInterceptor getInnerInterceptor() {
                return new TenantLineInnerInterceptor(
                    new DefaultTenantLineHandler(tenantProvider, properties));
            }

            @Override
            public int getOrder() {
                return ORDER_TENANT;
            }
        };
    }

    /**
     * 忽略租户切面：{@code @TenantIgnore} 方法作用域内临时放行租户隔离。
     */
    @Bean
    @ConditionalOnMissingBean
    public TenantIgnoreAspect tenantIgnoreAspect() {
        return new TenantIgnoreAspect();
    }

    /**
     * 租户上下文跨线程传播器：使异步任务子线程继承租户与忽略状态。
     */
    @Bean
    @ConditionalOnMissingBean
    public ContextPropagator<TenantContext.ContextSnapshot> tenantContextPropagator() {
        return new TenantContextPropagator();
    }

    /**
     * Reactor 跨线程租户传播：实现 {@link ThreadLocalAccessor} 并注册为 Bean，
     * Spring Boot 4 的 ObservationAutoConfiguration 自动将其加入全局 ContextRegistry，
     * Reactor 3.5+ 在每次线程切换时自动快照/还原租户上下文，保持隔离。
     * 仅在 context-propagation 在 classpath 时生效。
     */
    @Bean
    @ConditionalOnClass(ThreadLocalAccessor.class)
    @ConditionalOnMissingBean(TenantThreadLocalAccessor.class)
    public TenantThreadLocalAccessor tenantThreadLocalAccessor() {
        return new TenantThreadLocalAccessor();
    }
}

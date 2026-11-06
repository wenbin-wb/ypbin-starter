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
package cn.ypbin.starter.datapermission.autoconfigure;

import cn.ypbin.starter.core.context.ContextPropagator;
import cn.ypbin.starter.data.core.InnerInterceptorProvider;
import cn.ypbin.starter.datapermission.aspect.DataPermissionAspect;
import cn.ypbin.starter.datapermission.aspect.DataPermissionPointcut;
import cn.ypbin.starter.datapermission.core.DataPermissionContextPropagator;
import cn.ypbin.starter.datapermission.core.DataScopeHandler;
import cn.ypbin.starter.datapermission.handler.DataScopeMultiHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 数据权限自动配置。
 *
 * <p>仅在 {@code ypbin.data-permission.enabled=true} 且容器中存在业务方提供的
 * {@link DataScopeHandler} 时生效。通过 {@link InnerInterceptorProvider} 以数据权限专用
 * order 向 data 模块贡献 {@link DataPermissionInterceptor}，排在多租户之后、分页之前。</p>
 *
 * <p>数据权限规则依赖业务，本模块不提供默认 {@link DataScopeHandler} 实现：未提供时
 * 整个数据权限能力不装配，避免"默认放行/默认拦截"两种都不安全的猜测。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@AutoConfiguration
@ConditionalOnClass(DataPermissionInterceptor.class)
@ConditionalOnBean(DataScopeHandler.class)
@ConditionalOnProperty(prefix = DataPermissionAutoConfiguration.PREFIX, name = "enabled", havingValue = "true")
public class DataPermissionAutoConfiguration {

    /**
     * 配置前缀。
     *
     * <p>本能力只有装配开关、没有绑定字段，故前缀常量就近定义在本自动配置类内，
     * 并由 {@link DataPermissionMissingHandlerAutoConfiguration} 共用。</p>
     */
    public static final String PREFIX = "ypbin.data-permission";

    private static final Logger log = LoggerFactory.getLogger(DataPermissionAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(name = "dataPermissionInnerInterceptorProvider")
    public InnerInterceptorProvider dataPermissionInnerInterceptorProvider(DataScopeHandler dataScopeHandler) {
        log.debug("[ypbin-starter] data permission interceptor registered.");
        return new InnerInterceptorProvider() {
            @Override
            public InnerInterceptor getInnerInterceptor() {
                return new DataPermissionInterceptor(new DataScopeMultiHandler(dataScopeHandler));
            }

            @Override
            public int getOrder() {
                return ORDER_DATA_PERMISSION;
            }
        };
    }

    /**
     * 数据权限切面：只有 {@code @DataPermission} 方法作用域内才激活数据范围过滤，
     * 避免拦截器全局无差别拼接权限 SQL。
     */
    @Bean
    @ConditionalOnMissingBean
    public DataPermissionAspect dataPermissionAspect() {
        return new DataPermissionAspect();
    }

    /**
     * 数据权限切面装配：自定义切点同时覆盖实现类、目标类、接口方法与接口类上的注解，
     * CGLIB / JDK 两种代理行为一致。
     *
     * @param aspect 数据权限切面
     * @return 切面顾问
     */
    @Bean
    @ConditionalOnMissingBean(name = "dataPermissionAdvisor")
    public Advisor dataPermissionAdvisor(DataPermissionAspect aspect) {
        return new DefaultPointcutAdvisor(new DataPermissionPointcut(), aspect);
    }

    /**
     * 数据权限作用域的跨线程传播器。
     *
     * <p>不注册时，异步任务（{@code @Async}、自定义线程池）内的查询拿不到作用域，拦截器不拼数据范围，
     * 查询静默退化为全量——属 fail-open 的越权读。</p>
     *
     * @return 上下文传播器
     */
    @Bean
    @ConditionalOnMissingBean
    public ContextPropagator<Integer> dataPermissionContextPropagator() {
        return new DataPermissionContextPropagator();
    }
}

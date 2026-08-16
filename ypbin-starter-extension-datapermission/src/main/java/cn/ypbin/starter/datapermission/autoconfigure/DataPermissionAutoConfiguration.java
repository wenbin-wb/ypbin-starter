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

import cn.ypbin.starter.data.core.InnerInterceptorProvider;
import cn.ypbin.starter.datapermission.aspect.DataPermissionAspect;
import cn.ypbin.starter.datapermission.core.DataScopeHandler;
import cn.ypbin.starter.datapermission.handler.DataScopeMultiHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
@ConditionalOnProperty(prefix = "ypbin.data-permission", name = "enabled", havingValue = "true")
public class DataPermissionAutoConfiguration {

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
}

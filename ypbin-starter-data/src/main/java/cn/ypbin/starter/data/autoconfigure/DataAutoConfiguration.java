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
package cn.ypbin.starter.data.autoconfigure;

import cn.ypbin.starter.data.core.AuditorProvider;
import cn.ypbin.starter.data.core.InnerInterceptorProvider;
import cn.ypbin.starter.data.handler.DefaultMetaObjectHandler;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

/**
 * 数据访问自动配置。
 *
 * <p>装配 MyBatis-Plus 分页拦截器与审计字段自动填充。当业务方未提供
 * {@link AuditorProvider} 时使用返回空的默认实现，从而 data 模块可独立运行；
 * security 模块引入后可覆盖该 Bean 以对接实际登录用户。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@AutoConfiguration
@ConditionalOnClass(MybatisPlusInterceptor.class)
@ConditionalOnProperty(prefix = "ypbin.data", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(DataProperties.class)
public class DataAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DataAutoConfiguration.class);

    /**
     * 分页拦截器贡献者。
     *
     * <p>以较大 order 排在最后，确保多租户 / 数据权限等扩展拦截器先于分页执行。</p>
     */
    @Bean
    @ConditionalOnMissingBean(name = "paginationInnerInterceptorProvider")
    public InnerInterceptorProvider paginationInnerInterceptorProvider(DataProperties properties) {
        return new InnerInterceptorProvider() {
            @Override
            public com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor getInnerInterceptor() {
                PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(properties.getDbType());
                pagination.setMaxLimit(properties.getMaxLimit());
                pagination.setOverflow(properties.isOverflow());
                return pagination;
            }

            @Override
            public int getOrder() {
                return ORDER_PAGINATION;
            }
        };
    }

    /**
     * MyBatis-Plus 核心拦截器链：收集所有 {@link InnerInterceptorProvider}，按 order 升序装配。
     *
     * <p>顺序很关键：多租户 / 数据权限（小 order）必须先于分页（大 order）执行。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor(List<InnerInterceptorProvider> providers) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        List<InnerInterceptorProvider> sorted = new ArrayList<>(providers);
        AnnotationAwareOrderComparator.sort(sorted);
        for (InnerInterceptorProvider provider : sorted) {
            interceptor.addInnerInterceptor(provider.getInnerInterceptor());
        }
        log.debug("[ypbin-starter] mybatis-plus interceptor assembled with {} inner interceptor(s).", sorted.size());
        return interceptor;
    }

    /**
     * 默认审计人提供者：无上下文时返回空，保证 data 模块可独立运行。
     */
    @Bean
    @ConditionalOnMissingBean
    public AuditorProvider auditorProvider() {
        return Optional::empty;
    }

    /**
     * 审计字段自动填充处理器。
     */
    @Bean
    @ConditionalOnMissingBean
    public MetaObjectHandler metaObjectHandler(AuditorProvider auditorProvider) {
        return new DefaultMetaObjectHandler(auditorProvider);
    }
}

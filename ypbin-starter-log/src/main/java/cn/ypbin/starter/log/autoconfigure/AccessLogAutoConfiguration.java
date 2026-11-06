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
package cn.ypbin.starter.log.autoconfigure;

import cn.ypbin.starter.core.blocklist.ClientIpResolver;
import cn.ypbin.starter.log.aspect.AccessLogAspect;
import cn.ypbin.starter.log.support.LogMaskModule;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

/**
 * 全量访问日志自动配置。
 *
 * <p>仅在 Servlet Web 环境、存在 {@link RestController}（spring-web）且 {@code ypbin.log.access.enabled=true}
 * 时生效。注册 {@link AccessLogAspect} 打印控制器请求/响应分块日志，与基于 {@code @Log} 注解的操作日志互补。
 * 切面依赖 AOP，log 模块已引入 spring-boot-starter-aop。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@AutoConfiguration
@ConditionalOnClass(RestController.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = AccessLogProperties.PREFIX, name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AccessLogProperties.class)
public class AccessLogAutoConfiguration {

    @Bean(name = "accessLogAspect")
    @ConditionalOnMissingBean
    public AccessLogAspect accessLogAspectWithClientIpResolver(
        ObjectProvider<ObjectMapper> mapperProvider, AccessLogProperties properties,
        ObjectProvider<ClientIpResolver> clientIpResolverProvider) {
        // 与黑名单过滤器共用可信代理规则；解析器缺失时退化为只认直连地址，绝不无条件采信转发头
        ClientIpResolver clientIpResolver = clientIpResolverProvider.getIfAvailable(
            () -> new ClientIpResolver(List.of()));
        return createAccessLogAspect(mapperProvider, properties, clientIpResolver);
    }

    /**
     * 保留旧方法签名以维持二进制兼容；实际 Bean 由
     * {@link #accessLogAspectWithClientIpResolver(ObjectProvider, AccessLogProperties, ObjectProvider)} 注册。
     *
     * @param mapperProvider ObjectMapper 提供者
     * @param properties     访问日志配置
     * @return 访问日志切面
     * @deprecated 由容器装配 {@link AccessLogAspect}，不要直接调用本方法
     */
    @Deprecated(since = "4.4.0", forRemoval = false)
    public AccessLogAspect accessLogAspect(
        ObjectProvider<ObjectMapper> mapperProvider, AccessLogProperties properties) {
        return createAccessLogAspect(mapperProvider, properties, new ClientIpResolver(List.of()));
    }

    private AccessLogAspect createAccessLogAspect(ObjectProvider<ObjectMapper> mapperProvider,
        AccessLogProperties properties, ClientIpResolver clientIpResolver) {
        // 复用容器中的 ObjectMapper 配置（继承 json 模块），无则退化为默认实例；
        // rebuild() 隔离出日志专用实例再注册掩码模块，不影响业务接口的正常序列化
        ObjectMapper base = mapperProvider.getIfAvailable(ObjectMapper::new);
        ObjectMapper objectMapper = base.rebuild()
            .addModule(new LogMaskModule())
            .build();
        return new AccessLogAspect(objectMapper, properties, clientIpResolver);
    }
}

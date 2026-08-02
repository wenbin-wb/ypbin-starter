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

import cn.ypbin.starter.log.aspect.LogAspect;
import cn.ypbin.starter.log.core.IpLocationResolver;
import cn.ypbin.starter.log.core.LogClientProvider;
import cn.ypbin.starter.log.core.LogUserProvider;
import cn.ypbin.starter.log.dao.DefaultLogDao;
import cn.ypbin.starter.log.dao.LogDao;
import cn.ypbin.starter.log.enums.Include;
import cn.ypbin.starter.log.event.LogEventListener;
import cn.ypbin.starter.log.support.LogCollector;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 操作日志自动配置。
 *
 * <p>装配日志切面及其依赖：{@link LogDao}（默认打印）、{@link LogUserProvider}（默认空）、
 * {@link LogCollector}。仅在 AOP 存在且 {@code ypbin.log.enabled=true} 时生效，
 * 所有 Bean 均可被业务方覆盖。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@AutoConfiguration
@EnableAsync
@ConditionalOnClass(org.aspectj.lang.ProceedingJoinPoint.class)
@ConditionalOnProperty(prefix = "ypbin.log", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(LogProperties.class)
public class LogAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(LogAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public LogDao logDao() {
        return new DefaultLogDao();
    }

    @Bean
    @ConditionalOnMissingBean
    public LogUserProvider logUserProvider() {
        return Optional::empty;
    }

    @Bean
    @ConditionalOnMissingBean
    public LogClientProvider logClientProvider() {
        return Optional::empty;
    }

    /**
     * 默认 IP 归属地解析器：返回 null（不解析）。业务方接 ip2region 等实现自定义 Bean 覆盖即可。
     */
    @Bean
    @ConditionalOnMissingBean
    public IpLocationResolver ipLocationResolver() {
        return ip -> null;
    }

    @Bean
    @ConditionalOnMissingBean
    public LogCollector logCollector(LogUserProvider userProvider, LogClientProvider clientProvider,
        IpLocationResolver ipLocationResolver, ObjectProvider<ObjectMapper> objectMapperProvider) {
        // 复用容器中的 ObjectMapper（继承 json 模块配置），无则退化为默认实例
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        return new LogCollector(userProvider, clientProvider, ipLocationResolver, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public LogEventListener logEventListener(LogDao logDao) {
        return new LogEventListener(logDao);
    }

    @Bean
    @ConditionalOnMissingBean
    public LogAspect logAspect(LogCollector collector, ApplicationEventPublisher eventPublisher, LogProperties properties) {
        Set<Include> includes = (properties.getIncludes() != null && !properties.getIncludes().isEmpty())
            ? properties.getIncludes()
            : Include.defaultIncludes();
        log.debug("[ypbin-starter] operation log aspect enabled, includes={}.", includes);
        return new LogAspect(collector, eventPublisher, includes);
    }
}

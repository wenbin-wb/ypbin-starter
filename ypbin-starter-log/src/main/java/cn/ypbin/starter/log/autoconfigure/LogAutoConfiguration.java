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
import java.util.Optional;
import java.util.Set;
import org.aspectj.lang.ProceedingJoinPoint;
import org.jspecify.annotations.Nullable;
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
import tools.jackson.databind.ObjectMapper;

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
@ConditionalOnClass(ProceedingJoinPoint.class)
@ConditionalOnProperty(prefix = "ypbin.log", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(LogProperties.class)
public class LogAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(LogAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public LogDao logDao() {
        // 启动期显式告知：默认实现只把操作日志打到日志文件、不落库。否则宿主"以为已落库、查库/查表为空"
        // 属于最难排查的一类静默失效（无异常、无提示），这里用一行 INFO 把事实摆出来。
        log.info("[ypbin-starter] 未检测到自定义 LogDao，操作日志不会落库，仅打印到 ypbin.access-log 日志；"
            + "需要持久化请提供 LogDao Bean。");
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
    @Nullable
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
        // INFO 级：让运维能在启动日志里确认"切面确实注册了"，以及用的哪个采集器。
        // 采集器由本配置类的 logCollector() 兜底提供（@ConditionalOnMissingBean），宿主无需自行实现 LogCollector。
        log.info("[ypbin-starter] 操作日志切面已启用：collector={}, includes={}。",
            collector.getClass().getName(), includes);
        return new LogAspect(collector, eventPublisher, includes);
    }
}

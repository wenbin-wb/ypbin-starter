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
package cn.ypbin.starter.web.autoconfigure;

import cn.ypbin.starter.web.handler.GlobalExceptionHandler;
import cn.ypbin.starter.web.request.RepeatableReadRequestFilter;
import cn.ypbin.starter.web.xss.XssFilter;
import jakarta.servlet.DispatcherType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Web 层自动配置。
 *
 * <p>装配全局异常处理器与可选的 CORS 过滤器。仅在 Servlet Web 环境生效，
 * 所有 Bean 均以 {@link ConditionalOnMissingBean} 声明，允许业务方覆盖。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties({CorsProperties.class, XssProperties.class})
public class WebAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(WebAutoConfiguration.class);

    /**
     * 全局异常处理器。
     */
    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        log.debug("[ypbin-starter] global exception handler registered.");
        return new GlobalExceptionHandler();
    }

    /**
     * CORS 过滤器，仅在 {@code ypbin.web.cors.enabled=true} 时装配。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "ypbin.web.cors", name = "enabled", havingValue = "true")
    public CorsFilter corsFilter(CorsProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        properties.getAllowedOriginPatterns().forEach(config::addAllowedOriginPattern);
        properties.getAllowedMethods().forEach(config::addAllowedMethod);
        properties.getAllowedHeaders().forEach(config::addAllowedHeader);
        properties.getExposedHeaders().forEach(config::addExposedHeader);
        config.setAllowCredentials(properties.isAllowCredentials());
        config.setMaxAge(properties.getMaxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        log.debug("[ypbin-starter] CORS filter enabled.");
        return new CorsFilter(source);
    }

    /**
     * 可重复读请求过滤器，仅在 {@code ypbin.web.repeatable-read.enabled=true} 时装配。
     *
     * <p>以最高优先级排在其它过滤器之前，把带 body 的请求包装为可重复读，供 XSS、签名、
     * 日志等下游复用同一份缓存请求，避免各自包装与 body 读取冲突。签名等模块需要读 body 时
     * 应开启本项。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "ypbin.web.repeatable-read", name = "enabled", havingValue = "true")
    public FilterRegistrationBean<RepeatableReadRequestFilter> repeatableReadRequestFilterRegistration() {
        FilterRegistrationBean<RepeatableReadRequestFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RepeatableReadRequestFilter());
        registration.addUrlPatterns("/*");
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("ypbinRepeatableReadRequestFilter");
        log.debug("[ypbin-starter] repeatable-read request filter enabled.");
        return registration;
    }

    /**
     * XSS 过滤器，仅在 {@code ypbin.web.xss.enabled=true} 时装配。
     *
     * <p>用 {@link FilterRegistrationBean} 注册以便控制顺序（较高优先级，尽早清洗），
     * 仅作用于普通请求，不拦截异步分发。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "ypbin.web.xss", name = "enabled", havingValue = "true")
    public FilterRegistrationBean<XssFilter> xssFilterRegistration(XssProperties properties) {
        FilterRegistrationBean<XssFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new XssFilter(properties.getExcludes()));
        registration.addUrlPatterns("/*");
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 100);
        registration.setName("ypbinXssFilter");
        log.debug("[ypbin-starter] XSS filter enabled.");
        return registration;
    }
}

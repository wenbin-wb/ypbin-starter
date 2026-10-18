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

import cn.ypbin.starter.web.enums.BaseEnumConverterFactory;
import cn.ypbin.starter.web.handler.GlobalExceptionHandler;
import cn.ypbin.starter.web.request.RepeatableReadRequestFilter;
import cn.ypbin.starter.web.xss.XssFilter;
import jakarta.servlet.DispatcherType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.ObjectMapper;

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
@EnableConfigurationProperties({
    CorsProperties.class,
    RepeatableReadProperties.class,
    XssProperties.class
})
public class WebAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(WebAutoConfiguration.class);

    /**
     * {@code BaseEnum} 参数绑定的配置前缀。
     *
     * <p>独立前缀而非塞进已有 {@code ypbin.web.*} 子类：「枚举按 value 绑定」是横跨 JSON / 持久化 /
     * Web 三层的同一约定，各模块用自己的开关便于宿主按需取舍。</p>
     */
    public static final String BASE_ENUM_PREFIX = "ypbin.web.base-enum";

    /**
     * {@code BaseEnum} 参数绑定：让实现 {@link cn.ypbin.starter.core.enums.BaseEnum} 的枚举支持
     * {@code ?status=1} 形式的 URL 参数/表单字段绑定（按 {@code value} 匹配）。
     *
     * <p><b>注册方式</b>：本 bean 只声明转换器，实际注册进 MVC 的 {@code FormattingConversionService}
     * 由 {@link BaseEnumFormatterRegistrar}（{@link WebMvcConfigurer}）完成。分成两个 bean 是为了让
     * 「转换逻辑」可脱离 Spring MVC 单测（直接调 {@code matches}/{@code getConverter}），
     * 也便于宿主用 {@code @ConditionalOnMissingBean} 替换实现。</p>
     *
     * <p><b>不改变既有行为</b>：转换器实现了 {@code ConditionalConverter}，只对实现了
     * {@code BaseEnum} 的目标类型生效；未实现该接口的枚举仍由 Spring 内置
     * {@code StringToEnumConverterFactory} 按常量名绑定，行为不变。
     * 可用 {@code ypbin.web.base-enum.enabled=false} 关闭。</p>
     *
     * @return BaseEnum 转换器工厂
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = WebAutoConfiguration.BASE_ENUM_PREFIX, name = "enabled",
        havingValue = "true", matchIfMissing = true)
    public BaseEnumConverterFactory baseEnumConverterFactory() {
        log.debug("[ypbin-starter] base-enum converter factory registered (value-based enum binding).");
        return new BaseEnumConverterFactory();
    }

    /**
     * 把 {@link BaseEnumConverterFactory} 注册进 Spring MVC 的转换服务。
     *
     * <p>以 {@link WebMvcConfigurer} 形式装配而不是直接操作 {@code WebMvcAutoConfiguration}：
     * {@code WebMvcConfigurer#addFormatters} 是 Spring MVC 提供的唯一稳定扩展点，
     * 由 {@code WebMvcAutoConfigurationAdapter} 在创建 {@code mvcConversionService} 时回调。</p>
     *
     * @param converterFactory BaseEnum 转换器工厂
     * @return MVC 配置器
     */
    @Bean
    @ConditionalOnMissingBean(name = "baseEnumFormatterRegistrar")
    public WebMvcConfigurer baseEnumFormatterRegistrar(ConverterFactory<String, Enum<?>> converterFactory) {
        return new WebMvcConfigurer() {
            @Override
            public void addFormatters(FormatterRegistry registry) {
                registry.addConverterFactory(converterFactory);
            }
        };
    }

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
    @ConditionalOnProperty(prefix = CorsProperties.PREFIX, name = "enabled", havingValue = "true")
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
     *
     * <p>缓存上限默认 10MB（{@link RepeatableReadProperties#getMaxBodyBytes()}，配置项
     * {@code ypbin.web.repeatable-read.max-body-bytes}），超限请求在包装阶段即被拒绝读取。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = RepeatableReadProperties.PREFIX, name = "enabled", havingValue = "true")
    public FilterRegistrationBean<RepeatableReadRequestFilter> repeatableReadRequestFilterRegistration(
        RepeatableReadProperties properties, ObjectProvider<ObjectMapper> objectMapperProvider) {
        FilterRegistrationBean<RepeatableReadRequestFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RepeatableReadRequestFilter(properties.getMaxBodyBytes(),
            objectMapperProvider.getIfAvailable(ObjectMapper::new)));
        registration.addUrlPatterns("/*");
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("ypbinRepeatableReadRequestFilter");
        log.debug("[ypbin-starter] repeatable-read request filter enabled, max-body-bytes={}.",
            properties.getMaxBodyBytes());
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
    @ConditionalOnProperty(prefix = XssProperties.PREFIX, name = "enabled", havingValue = "true")
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

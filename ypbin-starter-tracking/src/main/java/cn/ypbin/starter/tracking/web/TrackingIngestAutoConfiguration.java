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
package cn.ypbin.starter.tracking.web;

import cn.ypbin.starter.tools.limiter.RateLimitProperties;
import cn.ypbin.starter.tracking.autoconfigure.TrackingAutoConfiguration;
import cn.ypbin.starter.tracking.autoconfigure.TrackingProperties;
import cn.ypbin.starter.tracking.core.TrackIdentityProvider;
import cn.ypbin.starter.tracking.core.TrackRecorder;
import cn.ypbin.starter.tracking.core.TrackingEventCatalog;
import cn.ypbin.starter.tracking.support.TrackCounters;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.DispatcherServlet;
import tools.jackson.databind.ObjectMapper;

/**
 * 采集端点自动配置。
 *
 * <p><strong>独立于主自动配置且默认关闭</strong>：端点是一个匿名可写入口，必须有单独开关；
 * 微服务下多个服务共用本模块，默认开启会让端点散布到每个服务上。</p>
 *
 * <p>装配条件同时要求「是 Servlet Web 应用」与「类路径存在 Spring MVC」，避免在 WebFlux 应用里
 * 注册一个永远不会被路由到的 Bean。</p>
 *
 * @author wenbin
 * @since 2026-09-15
 */
@AutoConfiguration(after = TrackingAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({DispatcherServlet.class, OncePerRequestFilter.class})
@ConditionalOnProperty(prefix = TrackingProperties.PREFIX, name = {"enabled", "ingest-enabled"}, havingValue = "true")
public class TrackingIngestAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TrackingIngestAutoConfiguration.class);

    /**
     * 采集服务：校验与入队。
     *
     * @param recorder   采集门面
     * @param counters   计数器
     * @param catalog    事件目录
     * @param properties 配置项
     * @return 采集服务
     */
    @Bean
    @ConditionalOnMissingBean
    public TrackIngestService trackIngestService(TrackRecorder recorder, TrackCounters counters,
                                                 TrackingEventCatalog catalog, TrackingProperties properties) {
        String appId = properties.getAppId().isBlank() ? null : properties.getAppId();
        return new TrackIngestService(recorder, counters, catalog, properties.getMaxEventsPerRequest(),
            properties.getMaxPayloadBytes(), appId, properties.isAnonymizeIp());
    }

    /**
     * 采集上下文解析器：在请求线程上取 IP / User-Agent / 链路 ID / 登录身份。
     *
     * @param properties       配置项
     * @param identityProvider 身份提供者（宿主可覆盖；缺省不提供身份维度）
     * @return 解析器
     */
    @Bean
    @ConditionalOnMissingBean
    public TrackRequestContextResolver trackRequestContextResolver(TrackingProperties properties,
                                                                   TrackIdentityProvider identityProvider) {
        return new TrackRequestContextResolver(properties.isTrustForwarded(), identityProvider);
    }

    /**
     * 采集端点。
     *
     * @param ingestService        采集服务
     * @param contextResolver      采集上下文解析器
     * @param rateLimitProperties  限流配置（仅用于装配期一致性告警）
     * @param properties           埋点配置项
     * @return 端点
     */
    @Bean
    @ConditionalOnMissingBean
    public TrackIngestController trackIngestController(TrackIngestService ingestService,
                                                       TrackRequestContextResolver contextResolver,
                                                       ObjectProvider<RateLimitProperties> rateLimitProperties,
                                                       TrackingProperties properties) {
        warnOnForwardedHeaderMismatch(rateLimitProperties.getIfAvailable(), properties);
        return new TrackIngestController(ingestService, contextResolver);
    }

    /**
     * 转发头信任开关的装配期一致性告警。
     *
     * <p>限流与埋点各自有一个 {@code trust-forwarded}（前者的取值影响安全性，不能由埋点模块代为决定），
     * 于是很容易「只开一个」：</p>
     * <ul>
     *   <li>只开限流侧：事件的 {@code clientIp} 恒为网关地址，脱敏后彻底失真；</li>
     *   <li>只开埋点侧：限流键退化为「方法 + 网关地址」单桶，匿名方可以低成本让正常上报全部被限流。</li>
     * </ul>
     * <p>这两条都不会报错、只会静默劣化，所以在此显式告警。</p>
     */
    private static void warnOnForwardedHeaderMismatch(@Nullable RateLimitProperties rateLimit,
                                                      TrackingProperties tracking) {
        boolean rateLimitTrusts = rateLimit != null && rateLimit.isTrustForwarded();
        if (rateLimitTrusts && tracking.isTrustForwarded()) {
            return;
        }
        log.warn("[ypbin-starter] tracking ingest endpoint is enabled, but forwarded-header trust is not fully "
                + "configured: ypbin.tools.rate-limit.trust-forwarded={}, ypbin.tracking.trust-forwarded={}. "
                + "Behind a gateway you should enable BOTH, otherwise the rate-limit bucket degrades to a single "
                + "global bucket keyed by the gateway address and/or recorded client IPs are the gateway itself.",
            rateLimitTrusts, tracking.isTrustForwarded());
    }

    /**
     * 请求体体积闸门，仅作用于采集端点路径。
     *
     * <p>这里用<strong>按 Bean 名</strong>的 {@code @ConditionalOnMissingBean}：按类型判断会命中宿主自己的
     * 其它 {@code FilterRegistrationBean}，从而误判为「已存在」而静默不注册本闸门。</p>
     *
     * @param objectMapperProvider 容器内的 Jackson 映射器（用于输出统一响应体）
     * @param counters             计数器
     * @param properties           配置项
     * @return 过滤器注册
     */
    @Bean
    @ConditionalOnMissingBean(name = "trackIngestSizeFilter")
    public FilterRegistrationBean<TrackIngestSizeFilter> trackIngestSizeFilter(
        ObjectProvider<ObjectMapper> objectMapperProvider, TrackCounters counters, TrackingProperties properties) {
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        FilterRegistrationBean<TrackIngestSizeFilter> registration = new FilterRegistrationBean<>(
            new TrackIngestSizeFilter(objectMapper, counters, properties.getMaxRequestBytes()));
        registration.addUrlPatterns(properties.getPath());
        registration.setName("ypbinTrackIngestSizeFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        log.debug("[ypbin-starter] tracking ingest endpoint registered at {}, maxRequestBytes={}.",
            properties.getPath(), properties.getMaxRequestBytes());
        return registration;
    }
}

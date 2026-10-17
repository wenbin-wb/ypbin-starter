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
package cn.ypbin.starter.cloud.autoconfigure;

import cn.ypbin.starter.cloud.feign.FeignHeaderInterceptor;
import cn.ypbin.starter.cloud.feign.RResponseErrorDecoder;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 微服务 Feign 增强自动配置。
 *
 * <p>仅在引入 OpenFeign 且 {@code ypbin.cloud.feign.enabled=true}（默认）时生效。装配统一错误解码器；
 * 只要 classpath 存在 Servlet API 就装配请求头透传拦截器——<b>不限于 Servlet Web 应用</b>：
 * {@code spring.main.web-application-type=none} 的纯后台/定时任务应用同样需要携带内部调用凭证，
 * 否则下游开启内部端点守卫（fail-closed）时会被 401 拒绝。业务方可提供自定义同类型 Bean 覆盖默认实现。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@AutoConfiguration
@ConditionalOnClass(RequestInterceptor.class)
@ConditionalOnProperty(prefix = FeignProperties.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(FeignProperties.class)
public class CloudFeignAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = FeignProperties.PREFIX, name = "error-decoder-enabled", havingValue = "true", matchIfMissing = true)
    public ErrorDecoder rResponseErrorDecoder(ObjectProvider<ObjectMapper> objectMapperProvider) {
        // 优先复用容器配置的 Jackson 3 序列化器（继承时间/大数字约定）；未装配时用等价默认实例，
        // 避免宿主未引 JSON 模块时因缺少 ObjectMapper Bean 直接启动失败。
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(() -> JsonMapper.builder().build());
        return new RResponseErrorDecoder(objectMapper);
    }

    /**
     * 请求头透传拦截器装配（含内部调用凭证）。
     *
     * <p><b>装配条件只有「classpath 有 Servlet API」</b>：{@link HttpServletRequest} 是
     * {@link FeignHeaderInterceptor} 的必需类型（取值经 {@code RequestContextHolder}，无请求上下文时
     * 自然跳过身份头透传），因此不能用 {@code @ConditionalOnWebApplication(SERVLET)} 把它限制在
     * Servlet Web 应用内——纯后台应用（{@code spring.main.web-application-type=none}）也需要
     * 携带内部凭证，否则下游开启守卫后会 401。</p>
     *
     * <p><b>为何把本类与其它 Bean 分开</b>：本嵌套配置内<b>只有</b>这一个 Bean，且它不绑定任何
     * Servlet 容器语义（不注入 {@code HttpServletRequest} 之类请求作用域对象，只静态调用
     * {@code RequestContextHolder}）。若将来在此加入依赖 Servlet 运行时语义的 Bean，应另建嵌套配置并
     * 保留 {@code @ConditionalOnWebApplication(SERVLET)}，否则非 Web 应用会启动失败。</p>
     */
    @ConditionalOnClass(HttpServletRequest.class)
    static class FeignHeaderConfiguration {

        @Bean
        @ConditionalOnMissingBean(FeignHeaderInterceptor.class)
        public FeignHeaderInterceptor feignHeaderInterceptor(FeignProperties properties) {
            return new FeignHeaderInterceptor(properties.getPropagateHeaders(),
                properties.getIdentityHeaders(), properties.getTrustedSourceHeader(),
                properties.getTrustedSourceToken(), properties.isRequireTrustedSource(),
                properties.getInternalTokenHeader(), properties.getInternalToken());
        }
    }
}

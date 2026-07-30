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
package cn.ypbin.starter.cloud.feign;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feign 请求头透传拦截器。
 *
 * <p>微服务调用链上，把当前请求（网关/上游进来的）里的认证、链路追踪、租户等请求头透传给
 * 下游 Feign 调用，保证鉴权与链路信息不丢。仅透传配置白名单内的头，避免误传
 * {@code Content-Length}/{@code Host} 等导致下游请求异常。</p>
 *
 * <p>无 Web 请求上下文（如异步线程、定时任务发起的 Feign 调用）时安全跳过——那种场景应配合
 * core 的 {@code ContextPropagator} 做上下文透传后再调用。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class FeignHeaderInterceptor implements RequestInterceptor {

    private final List<String> propagateHeaders;

    public FeignHeaderInterceptor(List<String> propagateHeaders) {
        this.propagateHeaders = propagateHeaders;
    }

    @Override
    public void apply(RequestTemplate template) {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return;
        }
        for (String name : propagateHeaders) {
            // 下游已显式设置该头则不覆盖
            if (!template.headers().containsKey(name)) {
                String value = request.getHeader(name);
                if (value != null && !value.isBlank()) {
                    template.header(name, value);
                }
            }
        }
    }

    private HttpServletRequest currentRequest() {
        try {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
                return attrs.getRequest();
            }
        } catch (Exception ignored) {
            // 无 Web 上下文，安全跳过
        }
        return null;
    }
}

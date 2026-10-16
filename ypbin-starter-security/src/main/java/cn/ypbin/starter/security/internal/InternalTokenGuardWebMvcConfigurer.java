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
package cn.ypbin.starter.security.internal;

import java.util.List;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 把 ENVELOPE 语义的内部凭证守卫挂到 {@link InternalTokenProperties#getEnvelopePathPatterns()}
 * （默认 {@code /internal/**}）。
 *
 * <p>路径清单为空时<b>不注册任何拦截器</b>：`addPathPatterns()` 为空数组在 Spring MVC 里语义含糊，
 * 显式跳过比交给框架兜底更可预期（RAW_401 侧的同类保护见
 * {@code InternalTokenGuardAutoConfiguration}，那里是"空清单会被 Boot 映射到 {@code /*}"的硬约束）。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
public class InternalTokenGuardWebMvcConfigurer implements WebMvcConfigurer {

    private final InternalTokenProperties properties;

    public InternalTokenGuardWebMvcConfigurer(InternalTokenProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        List<String> paths = properties.getEnvelopePathPatterns().stream()
            .filter(StringUtils::hasText)
            .toList();
        if (paths.isEmpty()) {
            return;
        }
        registry.addInterceptor(new InternalTokenGuardInterceptor(
            properties.getToken(), properties.getHeaderName()))
            .addPathPatterns(paths);
    }
}

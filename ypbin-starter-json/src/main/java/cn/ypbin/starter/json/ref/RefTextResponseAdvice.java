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
package cn.ypbin.starter.json.ref;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 引用翻译自动预加载切面。
 *
 * <p>在响应体序列化前自动扫描并按类型批量预加载引用翻译，业务<b>无需任何调用或注解</b>即享受列表零 N+1 翻译。
 * 依赖 {@link RefTextResolver} 的类级缓存快速跳过不含 {@link RefText} 的响应，对无关接口零遍历成本。</p>
 *
 * <p>个别接口想跳过时在方法/类上标注 {@link RefTextIgnore}；全局关闭设
 * {@code ypbin.json.ref-text.auto-resolve=false}。宿主未提供 {@link RefTextProvider}（即不存在
 * {@link RefTextResolver}）时本增强照常装配但不做事，不影响响应内容。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
@RestControllerAdvice
public class RefTextResponseAdvice implements ResponseBodyAdvice<Object> {

    @Nullable
    private final RefTextResolver resolver;

    public RefTextResponseAdvice(RefTextResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * 弱依赖构造：容器中没有 {@link RefTextResolver} 时装配为「不做事」的增强。
     *
     * <p><strong>为什么不在 {@code @Bean} 方法上用 {@code @ConditionalOnBean(RefTextResolver.class)}</strong>：
     * 自动配置类的条件在<em>解析阶段</em>求值，那时同一配置类里的 {@code RefTextResolver} 定义尚未注册，
     * 条件恒假 ⇒ 增强静默不装配、列表翻译仍逐行回源（N+1），且无任何日志。用 {@link ObjectProvider}
     * 把取值推迟到实例化阶段，与自动配置顺序无关。</p>
     *
     * @param resolverProvider 引用翻译解析器（可缺席）
     */
    public RefTextResponseAdvice(ObjectProvider<RefTextResolver> resolverProvider) {
        this.resolver = resolverProvider.getIfAvailable();
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // 标注 @RefTextIgnore 的方法/类跳过
        if (returnType.hasMethodAnnotation(RefTextIgnore.class)) {
            return false;
        }
        Class<?> declaringClass = returnType.getContainingClass();
        return !AnnotatedElementUtils.hasAnnotation(declaringClass, RefTextIgnore.class);
    }

    @Override
    public @Nullable Object beforeBodyWrite(@Nullable Object body, MethodParameter returnType,
        MediaType selectedContentType,
        Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request,
        ServerHttpResponse response) {
        if (body != null && resolver != null) {
            // preload 内部对不含 @RefText 的对象图会被类级缓存瞬间跳过，故可无条件调用
            resolver.preload(body);
        }
        return body;
    }
}

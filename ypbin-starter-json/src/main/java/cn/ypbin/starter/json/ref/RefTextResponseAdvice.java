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
 * {@code ypbin.json.ref-text.auto-resolve=false}。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
@RestControllerAdvice
public class RefTextResponseAdvice implements ResponseBodyAdvice<Object> {

    private final RefTextResolver resolver;

    public RefTextResponseAdvice(RefTextResolver resolver) {
        this.resolver = resolver;
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
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
        Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request,
        ServerHttpResponse response) {
        if (body != null) {
            // preload 内部对不含 @RefText 的对象图会被类级缓存瞬间跳过，故可无条件调用
            resolver.preload(body);
        }
        return body;
    }
}

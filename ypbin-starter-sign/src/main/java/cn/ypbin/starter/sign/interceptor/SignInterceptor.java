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
package cn.ypbin.starter.sign.interceptor;

import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.sign.annotation.ApiSign;
import cn.ypbin.starter.sign.autoconfigure.SignProperties;
import cn.ypbin.starter.sign.core.SignChecker;
import cn.ypbin.starter.sign.core.SignResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 签名校验拦截器。
 *
 * <p>支持两种模式（{@code ypbin.sign.mode}）：
 * <ul>
 *     <li>ANNOTATION：仅对标注 {@link ApiSign} 的接口校验（方法/类，类可被方法 ignore 排除）；</li>
 *     <li>GLOBAL：全局校验，按 {@code skip-path} 排除。</li>
 * </ul>
 * 校验失败返回统一 {@link R} 结构（HTTP 200，业务码非 0）。注解查找用 Spring 工具，兼容代理。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class SignInterceptor implements HandlerInterceptor {

    private final SignProperties properties;
    private final SignChecker signChecker;
    private final ObjectMapper objectMapper;
    private final PathMatcher pathMatcher = new AntPathMatcher();

    public SignInterceptor(SignProperties properties, SignChecker signChecker, ObjectMapper objectMapper) {
        this.properties = properties;
        this.signChecker = signChecker;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
        throws Exception {
        if (!needCheck(request, handler)) {
            return true;
        }
        SignResult result = signChecker.check(request);
        if (result.success()) {
            return true;
        }
        writeFail(response, result.message());
        return false;
    }

    private boolean needCheck(HttpServletRequest request, Object handler) {
        if (properties.getMode() == SignProperties.Mode.GLOBAL) {
            return !isSkipPath(request);
        }
        // ANNOTATION 模式：看方法/类是否标注 @ApiSign
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return false;
        }
        ApiSign methodAnno = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), ApiSign.class);
        if (methodAnno != null) {
            return !methodAnno.ignore();
        }
        ApiSign classAnno = AnnotatedElementUtils
            .findMergedAnnotation(handlerMethod.getBeanType(), ApiSign.class);
        return classAnno != null && !classAnno.ignore();
    }

    private boolean isSkipPath(HttpServletRequest request) {
        List<String> skipPath = properties.getSkipPath();
        if (skipPath == null || skipPath.isEmpty()) {
            return false;
        }
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank()) {
            uri = uri.substring(contextPath.length());
        }
        String finalUri = uri;
        return skipPath.stream().anyMatch(p -> pathMatcher.match(p, finalUri));
    }

    private void writeFail(HttpServletResponse response, String message) throws Exception {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(R.fail(message)));
    }
}

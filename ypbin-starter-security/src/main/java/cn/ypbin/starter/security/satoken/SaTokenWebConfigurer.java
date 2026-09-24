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
package cn.ypbin.starter.security.satoken;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import cn.ypbin.starter.security.autoconfigure.SecurityProperties;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 全局登录校验拦截器配置。
 *
 * <p>按两个<strong>相互独立</strong>的开关决定注册内容，统一拦截 {@code includes} 路径、放行
 * {@code excludes} 路径：</p>
 * <ul>
 *     <li>{@code ypbin.security.interceptor}（默认开启）：是否执行「登录态」校验
 *     （{@code StpUtil.checkLogin}）；</li>
 *     <li>{@code ypbin.security.annotation-check}（默认开启）：是否执行方法上的 Sa-Token 注解鉴权
 *     （{@code @SaCheckPermission} 等，由 {@link SaInterceptor#isAnnotation(boolean)} 控制），
 *     权限数据来自 {@link StpPermissionAdapter}。</li>
 * </ul>
 *
 * <p>两者都关闭时不注册任何拦截器。拆分前注解鉴权与登录校验共用一个开关：微服务下游服务没有 Sa-Token
 * 会话（身份来自网关注入的身份头），为不让登录校验必然失败只能关掉那个开关，注解鉴权因此一并失效。
 * 现在下游可只关 {@code interceptor} 而保留注解鉴权。</p>
 *
 * <p>检测到类路径存在 SpringDoc 时，自动追加 Swagger / 文档相关路径到放行列表，避免文档页被登录拦截。</p>
 *
 * <p>此外，收集所有 {@link SecurityExcludePathProvider} 贡献的路径合入放行列表：让其它 starter 端点（如靠
 * 一次性票据自证身份的 SSE 订阅端点）自动免于登录拦截，无需接入方手动配白名单。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
public class SaTokenWebConfigurer implements WebMvcConfigurer {

    /** SpringDoc 存在时自动放行的文档路径 */
    private static final List<String> API_DOC_EXCLUDES = List.of(
        "/doc.html", "/swagger-ui.html", "/swagger-ui/**",
        "/v3/api-docs/**", "/swagger-resources/**", "/webjars/**", "/favicon.ico");

    private static final String SPRINGDOC_MARKER =
        "org.springdoc.core.configuration.SpringDocConfiguration";

    private final SecurityProperties properties;

    private final List<SecurityExcludePathProvider> excludePathProviders;

    public SaTokenWebConfigurer(SecurityProperties properties,
        List<SecurityExcludePathProvider> excludePathProviders) {
        this.properties = properties;
        this.excludePathProviders = excludePathProviders;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        boolean loginCheck = properties.isInterceptor();
        boolean annotationCheck = properties.isAnnotationCheck();
        if (!loginCheck && !annotationCheck) {
            // 两个开关都关闭：宿主自行接管鉴权，注册一个什么都不做的拦截器只会增加每请求开销
            return;
        }
        List<String> excludes = new ArrayList<>(properties.getExcludes());
        if (properties.isExcludeApiDoc() && isSpringDocPresent()) {
            excludes.addAll(API_DOC_EXCLUDES);
        }
        // 其它模块贡献的放行路径（如 SSE 订阅端点靠 ticket 自证，须免登录拦截）
        for (SecurityExcludePathProvider provider : excludePathProviders) {
            List<String> paths = provider.excludePaths();
            if (paths != null) {
                excludes.addAll(paths);
            }
        }
        // 用无参构造：它自带 no-op 鉴权回调（SaInterceptor.preHandle 对 auth 字段无判空，不能传 null），
        // 只在需要登录校验时才覆盖为 StpUtil.checkLogin()；注解鉴权由 isAnnotation 开关单独控制
        SaInterceptor interceptor = new SaInterceptor() {
            /**
             * 非 REQUEST 分发（ERROR/ASYNC）直接放行：异步流（如 SSE）出错后的错误分发
             * 不在请求线程上，Sa-Token 上下文未初始化，二次登录校验会误报并掩盖真实错误。
             */
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                    Object handler) throws Exception {
                if (request.getDispatcherType() != DispatcherType.REQUEST) {
                    return true;
                }
                return super.preHandle(request, response, handler);
            }
        };
        if (loginCheck) {
            interceptor.setAuth(handle -> StpUtil.checkLogin());
        }
        interceptor.isAnnotation(annotationCheck);
        registry.addInterceptor(interceptor)
            .addPathPatterns(properties.getIncludes())
            .excludePathPatterns(excludes);
    }

    private boolean isSpringDocPresent() {
        try {
            Class.forName(SPRINGDOC_MARKER, false, getClass().getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            // 保留：这是「SpringDoc 是否存在」的显式能力探测，ClassNotFoundException 即「不存在」本身，
            // 不是失败；不存在时不追加文档路径放行也符合语义（那些路径本就不存在）
            return false;
        }
    }
}

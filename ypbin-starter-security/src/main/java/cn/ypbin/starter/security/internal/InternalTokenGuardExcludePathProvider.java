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

import cn.ypbin.starter.security.satoken.SecurityExcludePathProvider;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.util.StringUtils;

/**
 * 把内部凭证守卫接管的路径贡献给 Sa-Token 全局登录拦截的放行清单。
 *
 * <p><strong>为什么必须贡献</strong>：{@link InternalTokenGuardWebMvcConfigurer} 的 ENVELOPE 语义是
 * {@code HandlerInterceptor}，与 Sa-Token 的 {@code SaInterceptor} 同处 MVC 拦截器链；而
 * {@code SaTokenWebConfigurer} 默认拦截 {@code /**} 并执行 {@code StpUtil.checkLogin()}。服务间 Feign
 * 直连（只带 {@code X-Internal-Token}、没有用户会话）会在守卫拿到控制权<strong>之前</strong>就抛
 * {@code NotLoginException} ⇒ 内部凭证校验形同虚设、内部调用全部失败。</p>
 *
 * <p>与 SSE 订阅端点（{@code SecuritySseAutoConfiguration}）同一处理方式：由 starter 自己把"已由其它
 * 机制接管安全边界"的路径贡献出来，无需接入方手工往 {@code ypbin.security.excludes} 里塞。</p>
 *
 * <p><strong>放行边界严格等于守卫覆盖范围</strong>：只贡献 ENVELOPE 与 RAW_401 两份清单里的路径，且
 * RAW_401 侧先经 {@link InternalTokenGuardPatterns#toServletPatterns(List)} 归一化（与过滤器注册使用
 * 同一结果），<strong>不做 Ant 扩宽</strong>——例如不把 {@code /internal/mqtt/*} 放宽成
 * {@code /internal/mqtt/**}，否则会留下既不受登录校验、也不受凭证守卫保护的裸路径。</p>
 *
 * @author wenbin
 * @since 2026-10-17
 */
class InternalTokenGuardExcludePathProvider implements SecurityExcludePathProvider {

    private final List<String> excludePaths;

    /**
     * 构造时一次性计算放行清单：配置非法时在装配期即失败（fail-fast），
     * 也避免每次 {@link #excludePaths()} 调用重复归一化。
     *
     * <p><strong>守卫未开启时一律返回空集</strong>：这是纵深防御——当前装配路径由外层
     * {@code @ConditionalOnProperty(guard-enabled=true)} 保证不会注册本 Bean，但"豁免登录拦截"是
     * 安全边界上的让渡，绝不能只依赖单一条件；即便被手工 new 或未来装配方式变化，也不会凭空放行。</p>
     *
     * @param properties 守卫配置
     */
    InternalTokenGuardExcludePathProvider(InternalTokenProperties properties) {
        if (!properties.isGuardEnabled()) {
            // 守卫没开 ⇒ 这些路径没有任何凭证校验接管，登录拦截必须保留
            this.excludePaths = List.of();
            return;
        }
        // LinkedHashSet 去重且保持配置顺序：同一路径可同时出现在两份清单里（纵深防御），不必重复放行
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        for (String path : properties.getEnvelopePathPatterns()) {
            if (StringUtils.hasText(path)) {
                paths.add(path.trim());
            }
        }
        paths.addAll(InternalTokenGuardPatterns.toServletPatterns(properties.getRaw401PathPatterns()));
        this.excludePaths = List.copyOf(paths);
    }

    @Override
    public List<String> excludePaths() {
        return excludePaths;
    }
}

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

import java.util.List;

/**
 * Sa-Token 全局登录拦截的放行路径贡献者。
 *
 * <p>让其它模块把「自身机制要求免登录拦截」的路径贡献给 {@link SaTokenWebConfigurer}，而无需接入方手动往
 * {@code ypbin.security.excludes} 里塞——这些路径是 starter 端点自己的契约要求，理应由 starter 自动放行
 * （如同自动放行 SpringDoc 文档路径）。</p>
 *
 * <p>典型场景：SSE 订阅端点靠一次性票据自证身份（{@code EventSource} 不能带 Authorization 头），必须免于
 * 登录拦截，否则请求在进控制器前就被拦死、票据逻辑走不到。security 会收集所有此类 Bean 的路径合入 excludes。</p>
 *
 * @author wenbin
 * @since 2026-08-03
 */
@FunctionalInterface
public interface SecurityExcludePathProvider {

    /**
     * 贡献一组需放行登录拦截的路径模式。
     *
     * @return 放行路径模式（Ant 风格）
     */
    List<String> excludePaths();
}

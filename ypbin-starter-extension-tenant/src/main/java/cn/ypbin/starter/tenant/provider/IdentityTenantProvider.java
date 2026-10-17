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
package cn.ypbin.starter.tenant.provider;

import cn.ypbin.starter.security.identity.IdentityContext;
import cn.ypbin.starter.tenant.core.TenantProvider;
import java.util.Optional;

/**
 * 基于身份上下文的租户提供者（微服务身份头模式）。
 *
 * <p>租户来源是网关/身份层注入的 {@link IdentityContext}：网关校验 token 后签发可信身份头，
 * security 模块的 {@code IdentityHeaderFilter} 据此写入当前请求的身份上下文。下游服务因此
 * <strong>无需自行编写「读身份上下文 → 提供租户 ID」的胶水代码</strong>，引入本模块并在 classpath
 * 上存在 security 即自动获得该来源。</p>
 *
 * <p>取值语义严格跟随身份上下文：<strong>无身份</strong>（未登录，或异步线程/定时任务等本就
 * 无请求上下文的场景）与<strong>身份未携带租户</strong>两种情况一律返回 {@link Optional#empty()}，
 * 本类不做兜底猜测、不抛异常、不静默改用其它来源。返回空后如何处置由
 * {@code DefaultTenantLineHandler} 按 {@code ypbin.tenant.fail-on-missing-tenant} 决定
 * （默认 fail-closed 拒绝，置为 {@code false} 才退回「不追加租户条件」）。</p>
 *
 * <p>{@link IdentityContext} 是 {@link ThreadLocal} 语义的静态工具类，取值只在当前线程有效；
 * 跨线程/响应式场景需由 security 或租户上下文传播器先行还原上下文。</p>
 *
 * @author wenbin
 * @since 2026-10-17
 */
public class IdentityTenantProvider implements TenantProvider {

    @Override
    public Optional<Long> getCurrentTenantId() {
        return IdentityContext.getTenantId();
    }
}

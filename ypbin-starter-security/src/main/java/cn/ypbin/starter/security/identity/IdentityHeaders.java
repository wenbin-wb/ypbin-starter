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
package cn.ypbin.starter.security.identity;

/**
 * 内部身份头常量（网关签发、下游服务读取）。
 *
 * <p>网关校验 token 后签发这些头，{@link IdentityHeaderFilter} 据此构建
 * {@link LoginUser} 写入 {@link IdentityContext}。常量集中定义，网关与
 * 各业务服务共用同一份。</p>
 *
 * @author wenbin
 * @since 2026-09-01
 */
public final class IdentityHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String USER_NAME = "X-User-Name";
    public static final String TENANT_ID = "X-Tenant-Id";
    public static final String DEPT_ID = "X-Dept-Id";
    public static final String ROLES = "X-Roles";

    /**
     * 网关身份头来源标记头名。
     *
     * <p>网关签发身份头时同时写出该标记（值为与下游约定的随机串），下游
     * {@link IdentityHeaderFilter} 据此判定身份头来源可信（SF-5）；Feign 侧
     * （{@code FeignProperties.trustedSourceHeader}）默认亦同名。该常量只是默认头名，
     * 实际头名可通过 {@code ypbin.security.identity.trusted-source-header} 覆盖——
     * 网关与各下游必须配置一致。</p>
     */
    public static final String GATEWAY_SIGNED = "X-Gateway-Signed";

    private IdentityHeaders() {
    }
}

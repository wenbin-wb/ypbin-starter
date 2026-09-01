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

    private IdentityHeaders() {
    }
}

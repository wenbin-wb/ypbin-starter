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
package cn.ypbin.starter.tenant.core;

import java.util.Optional;

/**
 * 租户上下文提供者扩展点。
 *
 * <p>行级隔离时通过本接口获取当前请求的租户 ID。扩展模块不绑定具体的租户来源
 * （请求头 / 登录会话 / 域名等），由业务方实现完成解耦。未提供实现时使用默认实现，
 * 返回空表示不追加租户条件（如系统级操作）。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@FunctionalInterface
public interface TenantProvider {

    /**
     * 获取当前租户 ID。
     *
     * @return 租户 ID，无租户上下文时返回 {@link Optional#empty()}
     */
    Optional<Long> getCurrentTenantId();
}

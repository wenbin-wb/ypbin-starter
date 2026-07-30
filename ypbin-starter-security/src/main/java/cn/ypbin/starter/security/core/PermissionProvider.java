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
package cn.ypbin.starter.security.core;

import java.util.Collections;
import java.util.List;

/**
 * 权限数据源扩展点。
 *
 * <p>业务方实现本接口，向框架提供指定登录账号的权限码与角色码。框架内部会将其
 * 适配为 Sa-Token 的 {@code StpInterface}，从而支撑 {@code @SaCheckPermission}
 * 等注解鉴权。未提供实现时使用返回空列表的默认实现。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public interface PermissionProvider {

    /**
     * 获取账号的权限码集合。
     *
     * @param loginId   登录账号标识
     * @param loginType 账号体系类型（Sa-Token 多账号体系标识）
     * @return 权限码列表
     */
    default List<String> getPermissions(Object loginId, String loginType) {
        return Collections.emptyList();
    }

    /**
     * 获取账号的角色码集合。
     *
     * @param loginId   登录账号标识
     * @param loginType 账号体系类型
     * @return 角色码列表
     */
    default List<String> getRoles(Object loginId, String loginType) {
        return Collections.emptyList();
    }
}

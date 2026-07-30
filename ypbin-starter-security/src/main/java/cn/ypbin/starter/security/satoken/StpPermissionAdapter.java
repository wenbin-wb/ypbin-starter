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

import cn.dev33.satoken.stp.StpInterface;
import cn.ypbin.starter.security.core.PermissionProvider;
import java.util.List;

/**
 * Sa-Token 权限接口适配器。
 *
 * <p>将框架的 {@link PermissionProvider} 扩展点桥接到 Sa-Token 的 {@link StpInterface}，
 * 使业务方无需直接依赖 Sa-Token API 即可提供权限与角色数据。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class StpPermissionAdapter implements StpInterface {

    private final PermissionProvider permissionProvider;

    public StpPermissionAdapter(PermissionProvider permissionProvider) {
        this.permissionProvider = permissionProvider;
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return permissionProvider.getPermissions(loginId, loginType);
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return permissionProvider.getRoles(loginId, loginType);
    }
}

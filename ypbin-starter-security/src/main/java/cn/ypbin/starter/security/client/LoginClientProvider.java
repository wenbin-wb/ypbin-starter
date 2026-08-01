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
package cn.ypbin.starter.security.client;

import java.util.Optional;

/**
 * 登录客户端配置来源。
 *
 * <p>业务系统可实现本接口从数据库、配置中心或远程服务读取客户端配置。starter 默认提供配置文件版实现，
 * admin 系统有客户端管理表时用数据库实现覆盖即可。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public interface LoginClientProvider {

    /**
     * 根据客户端 ID 查询客户端配置。
     *
     * @param clientId 客户端 ID
     * @return 客户端配置
     */
    Optional<LoginClient> findByClientId(String clientId);
}

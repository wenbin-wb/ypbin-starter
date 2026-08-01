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

import cn.dev33.satoken.stp.parameter.SaLoginParameter;

/**
 * 登录客户端运行时服务。
 *
 * @author wenbin
 * @since 2026-08-01
 */
public interface LoginClientService {

    /**
     * 获取默认客户端 ID。
     *
     * @return 默认客户端 ID
     */
    String getDefaultClientId();

    /**
     * 查询并校验客户端。
     *
     * @param request 客户端登录请求
     * @return 客户端配置
     */
    LoginClient requireClient(LoginClientRequest request);

    /**
     * 构建 Sa-Token 登录参数。
     *
     * @param client  客户端配置
     * @param request 客户端登录请求
     * @return Sa-Token 登录参数
     */
    SaLoginParameter buildLoginParameter(LoginClient client, LoginClientRequest request);

    /**
     * 按客户端策略执行登录。
     *
     * @param userId  用户 ID
     * @param request 客户端登录请求
     * @return 客户端配置
     */
    LoginClient login(Long userId, LoginClientRequest request);
}

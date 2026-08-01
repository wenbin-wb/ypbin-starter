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
package cn.ypbin.starter.log.core;

import java.util.Optional;

/**
 * 登录客户端信息提供者扩展点。
 *
 * <p>日志采集时通过本接口获取当前登录客户端信息（客户端 ID、类型、认证方式）。log 模块不直接依赖
 * security 模块，由业务方（或 security 模块）提供实现完成解耦。未提供实现时使用返回空的默认实现。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public interface LogClientProvider {

    /**
     * 获取当前登录客户端信息。
     *
     * @return 客户端信息，未登录或无上下文时返回 {@link Optional#empty()}
     */
    Optional<LogClientInfo> getCurrentClient();

    /**
     * 登录客户端信息值对象。
     *
     * @param clientId   客户端 ID
     * @param clientType 客户端类型
     * @param authType   认证方式
     */
    record LogClientInfo(String clientId, String clientType, String authType) {
    }
}

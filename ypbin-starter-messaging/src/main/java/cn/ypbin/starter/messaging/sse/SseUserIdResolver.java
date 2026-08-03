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
package cn.ypbin.starter.messaging.sse;

import java.util.Optional;

/**
 * SSE 订阅用户解析扩展点。
 *
 * <p>内置订阅端点用它从<strong>服务端登录态</strong>解析当前用户标识，而非信任前端传参——这是内置端点的
 * 鉴权来源。返回空表示未登录，端点将拒绝建立连接。</p>
 *
 * <p>本模块不内置实现（不绑定具体鉴权框架）：引入 security 模块后自动桥接一个基于登录会话的实现；
 * 也可自行定义同类型 Bean 覆盖。<strong>无该 Bean 时内置订阅端点不注册</strong>，从根源杜绝无鉴权暴露。</p>
 *
 * @author wenbin
 * @since 2026-08-03
 */
@FunctionalInterface
public interface SseUserIdResolver {

    /**
     * 从当前请求的登录态解析用户标识。
     *
     * @return 当前登录用户标识；未登录时为 {@link Optional#empty()}
     */
    Optional<String> resolve();
}

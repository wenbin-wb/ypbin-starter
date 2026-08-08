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
package cn.ypbin.starter.social.core;

import java.util.Set;
import me.zhyd.oauth.request.AuthRequest;

/**
 * OAuth 授权请求注册表。
 *
 * <p>负责按平台标识动态注册、移除和查找授权请求。
 *
 * @author wenbin
 * @since 2026-08-08
 */
public interface SocialRequestRegistry {

    /**
     * 注册或替换指定平台的授权请求。
     *
     * @param source 平台标识
     * @param request 授权请求
     */
    void register(String source, AuthRequest request);

    /**
     * 移除指定平台的授权请求。
     *
     * @param source 平台标识
     * @return 被移除的授权请求，不存在时返回 {@code null}
     */
    AuthRequest remove(String source);

    /**
     * 获取指定平台的授权请求，不存在时显式抛出异常。
     *
     * @param source 平台标识
     * @return 授权请求
     */
    AuthRequest require(String source);

    /**
     * 获取已注册平台标识的不可变快照。
     *
     * @return 平台标识集合
     */
    Set<String> sources();
}

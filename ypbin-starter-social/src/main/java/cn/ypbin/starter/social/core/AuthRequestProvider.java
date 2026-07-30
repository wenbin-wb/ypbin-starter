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

import me.zhyd.oauth.request.AuthRequest;

/**
 * OAuth 授权请求提供者扩展点。
 *
 * <p>各第三方平台（微信、QQ、GitHub 等）的 {@link AuthRequest} 需要业务方各自的
 * appId / appSecret / 回调地址，且平台众多、配置敏感，本模块不预设。业务方为每个要支持的平台
 * 实现本接口（或注册对应 {@code AuthRequest} Bean），{@code SocialService} 按 source 调度。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public interface AuthRequestProvider {

    /**
     * 平台标识（小写，如 wechat / qq / github）。
     *
     * @return 平台标识
     */
    String getSource();

    /**
     * 该平台的 JustAuth 授权请求。
     *
     * @return {@link AuthRequest}
     */
    AuthRequest getAuthRequest();
}

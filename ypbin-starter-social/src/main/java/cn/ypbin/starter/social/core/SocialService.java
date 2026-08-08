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

import java.util.List;
import java.util.Set;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.request.AuthRequest;
import me.zhyd.oauth.utils.AuthStateUtils;

/**
 * 第三方登录服务。
 *
 * <p>按平台标识（source）从 {@link SocialRequestRegistry} 动态查找对应的 JustAuth {@link AuthRequest}：
 * 生成授权跳转地址、处理回调换取用户信息。
 *
 * @author wenbin
 * @since 2026-08-08
 */
public class SocialService {

    private final SocialRequestRegistry registry;

    public SocialService(SocialRequestRegistry registry) {
        this.registry = registry;
    }

    /**
     * 兼容基于授权请求提供者列表的构造方式。
     *
     * @param providers 授权请求提供者列表
     */
    public SocialService(List<AuthRequestProvider> providers) {
        this(new DefaultSocialRequestRegistry(providers));
    }

    /**
     * 生成指定平台的授权跳转地址。
     *
     * @param source 平台标识
     * @return 授权 URL（携带随机 state）
     */
    public String authorizeUrl(String source) {
        return require(source).authorize(AuthStateUtils.createState());
    }

    /**
     * 处理授权回调，换取第三方用户信息。
     *
     * @param source   平台标识
     * @param callback 回调参数（code / state 等）
     * @return 第三方用户信息
     */
    public AuthUser login(String source, AuthCallback callback) {
        AuthResponse<AuthUser> response = require(source).login(callback);
        if (!response.ok()) {
            throw new SocialException("第三方登录失败：" + response.getMsg());
        }
        return response.getData();
    }

    /**
     * 已注册的平台标识集合。
     *
     * @return 平台标识集合
     */
    public Set<String> sources() {
        return registry.sources();
    }

    private AuthRequest require(String source) {
        return registry.require(source);
    }
}

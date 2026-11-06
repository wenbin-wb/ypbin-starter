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

import org.jspecify.annotations.Nullable;

/**
 * OAuth {@code state} 的存储与一次性校验。
 *
 * <p><b>为什么必须存</b>：{@code state} 是 OAuth 的 CSRF 令牌。若发起授权时随机生成、回调时不校验，
 * 攻击者可以把自己在第三方平台完成的授权回调用受害者的会话重放（login CSRF），受害者的账号会被绑定
 * 到攻击者的第三方身份，进而以受害者身份操作或反向窃取数据。故 {@link SocialService#authorizeUrl}
 * 必须登记 state，{@link SocialService#login} 必须<b>先校验再换取用户信息</b>，且校验必须是
 * <b>一次性</b>（校验通过即删除，防重放）。</p>
 *
 * <p><b>部署形态</b>：默认为进程内实现 {@link InMemorySocialStateStore}，适用于单实例或会话粘滞部署；
 * 多实例无粘滞时必须由宿主提供共享实现（如 Redis），并在自动配置中覆盖本 Bean。</p>
 *
 * @author wenbin
 * @since 2026-11-06
 */
public interface SocialStateStore {

    /**
     * 登记一个 state（有效期由实现决定）。
     *
     * @param state 发起授权时生成的 state
     */
    void save(String state);

    /**
     * 校验并消费 state：存在且未过期返回 {@code true}，同时删除（一次性）。
     *
     * @param state 回调携带的 state（可空：回调可能不带 state，一律视为无效）
     * @return 是否有效
     */
    boolean consume(@Nullable String state);
}

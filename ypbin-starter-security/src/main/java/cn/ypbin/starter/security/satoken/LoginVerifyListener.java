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

import cn.dev33.satoken.listener.SaTokenListenerForSimple;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.ypbin.starter.security.core.LoginVerifyProvider;
import java.util.List;

/**
 * 登录回验监听器。
 *
 * <p>基于 Sa-Token 登录事件（{@code doLogin}），在每次登录成功后依次回调容器中所有
 * {@link LoginVerifyProvider}，实现「平台级」登录回验。任一 Provider 抛异常即中断，异常向上传播到登录调用
 * 处，使非法登录被阻断。</p>
 *
 * <p>登录事件在 {@code StpUtil.login} 内同步触发，因此回验异常会直接从登录调用栈抛出，交由统一异常处理器
 * 转换为响应。无任何 Provider 时本监听器不产生副作用。</p>
 *
 * @author wenbin
 * @since 2026-08-05
 */
public class LoginVerifyListener extends SaTokenListenerForSimple {

    private final List<LoginVerifyProvider> providers;

    public LoginVerifyListener(List<LoginVerifyProvider> providers) {
        this.providers = providers;
    }

    @Override
    public void doLogin(String loginType, Object loginId, String tokenValue, SaLoginParameter loginParameter) {
        for (LoginVerifyProvider provider : providers) {
            provider.verify(loginId, loginType);
        }
    }
}

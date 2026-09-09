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
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.ypbin.starter.security.core.LoginVerifyProvider;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 登录回验监听器。
 *
 * <p>基于 Sa-Token 登录事件（{@code doLogin}），在每次登录成功后依次回调容器中所有
 * {@link LoginVerifyProvider}，实现「平台级」登录回验。任一 Provider 抛异常即中断，异常向上传播到登录调用
 * 处，使非法登录被阻断。</p>
 *
 * <p>登录事件在 {@code StpUtil.login} 内同步触发，此时会话已落库；因此回验失败时本监听器先显式回收
 * 本次刚创建的 token/会话（{@link StpUtil#logoutByTokenValue}），再向调用栈抛出原异常，避免遗留
 * "幽灵登录态"。无任何 Provider 时本监听器不产生副作用。</p>
 *
 * @author wenbin
 * @since 2026-08-05
 */
public class LoginVerifyListener extends SaTokenListenerForSimple {

    private static final Logger log = LoggerFactory.getLogger(LoginVerifyListener.class);

    private final List<LoginVerifyProvider> providers;

    public LoginVerifyListener(List<LoginVerifyProvider> providers) {
        this.providers = providers;
    }

    @Override
    public void doLogin(String loginType, Object loginId, String tokenValue, SaLoginParameter loginParameter) {
        for (LoginVerifyProvider provider : providers) {
            try {
                provider.verify(loginId, loginType);
            } catch (RuntimeException e) {
                // 回验失败：Sa-Token 在触发本事件前已落库会话，显式回收本次新建的 token/会话后
                // 再传播原异常（回收失败只记 error，不吞原异常）
                recycleTokenQuietly(tokenValue);
                throw e;
            }
        }
    }

    private void recycleTokenQuietly(String tokenValue) {
        try {
            StpUtil.logoutByTokenValue(tokenValue);
        } catch (Exception e) {
            log.error("[ypbin-starter] 登录回验失败后回收会话失败，token 可能残留：{}", maskToken(tokenValue), e);
        }
    }

    /** 日志脱敏：仅显示 token 前缀，避免完整会话令牌落入日志 */
    private static String maskToken(String tokenValue) {
        if (tokenValue == null || tokenValue.length() <= 8) {
            return "***";
        }
        return tokenValue.substring(0, 8) + "***";
    }
}

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
package cn.ypbin.starter.security.identity;

import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;

/**
 * 以 {@link IdentityContext} 为基准的 Sa-Token 账号体系实现（微服务下游专用）。
 *
 * <p><strong>为什么需要它</strong>：Sa-Token 的注解鉴权（{@code @SaCheckPermission} 等）内部必然先解析
 * 「当前账号」（{@code StpLogic#getLoginId()}），而默认实现要求请求携带可查到 token-session 的 token。
 * 微服务下游服务没有 Sa-Token 会话——身份由可信网关清洗并签发的内部身份头承载（见
 * {@link IdentityHeaderFilter}）——于是注解鉴权只能抛「未登录」，权限码成为装饰。本类把
 * <strong>身份头当作账号来源</strong>接进 Sa-Token：token 值即当前账号标识，不再访问任何 token 存储，
 * 从而让下游既能关掉登录拦截（{@code ypbin.security.interceptor=false}），又保留注解鉴权
 * （{@code ypbin.security.annotation-check=true}）。</p>
 *
 * <p><strong>适用前提</strong>：仅在 {@code ypbin.security.identity.enabled=true}（即宿主已声明自己位于
 * 可信网关之后、且网关负责清洗外部身份头）时装配。此处信任的是网关已校验过的身份头，本类自身
 * 不做任何签名校验——若服务可被外部直接访问，开启该开关等于信任伪造身份。</p>
 *
 * <p><strong>能力边界</strong>：下游为无状态身份，没有 token-session，因此 token 活跃度冻结与自动续期
 * （{@code sa-token.active-timeout} / {@code dynamic-active-timeout}）在本模式下不生效——token 的生命周期
 * 由网关侧承担；需要二次安全验证的注解（如 {@code @SaCheckSafe}）因无安全会话一律拒绝（fail-closed，
 * 方向是「拒绝」而不是「放行」）。</p>
 *
 * @author wenbin
 * @since 2026-09-24
 */
public class IdentityStpLogic extends StpLogic {

    /**
     * 无身份头时返回的 token 值。
     *
     * <p>Sa-Token 以「token 为空」判定未登录（{@code SaFoxUtil.isEmpty}），因此这里用空串而不是
     * {@code null} 表达「无身份」——既能被 Sa-Token 正确识别为未登录，也不需要在非空返回类型上破例。</p>
     */
    private static final String NO_IDENTITY_TOKEN = "";

    /**
     * 使用 Sa-Token 默认账号体系（{@link StpUtil#TYPE}），使 {@code StpUtil} 与注解鉴权无需改造成本即可
     * 落到本实现上。
     */
    public IdentityStpLogic() {
        super(StpUtil.TYPE);
    }

    @Override
    public String getTokenValue() {
        return currentToken();
    }

    @Override
    public String getTokenValue(boolean noPrefixThrowException) {
        // 覆盖带前缀检查的重载：身份标识不是宿主配置的 token，不能因 sa-token.token-prefix 而判定「前缀缺失」
        return currentToken();
    }

    @Override
    public String getTokenValueNotCut() {
        return currentToken();
    }

    @Override
    public String getLoginIdNotHandle(String tokenValue) {
        // 身份头已由网关校验并签发，token 即账号标识；无 token 存储可查，也不应去查
        return currentToken();
    }

    @Override
    public boolean isOpenCheckActiveTimeout() {
        // 无状态身份模式没有 token-session 可供冻结与续期：开启该校验只会去访问不存在的会话
        return false;
    }

    /**
     * 由当前线程的身份头上下文派生 token 值：有身份返回账号标识，无身份返回空串（等价未登录）。
     *
     * @return token 值，永不为 {@code null}
     */
    private static String currentToken() {
        return IdentityContext.getUserId().map(String::valueOf).orElse(NO_IDENTITY_TOKEN);
    }
}

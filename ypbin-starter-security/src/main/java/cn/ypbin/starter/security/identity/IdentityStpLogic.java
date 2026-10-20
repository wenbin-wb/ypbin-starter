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
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import org.jspecify.annotations.Nullable;

/**
 * 以 {@link IdentityContext} 为基准的 Sa-Token 账号体系实现（微服务下游专用）。
 *
 * <p><strong>为什么需要它</strong>：Sa-Token 的注解鉴权（{@code @SaCheckPermission} 等）内部必然先解析
 * 「当前账号」（{@code StpLogic#getLoginId()}），而默认实现要求请求携带可查到 token-session 的 token。
 * 微服务下游服务没有 Sa-Token 会话——身份由可信网关清洗并签发的内部身份头承载（见
 * {@link IdentityHeaderFilter}）——于是注解鉴权只能抛「未登录」，权限码成为装饰。本类把
 * <strong>身份头当作账号来源</strong>接进 Sa-Token：当前请求身份的 token 值即当前账号标识，不再访问
 * token 存储，从而让下游既能关掉登录拦截（{@code ypbin.security.interceptor=false}），又保留注解鉴权
 * （{@code ypbin.security.annotation-check=true}）。</p>
 *
 * <p><strong>适用前提</strong>：仅在 {@code ypbin.security.identity.enabled=true}（即宿主已声明自己位于
 * 可信网关之后、且网关负责清洗外部身份头）时装配。此处信任的是网关已校验过的身份头，本类自身
 * 不做任何签名校验——若服务可被外部直接访问，开启该开关等于信任伪造身份。</p>
 *
 * <p><strong>能力边界（无状态身份模式，宿主不要在这些能力上依赖本类）</strong>：</p>
 * <ul>
 *     <li><b>按 token 反查</b>（{@code getLoginIdByToken} / {@code isValidToken} /
 *     {@code getTokenSessionByToken}）：只认与当前请求身份一致的 token，其它 token 一律按无效处理——
 *     不这样做会让「任意 token 的主人」被回答成当前调用者身份；</li>
 *     <li><b>续期</b>（{@code renewTimeout}）：本模式没有终端信息可续——有身份头但无会话时抛
 *     {@code SaTokenException}（不会静默成功），无身份头时按基类语义静默返回；</li>
 *     <li><b>token 会话</b>（{@code getTokenSession}）：仍会访问 SaTokenDao（生产上通常是 Redis），
 *     本模式不保证其语义；</li>
 *     <li><b>登出</b>（{@code logout}）：不影响 {@code isLogin()}（身份生命周期由网关承担）；</li>
 *     <li><b>二级认证</b>（{@code @SaCheckSafe}）：一律拒绝（fail-closed）；</li>
 *     <li><b>临时身份切换</b>（{@code switchTo}）：Sa-Token 的切换身份优先级高于身份头；</li>
 *     <li><b>账号标识</b>：loginId 为 userId 的十进制字符串，因此 userId 若为 {@code -3}/{@code -4}/{@code -5}
 *     会与 Sa-Token 内部哨兵值冲突（正常业务主键不受影响）。</li>
 * </ul>
 *
 * @author wenbin
 * @since 2026-09-24
 */
public class IdentityStpLogic extends StpLogic {

    /**
     * 无身份头时返回的 token 值。
     *
     * <p>Sa-Token 以「token 为空」判定未登录（{@code SaFoxUtil.isEmpty}），因此 {@code getTokenValue}
     * 系列用空串而不是 {@code null} 表达「无身份」——既能被 Sa-Token 正确识别为未登录，也不需要在
     * 非空返回类型上破例。</p>
     *
     * <p><strong>注意</strong>：{@link #getLoginIdNotHandle(String)} 是例外——它必须返回 {@code null}
     * 表达「该 token 不属于当前调用者」。Sa-Token 建 token 时以
     * {@code getLoginIdNotHandle(候选token) == null} 作为「候选 token 可用」的唯一判据（见
     * {@code StpLogic.lambda$distUsableToken$2}），此处若返回空串会导致唯一性判据恒不成立，
     * 建 token 重试 12 次后必然抛 {@code SaTokenException}（SF-4）。</p>
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

    /**
     * 反查「给定 token 对应的登录账号」。
     *
     * <p>只认「与当前请求身份一致」的 token，绝不忽略入参：若忽略，
     * {@code getLoginIdByToken}/{@code isValidToken} 会对任意 token 作答（把调用者身份冒充成
     * token 的主人），在线用户管理等按 token 反查的能力会因此给出错误答案。</p>
     *
     * <p><strong>无身份/不匹配必须返回 {@code null}</strong>：Sa-Token 建 token 的唯一性判据是
     * {@code getLoginIdNotHandle(候选) == null}（见 {@code StpLogic.lambda$distUsableToken$2}），
     * 返回空串会让判据恒不成立 → 12 次重试全败 → {@code SaTokenException}（SF-4）。</p>
     *
     * @param tokenValue 待反查的 token
     * @return 该 token 对应的登录账号标识；不属于当前调用者或无身份时为 {@code null}
     */
    @Override
    @Nullable
    public String getLoginIdNotHandle(String tokenValue) {
        String token = currentToken();
        if (!NO_IDENTITY_TOKEN.equals(token)) {
            // 有身份：只对入参等于当前身份标识的 token 作答
            return token.equals(tokenValue) ? token : null;
        }
        // 无身份：任何 token 都不属于当前调用者，必须返回 null。
        // Sa-Token 建 token 的唯一性判据是「getLoginIdNotHandle(候选) == null」，返回空串会让
        // 判据恒不成立 → 12 次重试全败 → SaTokenException（SF-4：identity 模式登录结构性失败）。
        return null;
    }

    @Override
    public boolean isOpenCheckActiveTimeout() {
        // 无状态身份模式没有 token-session 可供冻结与续期：开启该校验只会去访问不存在的会话
        return false;
    }

    /**
     * 由当前线程的身份头上下文派生 token 值：有身份返回账号标识，无身份返回空串（等价未登录）。
     *
     * <p>仅在 {@code getTokenValue} 系方法上代表「未登录」；{@link #getLoginIdNotHandle(String)}
     * 的「无身份」语义必须由返回 {@code null} 表达（见该方法的说明）。</p>
     *
     * @return token 值，永不为 {@code null}
     */
    private static String currentToken() {
        return IdentityContext.getUserId().map(String::valueOf).orElse(NO_IDENTITY_TOKEN);
    }

    // ------------------------------------------------------------------ 本地登录：一律 fail-fast
    //
    // 为什么在**这里**拦截：identity 模式下 StpUtil 的 token 值来自网关身份头，login() 要建的会话根本
    // 无处可放。放任它走 Sa-Token 内部流程的结果是「重试若干次 → NotLoginException/SaTokenException」，
    // 现象是**正确口令登录也返回 401**，与「token 过期」无法区分——2026-10-20 DEV 真实事故，
    // 排查成本极高（错误口令反而返回 409，把方向引到口令上）。
    // 故在入口处直接抛出**可自解释**的异常：调用方一眼看出「是 identity 模式冲突，不是口令/会话问题」。

    @Override
    public void login(Object id) {
        throw localLoginUnsupported();
    }

    @Override
    public void login(Object id, String device) {
        throw localLoginUnsupported();
    }

    @Override
    public void login(Object id, boolean isLastingCookie) {
        throw localLoginUnsupported();
    }

    @Override
    public void login(Object id, long timeout) {
        throw localLoginUnsupported();
    }

    @Override
    public void login(Object id, SaLoginParameter loginParameter) {
        throw localLoginUnsupported();
    }

    /**
     * 「identity 模式下本地登录不可用」的异常（唯一构造点，保证报文一致）。
     *
     * <p>取 {@link IllegalStateException} 而不是 {@code SaTokenException}：这不是「未登录/无权限」这类
     * 可被全局异常处理器转成 401/403 的业务态，而是**部署配置错误**——必须显式暴露成 500，
     * 否则又会被包装成「401 登录失败」，回到本次事故的迷惑形态。</p>
     *
     * @return 待抛出的异常（由各 login 重载 {@code throw}）
     */
    private static IllegalStateException localLoginUnsupported() {
        return new IllegalStateException(
            "[ypbin-starter] identity 模式（ypbin.security.identity.enabled=true）下不允许本地登录："
                + "该模式下 StpUtil 的 token 取自网关签发的身份头，login() 无法建立会话。"
                + "若本服务自己签发会话（如 auth），必须显式置 ypbin.security.identity.enabled=false"
                + "（identity 只给『位于可信网关之后、不自己签发会话』的下游服务）；"
                + "若确实需要本地登录而不改配置，请不要经由 StpUtil/LoginHelper 建会话。");
    }
}

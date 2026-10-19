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
package cn.ypbin.starter.security.log;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import cn.ypbin.starter.log.core.LogClientProvider;
import cn.ypbin.starter.security.core.LoginUser;
import cn.ypbin.starter.security.core.UserContext;
import cn.ypbin.starter.security.identity.IdentityContext;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * 登录客户端信息提供者（身份头模式 + 会话回退）。
 *
 * <p><strong>为什么需要它</strong>：网关签发的内部身份头只有
 * {@code id/username/tenantId/deptId/roles} 五项（见
 * {@link cn.ypbin.starter.security.identity.IdentityHeaders}），<strong>不含</strong>
 * {@code clientId/clientType/authType}。而操作日志的 {@code Include.CLIENT} 采集项会读取这三个字段——
 * 若只从「当前身份上下文」取，身份头模式下三元组恒为空（日志里永远看不出「从哪个客户端、用什么方式登录」）。
 * 这三个值由 auth 服务在登录收尾时写进 <strong>账号会话</strong>里的 {@link LoginUser}，因此本实现先按
 * 自适应入口取当前 userId（身份头优先、Sa-Token 会话回退），再按 loginId 读取账号会话中的
 * {@link LoginUser}。</p>
 *
 * <p><strong>为什么按 loginId 读账号会话而不是读当前 token 会话</strong>：身份头模式下下游服务没有
 * token（身份由网关校验），{@code StpUtil.getSession()} 拿不到会话；而账号会话以 loginId 为键，
 * 各服务共用同一份会话存储（微服务宿主统一引入 {@code sa-token-redis-template} 且 token-name 一致）时
 * 下游可读，与「在线用户按 loginId 读会话」是同一套机制。</p>
 *
 * <h2>两种模式的会话读取次数（本次性能优化的依据）</h2>
 *
 * <table border="1">
 *   <caption>会话读取次数</caption>
 *   <tr><th>模式</th><th>取值路径</th><th>会话读取次数</th></tr>
 *   <tr>
 *     <td>身份头模式（{@link IdentityContext#isLogin()} 为真）</td>
 *     <td>身份头只有 userId，三元组必须按 loginId 读<strong>账号会话</strong></td>
 *     <td><strong>1 次</strong>（{@code getSessionByLoginId}）；身份头本身不是会话读</td>
 *   </tr>
 *   <tr>
 *     <td>单体模式</td>
 *     <td>{@link UserContext#getLoginUser()} 直接读<strong>当前 token 会话</strong>里的
 *         {@code KEY_LOGIN_USER}——{@code LoginUser} 本来就在那里</td>
 *     <td><strong>1 次</strong>（{@code getSession}）</td>
 *   </tr>
 * </table>
 *
 * <p><b>本次消除的债</b>：此前单体模式下先 {@code getUserIdSafely()}（内部读会话取 loginId）、
 * 再 {@code StpUtil.getSessionByLoginId(userId, false)}（又读一次账号会话），同一次操作日志采集
 * 读了<strong>2 次</strong>会话；而单体模式下 {@code LoginUser} 就在当前 token 会话里，
 * 第二次读纯属多余。改为按模式分支后单体模式降为 <strong>1 次</strong>，且不再多一次
 * loginId → 账号会话的跨会话查询。身份头模式<strong>保持</strong>按 loginId 读账号会话——
 * 那是必须的，身份头里没有 client 三元组，且下游服务无 token 会话可读。</p>
 *
 * <p><strong>失败语义</strong>：本类只负责 3 个<strong>附加展示字段</strong>，读取失败不抛出——
 * starter 的 {@code LogAspect} 对采集异常是「丢整条日志」语义，为三个附加字段丢掉整条审计记录得不偿失。
 * 但失败绝不静默：会话读取异常/会话中登录用户类型不可识别一律 {@code log.error} 且带完整堆栈，
 * 让「三元组为空」这件事可定位（不属于静默降级）。登录时本就没回填客户端信息（字段全空）属于事实，
 * 直接返回空、不记错误、也不臆造取值。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
public class SessionLogClientProvider implements LogClientProvider {

    private static final Logger log = LoggerFactory.getLogger(SessionLogClientProvider.class);

    /**
     * 获取当前登录客户端信息。
     *
     * @return 客户端信息；未登录、无会话或会话中未写入这三个字段时为空
     */
    @Override
    public Optional<LogClientInfo> getCurrentClient() {
        // 身份头模式：身份头只有 id/username/tenantId/deptId/roles，没有 client 三元组，
        // 必须按 loginId 读一次账号会话（见类注释「两种模式的会话读取次数」）
        if (IdentityContext.isLogin()) {
            Long userId = IdentityContext.getUserId().orElse(null);
            if (userId == null) {
                return Optional.empty();
            }
            LoginUser loginUser = readAccountSession(userId);
            return loginUser == null ? Optional.empty() : toClientInfo(loginUser);
        }
        // 单体模式：LoginUser 就在当前 token 会话里，直接一次读到位。
        // 原先「先取 userId 再按 loginId 读账号会话」会二次读同一份会话，本次消除。
        return singleSessionLoginUser().flatMap(SessionLogClientProvider::toClientInfo);
    }

    /**
     * 单体模式：从当前 token 会话读取 {@link LoginUser}（一次会话读）。
     *
     * @return 登录用户；未登录/无会话/类型不可识别时为空
     */
    private static Optional<LoginUser> singleSessionLoginUser() {
        try {
            return UserContext.getLoginUser();
        } catch (RuntimeException ex) {
            log.error("[ypbin-starter] 操作日志客户端信息读取失败（读取 sa-token 会话异常），"
                + "本条日志的 clientId/clientType/authType 为空", ex);
            return Optional.empty();
        }
    }

    /**
     * 身份头模式：按 userId 读取<b>账号会话</b>中的 {@link LoginUser}。
     *
     * @param userId 当前登录用户 ID
     * @return 登录用户；读不到会话或类型不可识别时返回 {@code null}
     */
    @Nullable
    private static LoginUser readAccountSession(Long userId) {
        try {
            @Nullable SaSession session = StpUtil.getSessionByLoginId(userId, false);
            if (session == null) {
                // 本服务读不到账号会话（如未共用会话存储）：字段留空，不臆造
                return null;
            }
            @Nullable Object value = session.get(UserContext.KEY_LOGIN_USER);
            if (!(value instanceof LoginUser cached)) {
                if (value != null) {
                    log.error("[ypbin-starter] 操作日志客户端信息读取失败：账号会话中的登录用户类型不可识别"
                            + "（期望 {}，实际 {}），本条日志的 clientId/clientType/authType 为空；"
                            + "请确认该类型已登记在 META-INF/satoken/sa-json-type.list，userId={}",
                        LoginUser.class.getName(), value.getClass().getName(), userId);
                }
                return null;
            }
            return cached;
        } catch (RuntimeException ex) {
            log.error("[ypbin-starter] 操作日志客户端信息读取失败（读取 sa-token 账号会话异常），"
                + "本条日志的 clientId/clientType/authType 为空；userId={}", userId, ex);
            return null;
        }
    }

    /**
     * 三个展示字段全空视为「登录时未回填」，返回空而不臆造取值。
     *
     * @param loginUser 登录用户
     * @return 客户端信息；三元组全空时为空
     */
    private static Optional<LogClientInfo> toClientInfo(LoginUser loginUser) {
        if (!StringUtils.hasText(loginUser.getClientId())
            && !StringUtils.hasText(loginUser.getClientType())
            && !StringUtils.hasText(loginUser.getAuthType())) {
            return Optional.empty();
        }
        return Optional.of(new LogClientInfo(loginUser.getClientId(), loginUser.getClientType(),
            loginUser.getAuthType()));
    }
}

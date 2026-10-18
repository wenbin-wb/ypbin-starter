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
package cn.ypbin.starter.security.core;

import cn.ypbin.starter.core.util.LogSanitizer;
import java.util.List;
import java.util.OptionalLong;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 权限数据源抽象基类：把「Sa-Token loginId → 业务 userId」的解析口径与
 * 「数据源缺失即拒绝（fail-closed）」的骨架收敛到框架侧。
 *
 * <p><strong>为什么需要它</strong>：{@link PermissionProvider} 的宿主实现第一段代码永远是同一个
 * ——把 Sa-Token 的 {@code loginId}（{@code Object}，本仓约定为 userId 的十进制字符串）解析成
 * {@code Long}，非法则记 WARN 并返回空集合。这段是<strong>框架契约</strong>而非业务语义，
 * 各服务各抄一份后，口径变更（例如将来 loginId 支持 UUID / 复合键）要逐处修改，漏一处即
 * 「该服务权限恒空、403」的静默失效。</p>
 *
 * <p>继承本类后宿主只实现两个数据源方法 {@link #permissionsOf(long)} / {@link #rolesOf(long)}：</p>
 *
 * <pre>{@code
 * @Component
 * public class MyPermissionProvider extends AbstractPermissionProvider {
 *     @Override
 *     protected List<String> permissionsOf(long userId) {
 *         return permissionService.listByUserId(userId);
 *     }
 *
 *     @Override
 *     protected List<String> rolesOf(long userId) {
 *         return roleService.listCodesByUserId(userId);
 *     }
 * }
 * }</pre>
 *
 * <p><strong>失败语义（fail-closed，禁静默降级）</strong>：</p>
 * <ul>
 *     <li>loginId 为 {@code null} / 非数字 / 空白包裹的数字无法解析 ⇒ 记 WARN 并返回 {@link OptionalLong#empty()}，
 *     不发起任何数据源调用（拒绝，且错误可见）；</li>
 *     <li>数据源返回 {@code null} ⇒ 记 ERROR 并返回空集合（拒绝）。</li>
 * </ul>
 * <p>数据源<strong>自身抛出的异常不在此捕获</strong>：远程不可达等故障应显式上抛（由鉴权链路拒绝），
 * 不能被本类伪装成「空权限」——否则「服务故障」与「确实无权限」将无法区分。</p>
 *
 * <p><strong>兼容性</strong>：本类为新增类型，不修改 {@link PermissionProvider} 的任何签名，
 * 既有实现类可继续直接实现接口；需要虚拟主体等特殊分支的宿主仍可在自己的实现里覆写
 * {@link #getPermissions(Object, String)} / {@link #getRoles(Object, String)}。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
public abstract class AbstractPermissionProvider implements PermissionProvider {

    private static final Logger log = LoggerFactory.getLogger(AbstractPermissionProvider.class);

    /** 统一日志前缀，便于宿主按模块检索鉴权失败的暴露点 */
    private static final String LOG_PREFIX = "[ypbin-starter] ";

    /**
     * 把 Sa-Token 的 loginId 解析为业务 userId。
     *
     * <p>解析口径：{@code null} = 缺少账号；其余一律取 {@link Object#toString()} 后
     * {@link String#trim()} 再按十进制 {@code long} 解析（因此带首尾空白的数字视为合法）。
     * 无法解析时记 WARN 并返回空，由调用方转成「拒绝」。</p>
     *
     * @param loginId   登录账号标识（Sa-Token 传入，可能为 {@code null}）
     * @param loginType 账号体系类型（仅用于日志定位）
     * @return 用户 ID；无法解析时为 {@link OptionalLong#empty()}
     */
    protected final OptionalLong userIdOf(@Nullable Object loginId, @Nullable String loginType) {
        if (loginId == null) {
            log.warn(LOG_PREFIX + "权限查询缺少 loginId，按无权限处理：loginType={}", loginType);
            return OptionalLong.empty();
        }
        // loginId 可能来自会话/令牌等外部输入，写日志前先做日志注入脱敏
        String raw = LogSanitizer.sanitize(loginId);
        try {
            return OptionalLong.of(Long.parseLong(loginId.toString().trim()));
        } catch (NumberFormatException ex) {
            log.warn(LOG_PREFIX + "权限查询的 loginId 不是合法用户 ID，按无权限处理：loginType={}, loginId={}",
                loginType, raw);
            return OptionalLong.empty();
        }
    }

    /**
     * 获取账号的权限码集合：解析 userId → 查数据源 → 数据源返回 {@code null} 时记 ERROR 并按空集合拒绝。
     *
     * @param loginId   登录账号标识
     * @param loginType 账号体系类型
     * @return 权限码列表；无法确定身份或数据源缺失时为空集合（拒绝），永不为 {@code null}
     */
    @Override
    public List<String> getPermissions(Object loginId, String loginType) {
        OptionalLong userId = userIdOf(loginId, loginType);
        if (userId.isEmpty()) {
            return List.of();
        }
        return denyOnMissingResult(permissionsOf(userId.getAsLong()), "权限码", userId.getAsLong());
    }

    /**
     * 获取账号的角色码集合：解析 userId → 查数据源 → 数据源返回 {@code null} 时记 ERROR 并按空集合拒绝。
     *
     * @param loginId   登录账号标识
     * @param loginType 账号体系类型
     * @return 角色码列表；无法确定身份或数据源缺失时为空集合（拒绝），永不为 {@code null}
     */
    @Override
    public List<String> getRoles(Object loginId, String loginType) {
        OptionalLong userId = userIdOf(loginId, loginType);
        if (userId.isEmpty()) {
            return List.of();
        }
        return denyOnMissingResult(rolesOf(userId.getAsLong()), "角色码", userId.getAsLong());
    }

    /**
     * 查询指定用户 ID 的权限码（数据源方法，由宿主实现）。
     *
     * <p>数据源故障应<strong>直接抛出</strong>，不要返回 {@code null} 或空集合掩盖；
     * 仅当「查询成功但确实没有数据」时才返回空集合。返回 {@code null} 会被基类视为数据源故障。</p>
     *
     * @param userId 用户 ID
     * @return 权限码列表，可为 {@code null}（视为数据源异常，按拒绝处理）
     */
    protected abstract @Nullable List<String> permissionsOf(long userId);

    /**
     * 查询指定用户 ID 的角色码（数据源方法，由宿主实现）。
     *
     * <p>语义与 {@link #permissionsOf(long)} 一致：故障上抛，只有「成功但无数据」才返回空集合。</p>
     *
     * @param userId 用户 ID
     * @return 角色码列表，可为 {@code null}（视为数据源异常，按拒绝处理）
     */
    protected abstract @Nullable List<String> rolesOf(long userId);

    /**
     * 数据源返回 {@code null} 时的统一拒绝语义：记 ERROR（不静默）并返回空集合。
     *
     * @param codes  数据源返回值
     * @param kind   集合语义（权限码 / 角色码，仅用于日志）
     * @param userId 用户 ID（仅用于日志）
     * @return 非 {@code null} 的集合
     */
    private static List<String> denyOnMissingResult(@Nullable List<String> codes, String kind, long userId) {
        if (codes == null) {
            log.error(LOG_PREFIX + "{}查询结果缺失（数据源返回 null），按拒绝处理（不授予任何{}）：userId={}",
                kind, kind, userId);
            return List.of();
        }
        return codes;
    }
}

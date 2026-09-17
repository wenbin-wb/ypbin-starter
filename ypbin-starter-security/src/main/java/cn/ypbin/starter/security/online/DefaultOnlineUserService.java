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
package cn.ypbin.starter.security.online;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.session.SaTerminalInfo;
import cn.dev33.satoken.stp.StpUtil;
import cn.ypbin.starter.security.core.LoginUser;
import cn.ypbin.starter.security.core.UserContext;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 Sa-Token 的在线用户服务默认实现。
 *
 * <p>通过 {@link StpUtil#searchTokenValue} 枚举所有登录 token（返回的是带前缀的完整键，需截取最后一段得到
 * 真实 token 值），逐个解析登录用户与终端信息，过滤已过期/无效 token。</p>
 *
 * <p>各展示字段来自会话（{@link UserContext#KEY_LOGIN_USER}）与终端扩展信息
 * （{@link OnlineUserHelper#KEY_TERMINAL}），读取失败时保留该条在线记录并记录 WARN 与完整堆栈，绝不
 * 静默降级——字段变空必须能从日志定位（见 {@link OnlineUserHelper} 关于 Sa-Token 会话多态反序列化
 * 白名单的说明）。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public class DefaultOnlineUserService implements OnlineUserService {

    private static final Logger log = LoggerFactory.getLogger(DefaultOnlineUserService.class);

    @Override
    public List<OnlineUser> list() {
        return list(null);
    }

    @Override
    public List<OnlineUser> list(@Nullable String keyword) {
        List<String> tokenKeys = StpUtil.searchTokenValue("", 0, -1, false);
        List<OnlineUser> result = new ArrayList<>();
        for (String tokenKey : tokenKeys) {
            String token = extractToken(tokenKey);
            OnlineUser user = resolve(token);
            if (user == null) {
                continue;
            }
            if (matchKeyword(user, keyword)) {
                result.add(user);
            }
        }
        result.sort(Comparator.comparing(OnlineUser::getLoginTime,
            Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    @Override
    public List<OnlineUser> listByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }
        List<String> tokens = StpUtil.getTokenValueListByLoginId(userId);
        List<OnlineUser> result = new ArrayList<>();
        for (String token : tokens) {
            OnlineUser user = resolve(token);
            if (user != null) {
                result.add(user);
            }
        }
        result.sort(Comparator.comparing(OnlineUser::getLoginTime,
            Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    @Override
    public long count() {
        return list(null).size();
    }

    @Override
    public void kickoutByToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        StpUtil.kickoutByTokenValue(token);
    }

    @Override
    public void kickoutByUserId(Long userId) {
        if (userId == null || !StpUtil.isLogin(userId)) {
            return;
        }
        StpUtil.kickout(userId);
    }

    /**
     * 从 searchTokenValue 返回的完整键中截取真实 token 值（取最后一个冒号之后）。
     */
    @Nullable
    private String extractToken(String tokenKey) {
        if (tokenKey == null) {
            return null;
        }
        int idx = tokenKey.lastIndexOf(':');
        return idx < 0 ? tokenKey : tokenKey.substring(idx + 1);
    }

    /**
     * 解析单个 token 为在线用户；token 已过期或无对应登录 ID 时返回 {@code null}。
     */
    @Nullable
    private OnlineUser resolve(@Nullable String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        Object loginId = StpUtil.getLoginIdByToken(token);
        if (loginId == null) {
            return null;
        }
        // 过滤已冻结/过期 token
        long activeTimeout = StpUtil.getStpLogic().getTokenActiveTimeoutByToken(token);
        if (activeTimeout < SaTokenDao.NEVER_EXPIRE) {
            return null;
        }

        OnlineUser online = new OnlineUser();
        online.setToken(token);
        online.setUserId(parseUserId(loginId));

        // 业务展示字段：来自登录时写入会话的 LoginUser
        LoginUser loginUser = readLoginUser(loginId);
        if (loginUser != null) {
            online.setUsername(loginUser.getUsername());
            online.setNickname(loginUser.getNickname());
            online.setTenantId(loginUser.getTenantId());
            online.setClientId(loginUser.getClientId());
        }

        // 设备类型：来自 Sa-Token 终端信息
        try {
            SaTerminalInfo terminalInfo = StpUtil.getStpLogic().getTerminalInfoByToken(token);
            online.setDeviceType(terminalInfo == null ? null : terminalInfo.getDeviceType());
        } catch (Exception e) {
            log.warn("[ypbin-starter] 在线用户设备类型（Sa-Token 终端信息）读取失败，该用户的 deviceType 将显示为空，"
                + "token 已脱敏", e);
        }

        // IP/浏览器/OS/登录时间：来自 OnlineUserHelper 记录的终端扩展信息
        OnlineUserHelper.Terminal terminal = readTerminal(token);
        if (terminal != null) {
            online.setIp(terminal.getIp());
            online.setLocation(terminal.getLocation());
            online.setBrowser(terminal.getBrowser());
            online.setOs(terminal.getOs());
            online.setLoginTime(terminal.resolveLoginTime());
        }
        if (online.getLoginTime() == null) {
            online.setLoginTime(tokenCreateTime(token));
        }
        return online;
    }

    @Nullable
    private LoginUser readLoginUser(Object loginId) {
        try {
            SaSession session = StpUtil.getSessionByLoginId(loginId, false);
            if (session == null) {
                return null;
            }
            Object value = session.get(UserContext.KEY_LOGIN_USER);
            if (value == null || value instanceof LoginUser) {
                return (LoginUser) value;
            }
            // 值存在但不是 LoginUser（退化为 Map / 未知类型）：与终端信息同一族失败，过去静默返回 null
            // 会让「用户名/昵称全为空」无从排查，故必须留痕并给出实际类名
            log.warn("[ypbin-starter] 在线用户会话中的登录用户类型不可识别（期望 {}，实际 {}），该用户的"
                + "用户名/昵称/租户/客户端将显示为空；请确认该类型已登记在 META-INF/satoken/sa-json-type.list",
                LoginUser.class.getName(), value.getClass().getName());
            return null;
        } catch (Exception e) {
            log.warn("[ypbin-starter] 在线用户会话读取失败，该用户的用户名/昵称/租户/客户端将显示为空，loginId={}",
                loginId, e);
            return null;
        }
    }

    /**
     * 读取 token 的终端扩展信息。
     *
     * <p>失败（含 Token-Session 反序列化异常，如 Sa-Token 会话多态白名单未登记终端类型）会让该用户的
     * IP/浏览器/操作系统/登录时间全部为空，属必须可见的失败，故记录 WARN 与完整堆栈。</p>
     */
    private OnlineUserHelper.@Nullable Terminal readTerminal(String token) {
        try {
            return OnlineUserHelper.getByToken(token);
        } catch (Exception e) {
            log.warn("[ypbin-starter] 在线用户终端信息读取失败（Token-Session 反序列化异常），该用户的 "
                + "IP/浏览器/操作系统/登录时间将显示为空，token 已脱敏", e);
            return null;
        }
    }

    @Nullable
    private LocalDateTime tokenCreateTime(String token) {
        try {
            SaSession tokenSession = StpUtil.getTokenSessionByToken(token);
            long createTime = tokenSession.getCreateTime();
            if (createTime > 0) {
                return Instant.ofEpochMilli(createTime).atZone(ZoneId.systemDefault()).toLocalDateTime();
            }
        } catch (Exception e) {
            log.warn("[ypbin-starter] 在线用户 token 创建时间（登录时间兜底值）读取失败，该用户的登录时间将显示为空，"
                + "token 已脱敏", e);
        }
        return null;
    }

    @Nullable
    private Long parseUserId(Object loginId) {
        try {
            return Long.valueOf(loginId.toString());
        } catch (NumberFormatException e) {
            // 宿主自定义的登录 ID 未必是数字（本 starter 场景为 Long）；属「可预期的形态差异」而非失败：
            // 该 token 记录仍照常返回，仅 userId 留空，且失败在响应里直接可见（前端 userId 为空）。
            // 故保持 debug，不与上面三处「读取失败」同级——那三处会让整批展示字段变空且外部不可见。
            log.debug("[ypbin-starter] 登录 ID 非数字，userId 留空：loginId={}", loginId);
            return null;
        }
    }

    private boolean matchKeyword(OnlineUser user, @Nullable String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String kw = keyword.toLowerCase(Locale.ROOT);
        return contains(user.getUsername(), kw) || contains(user.getNickname(), kw);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }
}

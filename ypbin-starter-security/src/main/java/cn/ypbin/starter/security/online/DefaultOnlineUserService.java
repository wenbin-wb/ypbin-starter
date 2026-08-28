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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 Sa-Token 的在线用户服务默认实现。
 *
 * <p>通过 {@link StpUtil#searchTokenValue} 枚举所有登录 token（返回的是带前缀的完整键，需截取最后一段得到
 * 真实 token 值），逐个解析登录用户与终端信息，过滤已过期/无效 token。</p>
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
    public List<OnlineUser> list(String keyword) {
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
    private OnlineUser resolve(String token) {
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
            online.setDeviceType(StpUtil.getStpLogic().getTerminalInfoByToken(token) == null
                ? null : StpUtil.getStpLogic().getTerminalInfoByToken(token).getDeviceType());
        } catch (Exception e) {
            // 终端信息不可用时跳过该字段，但记录日志便于排查
            log.debug("[ypbin-starter] 在线用户终端信息读取失败，token 已脱敏", e);
        }

        // IP/浏览器/OS/登录时间：来自 OnlineUserHelper 记录的终端扩展信息
        OnlineUserHelper.Terminal terminal = readTerminal(token);
        if (terminal != null) {
            online.setIp(terminal.getIp());
            online.setLocation(terminal.getLocation());
            online.setBrowser(terminal.getBrowser());
            online.setOs(terminal.getOs());
            online.setLoginTime(terminal.getLoginTime());
        }
        if (online.getLoginTime() == null) {
            online.setLoginTime(tokenCreateTime(token));
        }
        return online;
    }

    private LoginUser readLoginUser(Object loginId) {
        try {
            SaSession session = StpUtil.getSessionByLoginId(loginId, false);
            if (session == null) {
                return null;
            }
            Object value = session.get(UserContext.KEY_LOGIN_USER);
            return (value instanceof LoginUser user) ? user : null;
        } catch (Exception e) {
            log.debug("[ypbin-starter] 在线用户会话读取失败，loginId={}", loginId, e);
            return null;
        }
    }

    private OnlineUserHelper.Terminal readTerminal(String token) {
        try {
            return OnlineUserHelper.getByToken(token);
        } catch (Exception e) {
            log.debug("[ypbin-starter] 在线用户终端扩展信息读取失败，token 已脱敏", e);
            return null;
        }
    }

    private LocalDateTime tokenCreateTime(String token) {
        try {
            SaSession tokenSession = StpUtil.getTokenSessionByToken(token);
            long createTime = tokenSession.getCreateTime();
            if (createTime > 0) {
                return Instant.ofEpochMilli(createTime).atZone(ZoneId.systemDefault()).toLocalDateTime();
            }
        } catch (Exception e) {
            // 无法取创建时间时返回 null，记录日志便于排查
            log.debug("[ypbin-starter] 在线用户 token 创建时间读取失败，token 已脱敏", e);
        }
        return null;
    }

    private Long parseUserId(Object loginId) {
        try {
            return Long.valueOf(loginId.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean matchKeyword(OnlineUser user, String keyword) {
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

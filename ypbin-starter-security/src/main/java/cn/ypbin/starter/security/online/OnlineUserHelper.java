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

import cn.dev33.satoken.stp.StpUtil;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 在线用户终端信息记录门面。
 *
 * <p>登录成功后调用 {@link #record}，把 IP、浏览器、操作系统、登录时间等展示信息写入当前登录 token 的
 * Token-Session，供 {@link OnlineUserService} 枚举在线用户时读取。不记录也不影响在线用户的基本枚举
 * （只是 IP/浏览器等字段为空）。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public final class OnlineUserHelper {

    /** Token-Session 中存终端信息的键 */
    public static final String KEY_TERMINAL = "ypbin:onlineTerminal";

    private OnlineUserHelper() {
    }

    /**
     * 记录当前登录 token 的终端信息（登录成功后调用）。
     *
     * @param terminal 终端信息
     */
    public static void record(Terminal terminal) {
        if (terminal == null) {
            return;
        }
        if (terminal.getLoginTime() == null) {
            terminal.setLoginTime(LocalDateTime.now());
        }
        StpUtil.getTokenSession().set(KEY_TERMINAL, terminal);
    }

    /**
     * 记录当前登录 token 的终端信息。
     *
     * @param ip      登录 IP
     * @param browser 浏览器
     * @param os      操作系统
     */
    public static void record(String ip, String browser, String os) {
        Terminal terminal = new Terminal();
        terminal.setIp(ip);
        terminal.setBrowser(browser);
        terminal.setOs(os);
        terminal.setLoginTime(LocalDateTime.now());
        record(terminal);
    }

    /**
     * 读取指定 token 的终端信息。
     *
     * @param token 令牌值
     * @return 终端信息，未记录时为 {@code null}
     */
    public static Terminal getByToken(String token) {
        Object value = StpUtil.getTokenSessionByToken(token).get(KEY_TERMINAL);
        return (value instanceof Terminal terminal) ? terminal : null;
    }

    /**
     * 登录终端信息。
     *
     * @author wenbin
     * @since 2026-08-01
     */
    public static class Terminal implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 登录 IP */
        private String ip;

        /** IP 归属地 */
        private String location;

        /** 浏览器 */
        private String browser;

        /** 操作系统 */
        private String os;

        /** 登录时间 */
        private LocalDateTime loginTime;

        public String getIp() {
            return ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }

        public String getBrowser() {
            return browser;
        }

        public void setBrowser(String browser) {
            this.browser = browser;
        }

        public String getOs() {
            return os;
        }

        public void setOs(String os) {
            this.os = os;
        }

        public LocalDateTime getLoginTime() {
            return loginTime;
        }

        public void setLoginTime(LocalDateTime loginTime) {
            this.loginTime = loginTime;
        }
    }
}

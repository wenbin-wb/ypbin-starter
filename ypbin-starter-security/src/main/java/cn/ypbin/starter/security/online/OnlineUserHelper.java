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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 在线用户终端信息记录门面。
 *
 * <p>登录成功后调用 {@link #record}，把 IP、浏览器、操作系统、登录时间等展示信息写入当前登录 token 的
 * Token-Session，供 {@link OnlineUserService} 枚举在线用户时读取。不记录也不影响在线用户的基本枚举
 * （只是 IP/浏览器等字段为空）。</p>
 *
 * <p><strong>存储形态：</strong>登录时间以「epoch 毫秒 + 文本」两种与 Jackson/时间类型配置无关的形态存储
 * （{@link Terminal#getLoginTimeMillis()} / {@link Terminal#getLoginTimeText()}），避免会话序列化器
 * （Sa-Token 可用 Jackson 2 / Jackson 3 / 其它实现）对 {@code LocalDateTime} 的读写形态不一致导致
 * 整条 Session 反序列化失败；同时仍写一份历史形态字段 {@link Terminal#getLoginTime()}，让未升级的读取方
 * 也能读到登录时间。旧数据（只有历史形态字段）由 {@link Terminal#resolveLoginTime()} 解析，不会因形态
 * 变化而变空。</p>
 *
 * <p><strong>会话反序列化白名单：</strong>{@link Terminal} 存在 Sa-Token 的 Token-Session 中，属于多态
 * 反序列化目标，必须登记在 {@code META-INF/satoken/sa-json-type.list}，否则 Sa-Token 读取会话时会拒绝该
 * 类型（写侧不受影响，表现为「Redis 里数据完全正常、读出来却全为空」）。新增本包内写入会话的类型时，
 * 必须同步登记该文件。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public final class OnlineUserHelper {

    /** Token-Session 中存终端信息的键 */
    public static final String KEY_TERMINAL = "ypbin:onlineTerminal";

    /** 历史形态登录时间字段名（会话 JSON 中的键，值为 ISO 字符串） */
    public static final String FIELD_LOGIN_TIME = "loginTime";

    /** 新形态登录时间字段名：epoch 毫秒 */
    public static final String FIELD_LOGIN_TIME_MILLIS = "loginTimeMillis";

    /** 新形态登录时间字段名：可读文本 */
    public static final String FIELD_LOGIN_TIME_TEXT = "loginTimeText";

    private static final Logger log = LoggerFactory.getLogger(OnlineUserHelper.class);

    /** 历史形态之一：Java 序列化/Jackson 默认形态（ISO-8601，可带纳秒） */
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /** 历史形态之二：项目统一展示格式 */
    private static final DateTimeFormatter TEXT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 历史形态之三：带毫秒的项目展示格式 */
    private static final DateTimeFormatter TEXT_MILLIS_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /** 历史形态之四：带时区偏移的 ISO-8601 */
    private static final DateTimeFormatter ISO_OFFSET_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    /** 逐项尝试的历史登录时间形态（顺序仅影响命中快慢，互不重叠） */
    private static final List<DateTimeFormatter> LEGACY_LOGIN_TIME_FORMATTERS =
        List.of(ISO_FORMATTER, TEXT_FORMATTER, TEXT_MILLIS_FORMATTER, ISO_OFFSET_FORMATTER);

    /** 新写入的历史形态字段文本格式（固定带秒与毫秒，避免 LocalDateTime.toString() 省略秒的歧义） */
    private static final DateTimeFormatter LEGACY_WRITE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    /** 退化形态下的字段名：登录 IP */
    private static final String FIELD_IP = "ip";

    /** 退化形态下的字段名：IP 归属地 */
    private static final String FIELD_LOCATION = "location";

    /** 退化形态下的字段名：浏览器 */
    private static final String FIELD_BROWSER = "browser";

    /** 退化形态下的字段名：操作系统 */
    private static final String FIELD_OS = "os";

    /** epoch 毫秒形态判定（10~19 位纯数字） */
    private static final Pattern EPOCH_MILLIS_PATTERN = Pattern.compile("\\d{10,19}");

    private OnlineUserHelper() {
    }

    /**
     * 记录当前登录 token 的终端信息（登录成功后调用）。
     *
     * @param terminal 终端信息
     */
    public static void record(@Nullable Terminal terminal) {
        if (terminal == null) {
            return;
        }
        terminal.fillLoginTimeIfAbsent();
        StpUtil.getTokenSession().set(KEY_TERMINAL, terminal);
    }

    /**
     * 记录当前登录 token 的终端信息。
     *
     * @param ip      登录 IP
     * @param browser 浏览器
     * @param os      操作系统
     */
    public static void record(String ip, @Nullable String browser, @Nullable String os) {
        Terminal terminal = new Terminal();
        terminal.setIp(ip);
        terminal.setBrowser(browser);
        terminal.setOs(os);
        record(terminal);
    }

    /**
     * 读取指定 token 的终端信息。
     *
     * <p>会话读取本身失败（如 Session 反序列化异常）会向上抛出，由调用方记录 WARN 暴露，不在此吞掉。</p>
     *
     * @param token 令牌值
     * @return 终端信息，未记录时为 {@code null}
     */
    @Nullable
    public static Terminal getByToken(String token) {
        Object value = StpUtil.getTokenSessionByToken(token).get(KEY_TERMINAL);
        return toTerminal(value);
    }

    /**
     * 把会话中取出的原始值兼容地转换为 {@link Terminal}。
     *
     * <p>取出的值可能不是 {@link Terminal}——典型是退化成 {@code Map}（如 {@code LinkedHashMap}）：当会话
     * JSON 里该键所在层级缺少 {@code @class} 类型信息（由不写类型信息的序列化器写入，或历史数据形态差异）
     * 时就会出现。此时按「能取多少取多少」逐字段解析，缺失字段留空，并记录 WARN 说明实际类型——绝不因为
     * 形态异常让整条在线用户记录丢失。</p>
     *
     * <p>注意区分另一种失败：Sa-Token 会话多态白名单缺少 {@code Terminal}（见
     * {@code META-INF/satoken/sa-json-type.list}）时，读侧在类型校验阶段**直接抛异常**，不会退化成
     * {@code Map}，由调用方（{@link DefaultOnlineUserService}）记录 WARN 暴露。</p>
     *
     * @param value 会话中的原始值，可空
     * @return 终端信息；值为空或类型完全不可识别时为 {@code null}
     */
    @Nullable
    public static Terminal toTerminal(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Terminal terminal) {
            return terminal;
        }
        String actualType = value.getClass().getName();
        if (value instanceof Map<?, ?> map) {
            Terminal terminal = fromMap(map);
            log.warn("[ypbin-starter] 在线用户终端信息反序列化形态异常：期望 {}，实际 {}。已按能取多少取多少"
                    + "降级解析（缺失字段留空）。若登录时间/IP 等字段缺失，请确认 {} 已登记在"
                    + " META-INF/satoken/sa-json-type.list（Sa-Token 会话多态反序列化白名单）。",
                Terminal.class.getName(), actualType, Terminal.class.getName());
            return terminal;
        }
        log.warn("[ypbin-starter] 在线用户终端信息类型不可识别：期望 {}，实际 {}。该用户的 IP/浏览器/操作系统/"
                + "登录时间将显示为空。", Terminal.class.getName(), actualType);
        return null;
    }

    /**
     * 从退化形态（{@code Map}）中逐字段还原终端信息。
     */
    private static Terminal fromMap(Map<?, ?> map) {
        Terminal terminal = new Terminal();
        String ip = asString(map.get(FIELD_IP));
        if (ip != null) {
            terminal.setIp(ip);
        }
        String location = asString(map.get(FIELD_LOCATION));
        if (location != null) {
            terminal.setLocation(location);
        }
        String browser = asString(map.get(FIELD_BROWSER));
        if (browser != null) {
            terminal.setBrowser(browser);
        }
        String os = asString(map.get(FIELD_OS));
        if (os != null) {
            terminal.setOs(os);
        }
        Object millis = map.get(FIELD_LOGIN_TIME_MILLIS);
        if (millis instanceof Number number) {
            terminal.setLoginTimeMillis(number.longValue());
        } else if (millis != null) {
            terminal.setLoginTimeMillis(parseEpochMillis(String.valueOf(millis)));
        }
        terminal.setLoginTimeText(asString(map.get(FIELD_LOGIN_TIME_TEXT)));
        Object legacy = map.get(FIELD_LOGIN_TIME);
        if (legacy instanceof LocalDateTime dateTime) {
            terminal.setLoginTimeMillis(toEpochMillis(dateTime));
        }
        terminal.setLoginTime(asString(legacy));
        return terminal;
    }

    @Nullable
    private static String asString(@Nullable Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 解析登录时间文本（兼容历史 ISO 形态、项目展示格式与 epoch 毫秒字符串）。
     *
     * @param text 登录时间文本，可空
     * @return 解析结果，无法解析时为 {@code null}
     */
    @Nullable
    public static LocalDateTime parseLoginTimeText(@Nullable String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String value = text.trim();
        if (EPOCH_MILLIS_PATTERN.matcher(value).matches()) {
            return toLocalDateTime(parseEpochMillis(value));
        }
        for (DateTimeFormatter formatter : LEGACY_LOGIN_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (DateTimeParseException e) {
                // 该历史形态不匹配：继续尝试下一种；全部失败时由本方法末尾统一 WARN，不在此静默返回
            }
        }
        log.warn("[ypbin-starter] 在线用户登录时间无法解析（数据形态超出已知历史形态范围），该字段留空：value={}",
            value);
        return null;
    }

    @Nullable
    private static Long parseEpochMillis(String value) {
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Nullable
    private static LocalDateTime toLocalDateTime(@Nullable Long epochMillis) {
        return epochMillis == null ? null
            : Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    private static long toEpochMillis(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * 登录终端信息。
     *
     * <p>登录时间同时保存 epoch 毫秒与可读文本（均与时间类型序列化配置无关），并额外写一份历史形态字段
     * {@code loginTime}（ISO 字符串）供未升级的读取方使用；{@link #resolveLoginTime()} 统一给出登录时间。</p>
     *
     * @author wenbin
     * @since 2026-08-01
     */
    // 字段由配置绑定 / setter / 映射逐项填充（构造后才赋值），属数据装配语义，故按类抑制 NullAway.Init
    @SuppressWarnings("NullAway.Init")
    public static class Terminal implements Serializable {

        // 会话存储形态为 JSON（Sa-Token SaJsonTemplate），Java 序列化并非兼容性边界；保持不变以免
        // 人为制造与历史字节流的不兼容（真实兼容策略见下方 loginTime 字段说明）
        @Serial
        private static final long serialVersionUID = 1L;

        /** 登录 IP */
        @Nullable
        private String ip;

        /** IP 归属地 */
        @Nullable
        private String location;

        /** 浏览器 */
        @Nullable
        private String browser;

        /** 操作系统 */
        @Nullable
        private String os;

        /** 登录时间（epoch 毫秒）：与 Jackson/时间类型配置无关的存储形态 */
        @Nullable
        private Long loginTimeMillis;

        /** 登录时间文本（{@code yyyy-MM-dd HH:mm:ss}）：与 Jackson/时间类型配置无关的存储形态 */
        @Nullable
        private String loginTimeText;

        /** 历史形态登录时间（ISO 字符串）：兼容旧数据的读与未升级读取方的写 */
        @Nullable
        private String loginTime;

        /**
         * 补齐登录时间（仅在三种形态都缺失时按当前时间写入）。
         */
        void fillLoginTimeIfAbsent() {
            if (loginTimeMillis != null || loginTimeText != null || loginTime != null) {
                return;
            }
            long now = System.currentTimeMillis();
            this.loginTimeMillis = now;
            this.loginTimeText = TEXT_FORMATTER.format(toLocalDateTime(now));
            this.loginTime = LEGACY_WRITE_FORMATTER.format(toLocalDateTime(now));
        }

        /**
         * 取出生效的登录时间：优先 epoch 毫秒，其次文本，最后兼容解析历史形态字段。
         *
         * @return 登录时间；三种形态都取不到时为 {@code null}
         */
        @Nullable
        public LocalDateTime resolveLoginTime() {
            LocalDateTime fromMillis = toLocalDateTime(loginTimeMillis);
            if (fromMillis != null) {
                return fromMillis;
            }
            LocalDateTime fromText = parseLoginTimeText(loginTimeText);
            return fromText != null ? fromText : parseLoginTimeText(loginTime);
        }

        @Nullable
        public String getIp() {
            return ip;
        }

        public void setIp(@Nullable String ip) {
            this.ip = ip;
        }

        @Nullable
        public String getLocation() {
            return location;
        }

        public void setLocation(@Nullable String location) {
            this.location = location;
        }

        @Nullable
        public String getBrowser() {
            return browser;
        }

        public void setBrowser(@Nullable String browser) {
            this.browser = browser;
        }

        @Nullable
        public String getOs() {
            return os;
        }

        public void setOs(@Nullable String os) {
            this.os = os;
        }

        @Nullable
        public Long getLoginTimeMillis() {
            return loginTimeMillis;
        }

        public void setLoginTimeMillis(@Nullable Long loginTimeMillis) {
            this.loginTimeMillis = loginTimeMillis;
        }

        @Nullable
        public String getLoginTimeText() {
            return loginTimeText;
        }

        public void setLoginTimeText(@Nullable String loginTimeText) {
            this.loginTimeText = loginTimeText;
        }

        @Nullable
        public String getLoginTime() {
            return loginTime;
        }

        public void setLoginTime(@Nullable String loginTime) {
            this.loginTime = loginTime;
        }
    }
}

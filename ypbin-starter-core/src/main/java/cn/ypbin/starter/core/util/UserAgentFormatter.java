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
package cn.ypbin.starter.core.util;

import cn.hutool.http.useragent.UserAgent;
import cn.hutool.http.useragent.UserAgentUtil;
import org.jspecify.annotations.Nullable;

/**
 * User-Agent 解析与格式化工具。
 *
 * <p><b>为什么需要它</b>（2026-10-19 反哺自 {@code ypbin-admin}/{@code ypbin-iot}）：
 * 「把 UA 格式化成『浏览器 版本』」这段逻辑此前存在<b>三份</b>——
 * {@code LogCollector} 里一份私有实现（操作日志的 {@code Include.CLIENT} 采集项），
 * 以及 admin/iot 的 {@code LoginSupport} 各抄一份（登录收尾写终端信息，注释还写着
 * 「与 starter 操作日志的写法保持一致」——靠注释维持一致性正是要消除的形态）。
 * UA 解析口径属**通用能力**（与业务无关），故上收到此，三处共用。</p>
 *
 * <p><b>口径</b>：版本缺失（null / 空白 / {@code "Unknown"}）时只留浏览器名称；
 * 浏览器信息缺失时返回 {@code null}。</p>
 *
 * @author wenbin
 * @since 2026-10-19
 */
public final class UserAgentFormatter {

    /** hutool 解析不出具体浏览器版本时的占位值。 */
    private static final String UNKNOWN_VERSION = "Unknown";

    private UserAgentFormatter() {
    }

    /**
     * 解析 User-Agent 字符串。
     *
     * @param userAgent 原始 UA（可为 {@code null} 或空白）
     * @return 解析结果；入参为空时返回 {@code null}
     */
    @Nullable
    public static UserAgent parse(@Nullable String userAgent) {
        return userAgent == null || userAgent.isBlank() ? null : UserAgentUtil.parse(userAgent);
    }

    /**
     * 格式化为「浏览器名称 + 版本」；版本缺失时只留名称。
     *
     * @param userAgent 解析结果（可为 {@code null}）
     * @return 格式化文本；浏览器信息缺失时返回 {@code null}
     */
    @Nullable
    public static String formatBrowser(@Nullable UserAgent userAgent) {
        if (userAgent == null || userAgent.getBrowser() == null) {
            return null;
        }
        String name = userAgent.getBrowser().getName();
        String version = userAgent.getVersion();
        if (version == null || version.isBlank() || UNKNOWN_VERSION.equalsIgnoreCase(version)) {
            return name;
        }
        return name + " " + version;
    }

    /**
     * 解析并格式化的便捷方法（等价于 {@code formatBrowser(parse(ua))}）。
     *
     * @param userAgentHeader 原始 UA 头（可为 {@code null}）
     * @return 格式化文本；无法解析或浏览器信息缺失时返回 {@code null}
     */
    @Nullable
    public static String formatBrowserHeader(@Nullable String userAgentHeader) {
        return formatBrowser(parse(userAgentHeader));
    }
}

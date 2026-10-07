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
package cn.ypbin.starter.iot.validate;

import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * {@code requestId} 形态的<b>唯一</b>口径（下沉自 ypbin-iot：入站幂等键与下行命令实例共用）。
 *
 * <p><b>为什么必须只有一份</b>：同一个 {@code requestId} 会同时出现在两条链路上——设备上报（入站幂等回执）
 * 与平台下发（命令实例）；两处各写一套正则，早晚变成"入站放行、下行拒绝"这类只在特定报文上暴露的故障。
 * 口径本身与 {@link PropertyIdRules} 同字符集（字母数字与 {@code _ . : -}），便于规则引擎侧拼接。</p>
 *
 * @author wenbin
 * @since 3.8.0
 */
public final class RequestIdRules {

    /** 长度上限（与 {@code iot_mqtt_ingest_receipt.request_id} / {@code iot_command_instance.request_id} 列宽一致）。 */
    public static final int MAX_LENGTH = 64;

    /** 形态：字母/数字/下划线/点/冒号/连字符，长度 1~{@value #MAX_LENGTH}。 */
    public static final String PATTERN = "[A-Za-z0-9_.:-]{1," + MAX_LENGTH + "}";

    private static final Pattern COMPILED = Pattern.compile(PATTERN);

    private RequestIdRules() {
    }

    /**
     * 判断 requestId 形态是否合法（{@code null}/空白/超长/含其它字符一律非法）。
     *
     * @param requestId 请求 ID
     * @return 合法返回 {@code true}
     */
    public static boolean isValid(@Nullable String requestId) {
        return requestId != null && COMPILED.matcher(requestId).matches();
    }
}

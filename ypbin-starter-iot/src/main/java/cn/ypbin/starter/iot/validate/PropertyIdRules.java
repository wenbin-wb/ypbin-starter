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
 * 点位标识（{@code propertyId}）的<b>唯一</b>校验口径：字符集白名单 + 长度上限（1~128）。
 * 下沉自 ypbin-iot（原实现随 SQL 字面量防护一起复核）。
 *
 * <p><b>为什么必须只有一份</b>：同一个 {@code propertyId} 会出现在三条路径上——
 * ① 入站读数写 IoTDB 行的 TAG 列与 Redis 最新值的 field；
 * ② 时序查询把它拼进 IoTDB 字面量（驱动对 TAG 列的 {@code ?} 绑定不可用，故必须拼串）；
 * ③ 采集入站的薄适配端点。
 * 若三处各写一套正则，「入站放行、查询拒绝」就会出现「数据写进去了却查不出来」这种看起来像丢数据的
 * 故障。因此本类是唯一实现，各处一律引用它。</p>
 *
 * @author wenbin
 * @since 3.8.0
 */
public final class PropertyIdRules {

    /** 点位标识的最大长度（含），与物模型属性编码的既有约定一致。 */
    public static final int MAX_LENGTH = 128;

    /** 合法形态：字母、数字、下划线、点、冒号、连字符，长度 1~128。 */
    public static final String PATTERN = "[A-Za-z0-9_.:-]{1," + MAX_LENGTH + "}";

    /** 非法时对外/对日志的统一原因（入站拒绝与查询拒绝共用同一句话，避免两处话术漂移）。 */
    public static final String INVALID_MESSAGE =
        "点位标识不合法（只允许字母、数字、下划线、点、冒号、连字符，长度 1~" + MAX_LENGTH + "）";

    /** 预编译：入站一批最多 500 条，逐条 {@code String#matches} 会重复编译同一个正则。 */
    private static final Pattern COMPILED = Pattern.compile(PATTERN);

    private PropertyIdRules() {
    }

    /**
     * 判断点位标识是否合法（{@code null} 非法）。
     *
     * @param propertyId 点位标识
     * @return 合法返回 {@code true}
     */
    public static boolean isValid(@Nullable String propertyId) {
        return propertyId != null && COMPILED.matcher(propertyId).matches();
    }
}

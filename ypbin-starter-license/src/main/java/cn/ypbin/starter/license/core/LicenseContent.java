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
package cn.ypbin.starter.license.core;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * License 授权内容。
 *
 * <p>授权文件解密验签后的业务载体，也是签发时的原始明文。字段覆盖标识、绑定、期限、范围四类：
 * 标识（授权编号、被授权方、供应方备注）、绑定（多机器指纹、租户）、期限（生效/到期时间、宽限天数）、
 * 范围（授权模块、业务额度、自定义参数）。整体经 SM2 签名，任一字段被篡改都会导致验签失败。</p>
 *
 * @param licenseId    授权编号（全局唯一，用于联机校验与吊销）
 * @param subject      被授权方名称
 * @param remark       供应方备注
 * @param fingerprints 允许运行的机器指纹列表（多机器绑定；为空表示不限机器）
 * @param tenantId     绑定租户标识（为空表示不限租户）
 * @param issuedAt     签发时间
 * @param effectiveAt  生效时间（早于此时间视为未生效）
 * @param expireAt     到期时间
 * @param graceDays    过期后的宽限天数（此期间状态为非法可用）
 * @param modules      授权的功能模块标识集合（为空表示不做模块级限制）
 * @param quotas       业务额度限制（如 device=100、user=500；键为业务参数名，值为上限）
 * @param attributes   自定义扩展参数（业务侧自解释的键值对）
 * @author wenbin
 * @since 2026-08-05
 */
public record LicenseContent(
    String licenseId,
    String subject,
    String remark,
    List<String> fingerprints,
    String tenantId,
    LocalDateTime issuedAt,
    LocalDateTime effectiveAt,
    LocalDateTime expireAt,
    int graceDays,
    List<String> modules,
    Map<String, Long> quotas,
    Map<String, String> attributes
) {

    /**
     * 是否绑定了机器指纹。
     *
     * @return {@code true} 表示限定机器运行
     */
    public boolean isMachineBound() {
        return fingerprints != null && !fingerprints.isEmpty();
    }

    /**
     * 给定指纹是否在授权的机器列表内。
     *
     * @param fingerprint 待校验的机器指纹
     * @return 未绑定机器时恒为 {@code true}；否则命中列表才为 {@code true}
     */
    public boolean matchesFingerprint(String fingerprint) {
        if (!isMachineBound()) {
            return true;
        }
        return fingerprints.contains(fingerprint);
    }

    /**
     * 是否授权了指定功能模块。
     *
     * @param module 模块标识
     * @return 未做模块限制时恒为 {@code true}；否则包含该模块才为 {@code true}
     */
    public boolean hasModule(String module) {
        if (modules == null || modules.isEmpty()) {
            return true;
        }
        return modules.contains(module);
    }

    /**
     * 读取某项业务额度上限。
     *
     * @param key 业务参数名
     * @return 额度上限；未配置时返回 {@code null}（表示该项不限）
     */
    public Long quota(String key) {
        return quotas == null ? null : quotas.get(key);
    }

    /**
     * 读取某项自定义扩展参数。
     *
     * @param key 参数名
     * @return 参数值；不存在返回 {@code null}
     */
    public String attribute(String key) {
        return attributes == null ? null : attributes.get(key);
    }
}

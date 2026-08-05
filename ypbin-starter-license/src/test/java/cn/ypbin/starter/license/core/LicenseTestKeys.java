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

import cn.ypbin.starter.tools.crypto.Sm2Utils;
import cn.ypbin.starter.tools.crypto.Sm2Utils.KeyPairBase64;
import cn.ypbin.starter.tools.crypto.Sm4Utils;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 授权测试的密钥与内容构造辅助。
 *
 * @author wenbin
 * @since 2026-08-05
 */
final class LicenseTestKeys {

    final KeyPairBase64 sm2 = Sm2Utils.generateKeyPair();
    final String sm4 = Sm4Utils.generateKeyBase64();

    /**
     * 构造一份基础授权内容。
     *
     * @param effectiveAt 生效时间
     * @param expireAt    到期时间
     * @param graceDays   宽限天数
     * @return 授权内容
     */
    LicenseContent content(LocalDateTime effectiveAt, LocalDateTime expireAt, int graceDays) {
        return new LicenseContent("LIC-0001", "测试被授权方", "单元测试",
            List.of(), null,
            LocalDateTime.now(), effectiveAt, expireAt, graceDays,
            List.of("report", "export"),
            Map.of("device", 100L, "user", 500L),
            Map.of("region", "cn"));
    }

    /**
     * 用当前密钥签发授权串。
     *
     * @param content 授权内容
     * @return Base64 授权串
     */
    String issue(LicenseContent content) {
        return LicenseSigner.issue(content, sm2.privateKey(), sm4);
    }
}

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

/**
 * License 签名信封。
 *
 * <p>封装授权载荷及其 SM2 签名。{@code payload} 为 {@link LicenseContent} 序列化后的 JSON 原文，
 * {@code signature} 是对该原文字节的 SM2 签名。验签直接基于存储的 {@code payload} 字节进行，
 * 不做重序列化，从根本上规避字段顺序/格式差异导致的验签抖动。信封整体再经 SM4 加密后对外分发。</p>
 *
 * @param payload   授权载荷 JSON 原文
 * @param signature Base64 编码的 SM2 签名
 * @author wenbin
 * @since 2026-08-05
 */
public record LicenseEnvelope(String payload, String signature) {
}

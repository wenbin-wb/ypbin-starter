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
 * <p>封装授权载荷及其 SM2 签名。{@code payload} 为 {@link LicenseContent} 序列化 JSON 原文经
 * Deflate 压缩后做 URL 安全 Base64 的编码（v2），{@code signature} 是对该 payload 字符串字节的 SM2
 * 签名。验签直接基于存储的 {@code payload} 字节进行，不做重序列化，从根本上规避字段顺序/格式差异
 * 导致的验签抖动。信封整体再经 SM4 加密后对外分发。</p>
 *
 * <p>{@code version} 标记信封格式版本，为将来格式演进预留机制；version 本身不参与签名，但信封整体
 * 经 SM4-GCM 认证加密，任何篡改都会在解密阶段失败。</p>
 *
 * @param version   信封格式版本（{@link LicenseSigner#ENVELOPE_VERSION}）
 * @param payload   授权载荷：Deflate 压缩后 URL 安全 Base64（无填充）
 * @param signature Base64 编码的 SM2 签名
 * @author wenbin
 * @since 2026-08-05
 */
public record LicenseEnvelope(int version, String payload, String signature) {
}

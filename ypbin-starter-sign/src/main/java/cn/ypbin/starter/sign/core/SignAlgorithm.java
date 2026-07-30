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
package cn.ypbin.starter.sign.core;

/**
 * 签名算法。
 *
 * @author wenbin
 * @since 2026-07-30
 */
public enum SignAlgorithm {

    /** MD5（拼接 appSecret 后摘要，兼容旧系统） */
    MD5,

    /** HMAC-SHA256（以 appSecret 为密钥，防长度扩展攻击，推荐） */
    HMAC_SHA256
}

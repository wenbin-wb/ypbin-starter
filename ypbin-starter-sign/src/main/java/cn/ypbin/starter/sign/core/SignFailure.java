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
 * 验签失败类型：决定对外返回的业务码，避免一律降级成 500「系统内部错误」。
 *
 * <ul>
 *     <li>{@link #UNAUTHORIZED} → 401：凭据缺失、无法识别或已过期，客户端应重新获取签名参数；</li>
 *     <li>{@link #FORBIDDEN} → 403：凭据有效但无权使用（签名不匹配、IP/作用域/配额、重复请求）；</li>
 *     <li>{@link #BAD_REQUEST} → 400：请求本身不合法（时间戳格式错误、JSON 请求体非法）。</li>
 * </ul>
 *
 * @author wenbin
 * @since 2026-11-06
 */
public enum SignFailure {

    /** 凭据缺失/无效/已过期（401） */
    UNAUTHORIZED,

    /** 凭据有效但无权或不匹配（403） */
    FORBIDDEN,

    /** 请求内容不合法（400） */
    BAD_REQUEST
}

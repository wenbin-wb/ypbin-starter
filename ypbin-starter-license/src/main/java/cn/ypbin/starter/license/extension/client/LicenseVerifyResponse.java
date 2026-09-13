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
package cn.ypbin.starter.license.extension.client;

/**
 * 联机校验响应体。
 *
 * <p>对应服务端 {@code {"data":{"valid":true,"reason":"ok"}}}；{@code valid} 用包装类型
 * {@link Boolean} 以区分「明确 true / 明确 false / 字段缺失」三态——缺失必须按「未明确裁决」处理，
 * 不能当作有效或无效。</p>
 *
 * @param data 校验数据；响应缺少 {@code data} 时为 {@code null}
 * @author wenbin
 * @since 2026-09-13
 */
public record LicenseVerifyResponse(VerifyData data) {

    /**
     * 校验数据。
     *
     * @param valid  是否有效；{@code null} 表示服务端未给出明确结论
     * @param reason 无效原因
     */
    public record VerifyData(Boolean valid, String reason) {
    }
}

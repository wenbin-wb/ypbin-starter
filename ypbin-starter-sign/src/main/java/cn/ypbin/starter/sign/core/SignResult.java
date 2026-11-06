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

import org.jspecify.annotations.Nullable;

/**
 * 签名校验结果。
 *
 * @param success   是否通过
 * @param message   失败原因（通过时为空）
 * @param accessKey 已认证的应用标识（失败时为空）
 * @param failure   失败类型（通过时为 {@code null}）；决定 {@code SignInterceptor} 返回的业务码
 * @author wenbin
 * @since 2026-07-30
 */
public record SignResult(boolean success, String message, String accessKey, @Nullable SignFailure failure) {

    /**
     * 兼容构造：自定义 {@link SignChecker} 仍可只关心结果与文案，失败类型留空（按 403 处理）。
     *
     * @param success   是否通过
     * @param message   失败原因
     * @param accessKey 已认证的应用标识
     */
    public SignResult(boolean success, String message, String accessKey) {
        this(success, message, accessKey, null);
    }

    /**
     * 成功结果。
     *
     * @param accessKey 已认证的应用标识
     * @return 成功
     */
    public static SignResult ok(String accessKey) {
        return new SignResult(true, "", accessKey, null);
    }

    /**
     * 失败结果（不区分类型）。
     *
     * <p>保留给既有实现：未声明类型的失败按 {@link SignFailure#FORBIDDEN}（403）对外呈现，
     * 避免把鉴权失败报成 500。能区分类型时请用 {@link #fail(String, SignFailure)}。</p>
     *
     * @param message 失败原因
     * @return 失败
     */
    public static SignResult fail(String message) {
        return fail(message, SignFailure.FORBIDDEN);
    }

    /**
     * 带类型的失败结果。
     *
     * @param message 失败原因
     * @param failure 失败类型
     * @return 失败
     */
    public static SignResult fail(String message, SignFailure failure) {
        return new SignResult(false, message, "", failure);
    }
}

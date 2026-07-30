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
 * 签名校验结果。
 *
 * @param success 是否通过
 * @param message 失败原因（通过时为空）
 * @author wenbin
 * @since 2026-07-30
 */
public record SignResult(boolean success, String message) {

    /**
     * 成功结果。
     *
     * @return 成功
     */
    public static SignResult ok() {
        return new SignResult(true, "");
    }

    /**
     * 失败结果。
     *
     * @param message 失败原因
     * @return 失败
     */
    public static SignResult fail(String message) {
        return new SignResult(false, message);
    }
}

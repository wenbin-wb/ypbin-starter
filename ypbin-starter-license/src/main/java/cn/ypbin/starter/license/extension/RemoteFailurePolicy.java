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
package cn.ypbin.starter.license.extension;

/**
 * 联机授权校验异常策略。
 *
 * @author wenbin
 * @since 2026-08-09
 */
public enum RemoteFailurePolicy {

    /** 网络或响应异常时阻断 */
    FAIL_CLOSED,

    /** 网络或响应异常时告警并临时放行 */
    FAIL_OPEN_WITH_WARNING
}

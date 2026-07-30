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
package cn.ypbin.starter.core.exception;

/**
 * 错误码契约。
 *
 * <p>业务方可用枚举实现本接口来扩展自定义错误码，从而与框架的统一响应体无缝对接。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public interface ErrorCode {

    /**
     * 业务状态码。
     *
     * @return 状态码
     */
    int getCode();

    /**
     * 提示信息。
     *
     * @return 描述文案
     */
    String getMessage();
}

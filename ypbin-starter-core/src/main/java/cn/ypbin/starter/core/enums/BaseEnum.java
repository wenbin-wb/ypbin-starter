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
package cn.ypbin.starter.core.enums;

/**
 * 通用枚举契约。
 *
 * <p>业务枚举实现本接口后，可与序列化、持久化、参数校验等环节统一对接：
 * {@code value} 用于存储/传输，{@code description} 用于展示。</p>
 *
 * @param <V> 枚举值类型（通常为 Integer 或 String）
 *
 * @author wenbin
 * @since 2026-07-30
 */
public interface BaseEnum<V> {

    /**
     * 枚举值（用于存储与传输）。
     *
     * @return 值
     */
    V getValue();

    /**
     * 枚举描述（用于展示）。
     *
     * @return 描述
     */
    String getDescription();
}

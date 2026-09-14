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
package cn.ypbin.starter.benchmarks;

import java.util.List;
import java.util.Map;

/**
 * 缓存序列化基准用的载荷。
 *
 * <p>刻意使用不可变集合（{@code List.of} / {@code Map.of}）与嵌套结构：JDK 不可变集合为 final 类型，
 * 多态类型标识无法写入，缓存序列化器必须先做规范化——这正是需要盯住的热路径。</p>
 *
 * @author wenbin
 * @since 2026-09-14
 */
public class CachePayload {

    /** 标识 */
    public Long id;

    /** 名称 */
    public String name;

    /** 不可变标签集合 */
    public List<String> tags;

    /** 不可变属性映射 */
    public Map<String, Object> attrs;

    /** 嵌套不可变集合 */
    public List<Object> nested;
}

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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * License 模块内部 JSON 编解码。
 *
 * <p>持有一个自包含、与 Web 层无关的 {@link ObjectMapper}，关闭时间戳序列化以固定 {@code LocalDateTime}
 * 的 ISO 文本表示，忽略未知字段以便向后兼容授权文件格式演进。授权载荷的签发与解析共用同一配置，
 * 保证签名侧与校验侧行为一致。</p>
 *
 * @author wenbin
 * @since 2026-08-05
 */
final class LicenseJson {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private LicenseJson() {
    }

    /**
     * 序列化为 JSON 字符串。
     *
     * @param value 对象
     * @return JSON 文本
     */
    static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("License JSON 序列化失败", e);
        }
    }

    /**
     * 反序列化为指定类型。
     *
     * @param json JSON 文本
     * @param type 目标类型
     * @param <T>  类型参数
     * @return 反序列化对象
     */
    static <T> T fromJson(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("License JSON 反序列化失败", e);
        }
    }
}

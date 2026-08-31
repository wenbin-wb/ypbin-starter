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
package cn.ypbin.starter.json.sensitive;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 敏感字段脱敏序列化测试：@Sensitive 注解字段在 JSON 输出时按类型掩码。
 *
 * @author wenbin
 * @since 2026-08-31
 */
class SensitiveSerializerTest {

    record User(
        @Sensitive(value = SensitiveType.PHONE, prefixKeep = 3, suffixKeep = 4) String phone,
        String name
    ) {
    }

    record AllType(
        @Sensitive(SensitiveType.ALL) String value
    ) {
    }

    private final ObjectMapper mapper = JsonMapper.builder().build();

    @Test
    void shouldMaskPhoneField() throws Exception {
        String json = mapper.writeValueAsString(new User("13800008000", "张三"));
        assertThat(json).contains("138****8000");
        assertThat(json).doesNotContain("13800008000");
    }

    @Test
    void shouldMaskAllWithAllType() throws Exception {
        String json = mapper.writeValueAsString(new AllType("abc"));
        assertThat(json).contains("***");
    }

    @Test
    void shouldHandleNullValue() throws Exception {
        String json = mapper.writeValueAsString(new User(null, "张三"));
        assertThat(json).contains("\"phone\":null");
    }
}

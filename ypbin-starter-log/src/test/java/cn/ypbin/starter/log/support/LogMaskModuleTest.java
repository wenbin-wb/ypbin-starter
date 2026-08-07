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
package cn.ypbin.starter.log.support;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.log.annotation.LogMask;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * {@link LogMaskModule} 单元测试：标注 {@link LogMask} 的字段序列化为固定掩码，未标注字段不受影响。
 *
 * @author wenbin
 * @since 2026-08-07
 */
class LogMaskModuleTest {

    static class LoginRequest {
        public String username;
        @LogMask
        public String password;
    }

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new LogMaskModule());

    @Test
    void serialize_shouldMaskAnnotatedField() throws Exception {
        LoginRequest request = new LoginRequest();
        request.username = "tom";
        request.password = "s3cret";

        String json = mapper.writeValueAsString(request);

        assertThat(json).contains("\"username\":\"tom\"");
        assertThat(json).contains("\"password\":\"******\"");
        assertThat(json).doesNotContain("s3cret");
    }

    @Test
    void serialize_shouldKeepNullAsNull() throws Exception {
        LoginRequest request = new LoginRequest();
        request.username = "tom";
        request.password = null;

        String json = mapper.writeValueAsString(request);

        assertThat(json).contains("\"password\":null");
    }

    @Test
    void serialize_shouldNotAffectPlainMapper() throws Exception {
        LoginRequest request = new LoginRequest();
        request.username = "tom";
        request.password = "s3cret";

        String json = new ObjectMapper().writeValueAsString(request);

        assertThat(json).contains("\"password\":\"s3cret\"");
    }
}

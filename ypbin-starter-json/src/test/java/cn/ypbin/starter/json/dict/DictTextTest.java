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
package cn.ypbin.starter.json.dict;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link DictText} / {@link DictTextSerializer} / {@link DictCache} 测试。
 *
 * @author wenbin
 * @since 2026-08-01
 */
class DictTextTest {

    private final ObjectMapper mapper = new ObjectMapper();

    static class Demo {
        @DictText("sys_user_status")
        public String status;

        @DictText(value = "gender", suffix = "Label")
        public String gender;

        Demo(String status, String gender) {
            this.status = status;
            this.gender = gender;
        }
    }

    @BeforeEach
    void setUp() {
        DictProvider provider = dictType -> switch (dictType) {
            case "sys_user_status" -> List.of(new DictItem("1", "正常"), new DictItem("0", "禁用"));
            case "gender" -> List.of(new DictItem("1", "男"), new DictItem("2", "女"));
            default -> List.of();
        };
        DictUtils.bind(new DictCache(provider));
    }

    @AfterEach
    void tearDown() {
        DictUtils.bind(null);
    }

    @Test
    void shouldOutputOriginalPlusTextField() throws Exception {
        String json = mapper.writeValueAsString(new Demo("1", "1"));
        // 原字段名不变 + 额外派生字段
        assertThat(json).contains("\"status\":\"1\"").contains("\"statusText\":\"正常\"");
        assertThat(json).contains("\"gender\":\"1\"").contains("\"genderLabel\":\"男\"");
    }

    @Test
    void unknownValueFallsBackToOriginal() throws Exception {
        String json = mapper.writeValueAsString(new Demo("9", "1"));
        assertThat(json).contains("\"statusText\":\"9\"");
    }

    @Test
    void translateAndCache() {
        assertThat(DictUtils.translate("sys_user_status", "0")).isEqualTo("禁用");
        assertThat(DictUtils.getItems("gender")).hasSize(2);
        assertThat(DictUtils.translate("sys_user_status", "unknown")).isEqualTo("unknown");
    }

    @Test
    void notReadyFallsBackToOriginal() {
        DictUtils.bind(null);
        assertThat(DictUtils.isReady()).isFalse();
        assertThat(DictUtils.translate("any", "1")).isEqualTo("1");
    }
}

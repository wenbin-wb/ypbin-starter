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
package cn.ypbin.starter.json.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.json.dict.DictItem;
import cn.ypbin.starter.json.dict.DictProvider;
import cn.ypbin.starter.json.dict.DictUtils;
import cn.ypbin.starter.json.ref.RefTextManager;
import cn.ypbin.starter.json.ref.RefTextResolver;
import cn.ypbin.starter.json.ref.RefTextUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;

/**
 * Jackson 自动装配集成测试：容器启动后 ObjectMapper 定制、Dict/RefText 绑定链应完整可用。
 *
 * @author wenbin
 * @since 2026-08-31
 */
@SpringBootTest(classes = {
    JacksonAutoConfigurationTest.TestConfig.class,
    cn.ypbin.starter.json.autoconfigure.JacksonAutoConfiguration.class,
})
@TestPropertySource(properties = {
    "ypbin.json.date-time-format=yyyy-MM-dd HH:mm:ss",
})
class JacksonAutoConfigurationTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        DictProvider dictProvider() {
            return new TestDictProvider();
        }

        @Bean
        cn.ypbin.starter.json.ref.RefTextProvider refTextProvider() {
            return new cn.ypbin.starter.json.ref.RefTextProvider() {
                @Override
                public String type() {
                    return "user";
                }

                @Override
                public java.util.Map<Object, String> getNames(java.util.Collection<Object> ids) {
                    java.util.Map<Object, String> map = new java.util.HashMap<>();
                    ids.forEach(id -> map.put(id, "用户" + id));
                    return map;
                }
            };
        }

        @Bean
        com.fasterxml.jackson.databind.ObjectMapper objectMapper(
            List<org.springframework.boot.jackson2.autoconfigure.Jackson2ObjectMapperBuilderCustomizer> customizers) {
            org.springframework.http.converter.json.Jackson2ObjectMapperBuilder builder =
                new org.springframework.http.converter.json.Jackson2ObjectMapperBuilder();
            for (var customizer : customizers) {
                customizer.customize(builder);
            }
            return builder.build();
        }
    }

    static class TestDictProvider implements DictProvider {
        @Override
        public List<DictItem> getItems(String dictType) {
            return List.of(new DictItem("1", "启用"));
        }
    }

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RefTextManager refTextManager;

    @Autowired
    private RefTextResolver refTextResolver;

    @Test
    void shouldWireJacksonBeans() {
        assertThat(objectMapper).isNotNull();
        assertThat(refTextManager).isNotNull();
        assertThat(refTextResolver).isNotNull();
    }

    @Test
    void shouldSerializeLocalDateTimeWithConfiguredPattern() throws Exception {
        String json = objectMapper.writeValueAsString(
            new Holder(LocalDateTime.of(2026, 8, 31, 10, 30)));
        assertThat(json).contains("2026-08-31 10:30:00");
    }

    @Test
    void refTextManagerShouldTranslateViaProvider() {
        String name = refTextManager.translate("user", 1L);
        assertThat(name).isEqualTo("用户1");
        RefTextUtils.preload("user", List.of(2L, 3L));
        RefTextUtils.refresh();
        RefTextUtils.refresh("user");
    }

    @Test
    void dictUtilsShouldTranslateViaProvider() {
        assertThat(DictUtils.translate("status", "1")).isEqualTo("启用");
    }

    record Holder(LocalDateTime time) {
    }
}

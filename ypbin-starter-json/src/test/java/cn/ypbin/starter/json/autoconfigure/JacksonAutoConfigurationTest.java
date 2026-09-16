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

import cn.ypbin.starter.json.dict.DictCache;
import cn.ypbin.starter.json.dict.DictItem;
import cn.ypbin.starter.json.dict.DictProvider;
import cn.ypbin.starter.json.dict.DictUtils;
import cn.ypbin.starter.json.ref.RefTextManager;
import cn.ypbin.starter.json.ref.RefTextResolver;
import cn.ypbin.starter.json.ref.RefTextUtils;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.json.JsonMapper;

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
    "ypbin.json.dict.ttl-seconds=1",
    "ypbin.json.dict.max-size=7",
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
        JsonMapper jsonMapper(List<JsonMapperBuilderCustomizer> customizers) {
            JsonMapper.Builder builder = JsonMapper.builder();
            for (JsonMapperBuilderCustomizer customizer : customizers) {
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
    private JsonMapper objectMapper;

    @Autowired
    private RefTextManager refTextManager;

    @Autowired
    private RefTextResolver refTextResolver;

    @Autowired
    private JacksonProperties jacksonProperties;

    @Autowired
    private DictCache dictCache;

    @Test
    void shouldWireJacksonBeans() {
        assertThat(objectMapper).isNotNull();
        assertThat(refTextManager).isNotNull();
        assertThat(refTextResolver).isNotNull();
    }

    /**
     * 字典缓存的 TTL / 容量上限必须由 {@code ypbin.json.dict.*} 真实驱动到 {@link DictCache} 实例上。
     *
     * <p>只断言属性绑定不足以证明接线（装配处误用 {@code getRefText()} 同样会通过），
     * 故直接读取 {@link DictCache} 内部生效值：ttl=1s、maxSize=7。</p>
     */
    @Test
    void shouldWireDictCachePropertiesIntoCacheInstance() throws Exception {
        assertThat(jacksonProperties.getDict().getTtlSeconds()).isEqualTo(1L);
        assertThat(jacksonProperties.getDict().getMaxSize()).isEqualTo(7);

        Field ttlMillis = DictCache.class.getDeclaredField("ttlMillis");
        ttlMillis.setAccessible(true);
        Field maxSize = DictCache.class.getDeclaredField("maxSize");
        maxSize.setAccessible(true);
        assertThat((long) ttlMillis.get(dictCache)).isEqualTo(1000L);
        assertThat((int) maxSize.get(dictCache)).isEqualTo(7);
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

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
package cn.ypbin.starter.cache.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证 Redis 值序列化器（default typing）对泛型集合的还原行为。
 *
 * <p>背景：{@code CacheService.get(key, type)} 的 type 参数仅做强转，实际反序列化由
 * {@code GenericJackson2JsonRedisSerializer}（default typing，元素带 {@code @class}）完成。
 * 本测试实证：非空/空泛型集合经序列化往返后，元素类型是否正确还原（而非 LinkedHashMap）。</p>
 *
 * @author wenbin
 * @since 2026-09-01
 */
class GenericCollectionRoundTripTest {

    /** 与 CacheAutoConfiguration.buildRedisObjectMapper 相同配置（default typing NON_FINAL + As.PROPERTY）。 */
    private ObjectMapper redisMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        mapper.activateDefaultTyping(
            BasicPolymorphicTypeValidator.builder().allowIfBaseType(Object.class).build(),
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY);
        return mapper;
    }

    public static class DemoItem {

        private Long id;

        private String name;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @Test
    void nonEmptyGenericListShouldRoundTripWithElementType() throws Exception {
        ObjectMapper mapper = redisMapper();
        List<DemoItem> original = new ArrayList<>();
        DemoItem item = new DemoItem();
        item.setId(42L);
        item.setName("alice");
        original.add(item);

        byte[] bytes = mapper.writeValueAsBytes(original);
        Object restored = mapper.readValue(bytes, Object.class);

        assertThat(restored).isInstanceOf(List.class);
        Object first = ((List<?>) restored).get(0);
        // 元素应带 @class 正确还原为 DemoItem，而非 LinkedHashMap
        assertThat(first).isInstanceOf(DemoItem.class);
        assertThat(((DemoItem) first).getName()).isEqualTo("alice");
    }

    @Test
    void stringListShouldRoundTrip() throws Exception {
        ObjectMapper mapper = redisMapper();
        List<String> original = new ArrayList<>(List.of("admin", "user"));

        byte[] bytes = mapper.writeValueAsBytes(original);
        Object restored = mapper.readValue(bytes, Object.class);

        assertThat(restored).isInstanceOf(List.class);
        List<?> list = (List<?>) restored;
        assertThat(list).hasSize(2);
        assertThat(list.get(0)).isEqualTo("admin");
        assertThat(list.get(1)).isEqualTo("user");
    }

    @Test
    void emptyListShouldRoundTrip() throws Exception {
        ObjectMapper mapper = redisMapper();
        List<DemoItem> original = new ArrayList<>();

        byte[] bytes = mapper.writeValueAsBytes(original);
        Object restored = mapper.readValue(bytes, Object.class);

        // 空集合还原为 List（元素类型无从体现，但业务侧空集合不遍历，安全）
        assertThat(restored).isInstanceOf(List.class);
        assertThat((List<?>) restored).isEmpty();
    }
}

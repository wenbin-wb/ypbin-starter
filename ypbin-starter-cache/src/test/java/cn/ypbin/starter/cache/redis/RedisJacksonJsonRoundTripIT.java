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

import cn.ypbin.starter.test.condition.EnabledIfRedisAvailable;
import cn.ypbin.starter.test.container.RedisIntegrationTestSupport;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 缓存值序列化（Jackson 3）真实 Redis 往返测试。
 *
 * <p>默认跳过：仅在设置了环境变量 {@code YPBIN_TEST_REDIS_PASSWORD} 时运行，避免 CI 无 Redis 环境失败。
 * 本地需要真实回归时执行（密码取自部署 .env）：</p>
 * <pre>{@code
 * YPBIN_TEST_REDIS_PASSWORD=xxx mvn -pl ypbin-starter-cache test -Dtest=RedisJacksonJsonRoundTripIT
 * }</pre>
 *
 * <p>测试用与 {@code CacheAutoConfiguration} 相同的装配方式（String 键 + JSON 值 + default typing），
 * 因此覆盖的是真实生产序列化路径，而非仅校验 mapper 配置。</p>
 *
 * @author wenbin
 * @since 2026-09-13
 */
@EnabledIfRedisAvailable
class RedisJacksonJsonRoundTripIT {

    private static final String KEY_PREFIX = "ypbin:test:jackson3:";

    /** 测试用元素类型：无默认构造以外的要求，用于验证多态类型信息是否被还原 */
    public static class DemoItem {

        private Long id;

        private String name;

        private List<String> tags;

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

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }
    }

    private static RedisTemplate<String, Object> redisTemplate() {
        // 连接由基座解析：外部实例（YPBIN_TEST_REDIS_*）优先，其次 Docker 容器
        // 复用生产工厂，避免测试复刻配置产生漂移
        RedisSerializer<Object> valueSerializer =
            RedisJsonSerializerFactory.create(JsonMapper.builder().build());
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(RedisIntegrationTestSupport.connectionFactory());
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();
        return template;
    }

    @Test
    void genericCollectionShouldRoundTripThroughRealRedis() {
        RedisTemplate<String, Object> template = redisTemplate();
        String key = KEY_PREFIX + "list";
        try {
            DemoItem item = new DemoItem();
            item.setId(42L);
            item.setName("alice");
            item.setTags(List.of("a", "b"));

            template.opsForValue().set(key, List.of(item));
            Object restored = template.opsForValue().get(key);

            assertThat(restored).isInstanceOf(List.class);
            Object first = ((List<?>) restored).get(0);
            // 关键断言：元素被还原为 DemoItem 而非退化为 LinkedHashMap，证明多态类型信息生效
            assertThat(first).isInstanceOf(DemoItem.class);
            assertThat(((DemoItem) first).getName()).isEqualTo("alice");
            assertThat(((DemoItem) first).getTags()).containsExactly("a", "b");
        } finally {
            template.delete(key);
        }
    }

    /**
     * 回归：JDK 不可变集合（List.of/Map.of/Set.of/Stream.toList）为 final 类型，
     * 多态类型标识无法写入。若写入前不做规范化，这些值读回时必然抛
     * {@code SerializationException}（缺少类型 id）。本项目大量使用此类集合，必须锁定。
     */
    @Test
    void immutableCollectionsShouldRoundTripThroughRealRedis() {
        RedisTemplate<String, Object> template = redisTemplate();
        DemoItem item = new DemoItem();
        item.setId(9L);
        item.setName("carol");

        assertRoundTrip(template, "listOf", List.of(item), restored -> {
            assertThat(restored).isInstanceOf(List.class);
            assertThat(((List<?>) restored).get(0)).isInstanceOf(DemoItem.class);
        });
        assertRoundTrip(template, "toList", Stream.of(item).toList(), restored -> {
            assertThat(restored).isInstanceOf(List.class);
            assertThat(((List<?>) restored).get(0)).isInstanceOf(DemoItem.class);
        });
        assertRoundTrip(template, "setOf", Set.of(item), restored -> {
            assertThat(restored).isInstanceOf(Set.class);
            assertThat(((Set<?>) restored).iterator().next()).isInstanceOf(DemoItem.class);
        });
        assertRoundTrip(template, "mapOf", Map.of("k", item), restored -> {
            assertThat(restored).isInstanceOf(Map.class);
            assertThat(((Map<?, ?>) restored).get("k")).isInstanceOf(DemoItem.class);
        });
        assertRoundTrip(template, "stringList", List.of("a", "b"),
            restored -> assertThat(toStringList(restored)).containsExactly("a", "b"));
        assertRoundTrip(template, "emptyList", List.of(),
            restored -> assertThat((List<?>) restored).isEmpty());
        assertRoundTrip(template, "emptyMap", Map.of(),
            restored -> assertThat((Map<?, ?>) restored).isEmpty());
        assertRoundTrip(template, "nestedListOfList", List.of(List.of("a"), List.of("b")),
            restored -> assertThat(toStringList(toStringList(restored).get(0))).containsExactly("a"));
    }

    @SuppressWarnings("unchecked")
    private static List<Object> toStringList(Object value) {
        return (List<Object>) value;
    }

    private static void assertRoundTrip(RedisTemplate<String, Object> template, String label,
            Object value, java.util.function.Consumer<Object> assertions) {
        String key = KEY_PREFIX + label;
        try {
            template.opsForValue().set(key, value);
            Object restored = template.opsForValue().get(key);
            assertThat(restored).as("round-trip of %s", label).isNotNull();
            assertions.accept(restored);
        } finally {
            template.delete(key);
        }
    }

    @Test
    void scalarAndPlainBeanShouldRoundTripThroughRealRedis() {
        RedisTemplate<String, Object> template = redisTemplate();
        String key = KEY_PREFIX + "bean";
        try {
            DemoItem item = new DemoItem();
            item.setId(7L);
            item.setName("bob");

            template.opsForValue().set(key, item);
            Object restored = template.opsForValue().get(key);

            assertThat(restored).isInstanceOf(DemoItem.class);
            assertThat(((DemoItem) restored).getId()).isEqualTo(7L);
            assertThat(((DemoItem) restored).getName()).isEqualTo("bob");
        } finally {
            template.delete(key);
        }
    }
}

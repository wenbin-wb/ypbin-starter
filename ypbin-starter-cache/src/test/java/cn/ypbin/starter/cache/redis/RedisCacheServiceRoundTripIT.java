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

import cn.ypbin.starter.cache.core.CacheService;
import cn.ypbin.starter.test.condition.EnabledIfRedisAvailable;
import cn.ypbin.starter.test.container.RedisIntegrationTestSupport;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * 生产装配链路（{@code CacheAutoConfiguration} + {@code CacheService}）的真实 Redis 往返测试。
 *
 * <p>与 {@link RedisJacksonJsonRoundTripIT} 的区别：本测试走完整 Spring 装配——由自动配置创建
 * {@code redisTemplate} 与 {@code CacheService} Bean，验证「Bean 装配 + 序列化器注入 + 业务取数」整条链路，
 * 而不只是序列化器自身。默认跳过，设置 {@code YPBIN_TEST_REDIS_PASSWORD} 后运行：</p>
 * <pre>{@code
 * YPBIN_TEST_REDIS_PASSWORD=xxx mvn -pl ypbin-starter-cache test -Dtest=RedisCacheServiceRoundTripIT
 * }</pre>
 *
 * @author wenbin
 * @since 2026-09-13
 */
@EnabledIfRedisAvailable
class RedisCacheServiceRoundTripIT {

    private static final String KEY_PREFIX = "ypbin:test:cachesvc:";

    /** 测试用元素类型 */
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

    private static DemoItem item(long id, String name) {
        DemoItem demo = new DemoItem();
        demo.setId(id);
        demo.setName(name);
        return demo;
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                cn.ypbin.starter.cache.autoconfigure.CacheAutoConfiguration.class))
            .withBean("redisConnectionFactory", RedisConnectionFactory.class,
                RedisIntegrationTestSupport::connectionFactory);
    }

    @Test
    void cacheServiceShouldRoundTripImmutableCollections() {
        runner().run(context -> {
            CacheService cacheService = context.getBean(CacheService.class);

            // 不可变 List（本项目最常见的返回形态）
            cacheService.set(KEY_PREFIX + "listOf", List.of(item(1L, "alice")));
            List<?> list = cacheService.get(KEY_PREFIX + "listOf", List.class);
            assertThat(list).hasSize(1);
            // 元素类型必须被还原，否则业务侧强转会 ClassCastException
            assertThat(list.get(0)).isInstanceOf(DemoItem.class);
            assertThat(((DemoItem) list.get(0)).getName()).isEqualTo("alice");
            cacheService.delete(KEY_PREFIX + "listOf");

            // Stream.toList() 产物
            cacheService.set(KEY_PREFIX + "toList", Stream.of(item(2L, "bob")).toList());
            List<?> toList = cacheService.get(KEY_PREFIX + "toList", List.class);
            assertThat(toList.get(0)).isInstanceOf(DemoItem.class);
            cacheService.delete(KEY_PREFIX + "toList");

            // 不可变 Set
            cacheService.set(KEY_PREFIX + "setOf", Set.of(item(3L, "carol")));
            Set<?> set = cacheService.get(KEY_PREFIX + "setOf", Set.class);
            assertThat(set.iterator().next()).isInstanceOf(DemoItem.class);
            cacheService.delete(KEY_PREFIX + "setOf");

            // 不可变 Map
            cacheService.set(KEY_PREFIX + "mapOf", Map.of("k", item(4L, "dave")));
            Map<?, ?> map = cacheService.get(KEY_PREFIX + "mapOf", Map.class);
            assertThat(map.get("k")).isInstanceOf(DemoItem.class);
            cacheService.delete(KEY_PREFIX + "mapOf");

            // 空集合兜底（starter 约定返回空集合而非 null）
            cacheService.set(KEY_PREFIX + "empty", List.of());
            assertThat(cacheService.get(KEY_PREFIX + "empty", List.class)).isEmpty();
            cacheService.delete(KEY_PREFIX + "empty");

            // 单个 POJO
            cacheService.set(KEY_PREFIX + "bean", item(5L, "erin"));
            DemoItem bean = cacheService.get(KEY_PREFIX + "bean", DemoItem.class);
            assertThat(bean.getName()).isEqualTo("erin");
            cacheService.delete(KEY_PREFIX + "bean");
        });
    }
}

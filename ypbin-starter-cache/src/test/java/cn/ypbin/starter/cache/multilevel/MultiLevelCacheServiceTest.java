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
package cn.ypbin.starter.cache.multilevel;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.cache.core.CacheService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * {@link MultiLevelCacheService} 单元测试（用内存假 L2，聚焦 L1 逻辑）。
 *
 * @author wenbin
 * @since 2026-07-31
 */
class MultiLevelCacheServiceTest {

    /** 记录 L2 访问次数的内存假实现 */
    static class FakeL2 implements CacheService {
        final Map<String, Object> store = new HashMap<>();
        final AtomicInteger getCount = new AtomicInteger();
        final AtomicInteger loadCount = new AtomicInteger();

        @Override
        public void set(String key, Object value) {
            store.put(key, value);
        }

        @Override
        public void set(String key, Object value, Duration timeout) {
            store.put(key, value);
        }

        @Override
        public boolean setIfAbsent(String key, Object value, Duration timeout) {
            return store.putIfAbsent(key, value) == null;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(String key, Class<T> type) {
            getCount.incrementAndGet();
            return (T) store.get(key);
        }

        @Override
        public boolean delete(String key) {
            return store.remove(key) != null;
        }

        @Override
        public boolean compareAndDelete(String key, Object expected) {
            Object current = store.get(key);
            if (!Objects.equals(current, expected)) {
                return false;
            }
            store.remove(key);
            return true;
        }

        @Override
        public long delete(Collection<String> keys) {
            long c = 0;
            for (String k : keys) {
                if (store.remove(k) != null) {
                    c++;
                }
            }
            return c;
        }

        @Override
        public boolean exists(String key) {
            return store.containsKey(key);
        }

        @Override
        public boolean expire(String key, Duration timeout) {
            return true;
        }

        @Override
        public long increment(String key, long delta) {
            return delta;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getOrLoad(String key, Class<T> type, Supplier<T> loader, Duration ttl) {
            Object v = store.get(key);
            if (v != null) {
                return (T) v;
            }
            loadCount.incrementAndGet();
            T loaded = loader.get();
            store.put(key, loaded);
            return loaded;
        }
    }

    private MultiLevelCacheService build(FakeL2 l2) {
        Cache<String, Object> l1 = Caffeine.newBuilder().maximumSize(100).build();
        return new MultiLevelCacheService(l2, l1, null);
    }

    @Test
    void setIfAbsentShouldUseL2AsAtomicSourceAndEvictL1() {
        FakeL2 l2 = new FakeL2();
        l2.set("k", "old");
        MultiLevelCacheService cache = build(l2);
        assertThat(cache.get("k", String.class)).isEqualTo("old");

        assertThat(cache.setIfAbsent("k", "new", Duration.ofMinutes(1))).isFalse();
        assertThat(l2.store).containsEntry("k", "old");
        assertThat(cache.get("k", String.class)).isEqualTo("old");

        assertThat(cache.setIfAbsent("new", "value", Duration.ofMinutes(1))).isTrue();
        assertThat(cache.get("new", String.class)).isEqualTo("value");
    }

    @Test
    void secondGetShouldHitL1NotL2() {
        FakeL2 l2 = new FakeL2();
        l2.set("k", "v");
        MultiLevelCacheService cache = build(l2);

        assertThat(cache.get("k", String.class)).isEqualTo("v"); // L1 miss -> L2 hit -> 回填 L1
        assertThat(cache.get("k", String.class)).isEqualTo("v"); // L1 hit
        // L2.get 只应被调用一次（第二次命中 L1）
        assertThat(l2.getCount.get()).isEqualTo(1);
    }

    @Test
    void getOrLoadShouldLoadOnceThenHitL1() {
        FakeL2 l2 = new FakeL2();
        MultiLevelCacheService cache = build(l2);
        AtomicInteger loaderCalls = new AtomicInteger();

        Supplier<String> loader = () -> {
            loaderCalls.incrementAndGet();
            return "loaded";
        };

        assertThat(cache.getOrLoad("k", String.class, loader, Duration.ofMinutes(5))).isEqualTo("loaded");
        assertThat(cache.getOrLoad("k", String.class, loader, Duration.ofMinutes(5))).isEqualTo("loaded");
        // 回源只发生一次，第二次命中 L1
        assertThat(loaderCalls.get()).isEqualTo(1);
        assertThat(l2.loadCount.get()).isEqualTo(1);
    }

    @Test
    void getOrLoadShouldCacheNullAndNotReload() {
        FakeL2 l2 = new FakeL2();
        MultiLevelCacheService cache = build(l2);
        AtomicInteger loaderCalls = new AtomicInteger();

        Supplier<String> loader = () -> {
            loaderCalls.incrementAndGet();
            return null;
        };

        assertThat(cache.getOrLoad("k", String.class, loader, Duration.ofMinutes(5))).isNull();
        // 空值也回填 L1（哨兵），第二次不再回源
        assertThat(cache.getOrLoad("k", String.class, loader, Duration.ofMinutes(5))).isNull();
        assertThat(loaderCalls.get()).isEqualTo(1);
    }

    @Test
    void deleteShouldEvictL1() {
        FakeL2 l2 = new FakeL2();
        l2.set("k", "v");
        MultiLevelCacheService cache = build(l2);

        cache.get("k", String.class); // 回填 L1
        cache.delete("k"); // 失效 L1 + L2
        assertThat(cache.get("k", String.class)).isNull();
    }

    @Test
    void compareAndDeleteShouldRemoveMatchingL2ValueAndEvictL1() {
        FakeL2 l2 = new FakeL2();
        l2.set("k", "v");
        MultiLevelCacheService cache = build(l2);
        assertThat(cache.get("k", String.class)).isEqualTo("v");

        assertThat(cache.compareAndDelete("k", "v")).isTrue();
        assertThat(l2.store).doesNotContainKey("k");
        assertThat(cache.get("k", String.class)).isNull();
    }

    @Test
    void compareAndDeleteShouldKeepMismatchedL2ValueAndRefreshStaleL1() {
        FakeL2 l2 = new FakeL2();
        l2.set("k", "old");
        MultiLevelCacheService cache = build(l2);
        assertThat(cache.get("k", String.class)).isEqualTo("old");
        l2.set("k", "current");

        assertThat(cache.compareAndDelete("k", "wrong")).isFalse();
        assertThat(l2.store).containsEntry("k", "current");
        assertThat(cache.get("k", String.class)).isEqualTo("current");
    }

    @Test
    void existsShouldTreatNullSentinelAsAbsent() {
        FakeL2 l2 = new FakeL2();
        MultiLevelCacheService cache = build(l2);

        // 回源为 null：L1 缓存空值哨兵。get 返回 null，exists 也应返回 false（语义一致）
        cache.getOrLoad("k", String.class, () -> null, Duration.ofMinutes(5));
        assertThat(cache.get("k", String.class)).isNull();
        assertThat(cache.exists("k")).isFalse();
    }
}

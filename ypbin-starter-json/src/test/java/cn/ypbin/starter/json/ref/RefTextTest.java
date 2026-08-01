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
package cn.ypbin.starter.json.ref;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link RefText} 引用翻译：缓存、批量、预加载、序列化测试。
 *
 * @author wenbin
 * @since 2026-08-01
 */
class RefTextTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /** 记录批量查询次数与被查 ID 总数的假 provider */
    static class CountingUserProvider implements RefTextProvider {
        final AtomicInteger queryCount = new AtomicInteger();
        final AtomicInteger idCount = new AtomicInteger();
        final Map<Object, String> db = new HashMap<>();

        CountingUserProvider() {
            db.put(1L, "张三");
            db.put(2L, "李四");
            db.put(3L, "王五");
        }

        @Override
        public String type() {
            return "user";
        }

        @Override
        public Map<Object, String> getNames(Collection<Object> ids) {
            queryCount.incrementAndGet();
            idCount.addAndGet(ids.size());
            Map<Object, String> result = new HashMap<>();
            for (Object id : ids) {
                if (db.containsKey(id)) {
                    result.put(id, db.get(id));
                }
            }
            return result;
        }
    }

    static class Row {
        @RefText("user")
        public Long createUser;

        Row(Long createUser) {
            this.createUser = createUser;
        }
    }

    private CountingUserProvider provider;
    private RefTextManager manager;

    @BeforeEach
    void setUp() {
        provider = new CountingUserProvider();
        RefTextCache cache = new RefTextCache(60_000L, 1000);
        manager = new RefTextManager(List.of(provider), cache);
        RefTextUtils.bind(manager);
    }

    @AfterEach
    void tearDown() {
        RefTextUtils.bind(null);
    }

    @Test
    void translateUsesCacheOnRepeatedId() {
        assertThat(manager.translate("user", 1L)).isEqualTo("张三");
        assertThat(manager.translate("user", 1L)).isEqualTo("张三");
        assertThat(manager.translate("user", 1L)).isEqualTo("张三");
        // 同一 ID 只回源一次，其余命中缓存
        assertThat(provider.queryCount.get()).isEqualTo(1);
    }

    @Test
    void preloadBatchesAllIdsInOneQuery() {
        List<Object> ids = List.of(1L, 2L, 3L, 1L, 2L);
        manager.preload("user", ids);
        // 5 个（去重后 3 个）ID 合并为一次批量查询
        assertThat(provider.queryCount.get()).isEqualTo(1);
        assertThat(provider.idCount.get()).isEqualTo(3);
        // 预加载后翻译零回源
        assertThat(manager.translate("user", 2L)).isEqualTo("李四");
        assertThat(provider.queryCount.get()).isEqualTo(1);
    }

    @Test
    void listSerializationAfterPreloadHasNoNPlusOne() throws Exception {
        List<Row> rows = new ArrayList<>();
        for (long i = 0; i < 100; i++) {
            rows.add(new Row((i % 3) + 1)); // 100 行，只有 3 个不同创建人
        }
        // 预加载：整表一次批量查询
        new RefTextResolver(manager).preload(rows);
        assertThat(provider.queryCount.get()).isEqualTo(1);

        // 序列化 100 行，全部命中缓存，无新增回源
        String json = mapper.writeValueAsString(rows);
        assertThat(provider.queryCount.get()).isEqualTo(1);
        assertThat(json).contains("\"createUserName\":\"张三\"");
    }

    @Test
    void serializationKeepsOriginalAndAddsNameField() throws Exception {
        String json = mapper.writeValueAsString(new Row(1L));
        assertThat(json).contains("\"createUser\":1").contains("\"createUserName\":\"张三\"");
    }

    @Test
    void unknownIdCachedAsSentinelNoRepeatQuery() {
        assertThat(manager.translate("user", 999L)).isNull();
        assertThat(manager.translate("user", 999L)).isNull();
        // 不存在的 ID 也只查一次（空值哨兵防穿透）
        assertThat(provider.queryCount.get()).isEqualTo(1);
    }

    @Test
    void refreshClearsCache() {
        manager.translate("user", 1L);
        manager.refresh();
        manager.translate("user", 1L);
        // 刷新后重新回源
        assertThat(provider.queryCount.get()).isEqualTo(2);
    }

    @Test
    void notReadyFallsBackToNull() {
        RefTextUtils.bind(null);
        assertThat(RefTextUtils.isReady()).isFalse();
        assertThat(RefTextUtils.translate("user", 1L)).isNull();
    }

    /** 不含 @RefText 的类应被类级缓存判定为 false，供自动预加载零遍历跳过 */
    static class PlainDto {
        public String name;
        public int age;
    }

    @Test
    void containsRefTextDetection() {
        RefTextResolver resolver = new RefTextResolver(manager);
        assertThat(resolver.containsRefText(Row.class)).isTrue();
        assertThat(resolver.containsRefText(PlainDto.class)).isFalse();
        assertThat(resolver.containsRefText(String.class)).isFalse();
    }

    @Test
    void preloadOnPlainObjectIsNoop() {
        new RefTextResolver(manager).preload(new PlainDto());
        // 无 @RefText，零回源
        assertThat(provider.queryCount.get()).isZero();
    }

    /** 外层对象本身无 @RefText，但嵌套集合的元素有 —— 剪枝不能误杀 */
    static class Order {
        public List<OrderItem> items;

        Order(List<OrderItem> items) {
            this.items = items;
        }
    }

    static class OrderItem {
        @RefText("user")
        public Long handler;

        OrderItem(Long handler) {
            this.handler = handler;
        }
    }

    @Test
    void containsRefTextDetectsNestedCollectionElement() {
        // 关键回归：List<OrderItem> 的元素含 @RefText，Order 应被判定为含引用
        assertThat(new RefTextResolver(manager).containsRefText(Order.class)).isTrue();
    }

    @Test
    void preloadScansNestedCollectionAndBatchesOnce() throws Exception {
        List<OrderItem> items = new ArrayList<>();
        for (long i = 0; i < 50; i++) {
            items.add(new OrderItem((i % 3) + 1));
        }
        Order order = new Order(items);

        new RefTextResolver(manager).preload(order);
        // 嵌套集合被正确扫描，50 项 3 个不同 handler 合并为一次批量查询（剪枝修复前这里会是 0，退化 N+1）
        assertThat(provider.queryCount.get()).isEqualTo(1);

        String json = mapper.writeValueAsString(order);
        assertThat(provider.queryCount.get()).isEqualTo(1);
        assertThat(json).contains("\"handlerName\":\"张三\"");
    }
}

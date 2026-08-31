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

import cn.ypbin.starter.json.dict.DictItem;
import cn.ypbin.starter.json.dict.DictProvider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * @RefText 引用翻译完整链路测试：实体字段标注 → 序列化器 → resolver 反射解析 → 名称替换。
 *
 * @author wenbin
 * @since 2026-08-31
 */
class RefTextSerializationTest {

    static class Order {
        public Long id;

        @RefText("user")
        public Long userId;

        public String userName;

        public Order(Long id, Long userId) {
            this.id = id;
            this.userId = userId;
        }
    }

    static class UserProvider implements RefTextProvider {
        @Override
        public String type() {
            return "user";
        }

        @Override
        public Map<Object, String> getNames(java.util.Collection<Object> ids) {
            return Map.of(1L, "张三", 2L, "李四");
        }
    }

    @Test
    void shouldTranslateRefTextFieldOnSerialization() throws Exception {
        RefTextCache cache = new RefTextCache(60_000, 100);
        RefTextManager manager = new RefTextManager(List.of(new UserProvider()), cache);
        RefTextResolver resolver = new RefTextResolver(manager);
        RefTextUtils.bind(manager);

        ObjectMapper mapper = JsonMapper.builder().build();
        // 序列化前预热，触发 resolver.preload 反射解析
        resolver.preload(new Order(10L, 1L));
        assertThat(manager.translate("user", 1L)).isEqualTo("张三");

        String json = mapper.writeValueAsString(new Order(10L, 1L));
        assertThat(json).contains("userId");
    }

    @Test
    void containsRefTextShouldDetectAnnotatedFields() {
        RefTextCache cache = new RefTextCache(60_000, 100);
        RefTextManager manager = new RefTextManager(List.of(new UserProvider()), cache);
        RefTextResolver resolver = new RefTextResolver(manager);
        assertThat(resolver.containsRefText(Order.class)).isTrue();
        assertThat(resolver.containsRefText(String.class)).isFalse();
    }

    @Test
    void managerShouldDelegateToProviderAndCache() {
        RefTextCache cache = new RefTextCache(60_000, 100);
        RefTextManager manager = new RefTextManager(List.of(new UserProvider()), cache);
        assertThat(manager.translate("user", 1L)).isEqualTo("张三");
        assertThat(manager.translate("user", 99L)).isNull();
        assertThat(manager.supports("user")).isTrue();
        manager.preload("user", List.of(1L, 2L));
        manager.refresh();
        manager.refresh("user");
    }

    @Test
    void dictProviderIntegration() {
        // DictProvider 独立验证（与 @DictText 序列化器解耦的最小路径）
        DictProvider provider = new DictProvider() {
            @Override
            public List<DictItem> getItems(String dictType) {
                return List.of(new DictItem("1", "启用"));
            }
        };
        cn.ypbin.starter.json.dict.DictCache dictCache = new cn.ypbin.starter.json.dict.DictCache(provider);
        assertThat(dictCache.translate("status", "1")).isEqualTo("启用");
    }
}

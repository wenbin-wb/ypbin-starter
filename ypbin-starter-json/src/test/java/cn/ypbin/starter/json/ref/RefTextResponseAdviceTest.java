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

import cn.ypbin.starter.json.dict.DictCache;
import cn.ypbin.starter.json.dict.DictItem;
import cn.ypbin.starter.json.dict.DictProvider;
import cn.ypbin.starter.json.dict.DictUtils;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

/**
 * 响应拦截与缓存工具测试：RefTextResponseAdvice 的 supports/委托逻辑、
 * DictCache 的 TTL/容量、DictUtils 静态绑定。
 *
 * @author wenbin
 * @since 2026-08-31
 */
class RefTextResponseAdviceTest {

    @RefTextIgnore
    static class IgnoredController {
        public void ignored() {
        }
    }

    static class PlainController {
        public void plain() {
        }
    }

    private MethodParameter paramOf(Class<?> clazz) throws Exception {
        return MethodParameter.forExecutable(clazz.getDeclaredMethods()[0], -1);
    }

    @Test
    void supportsShouldSkipIgnoredClass() throws Exception {
        RefTextResponseAdvice advice = new RefTextResponseAdvice(new RefTextResolver(null));
        assertThat(advice.supports(paramOf(IgnoredController.class),
            MappingJackson2HttpMessageConverter.class)).isFalse();
    }

    @Test
    void supportsShouldAcceptPlainClass() throws Exception {
        RefTextResponseAdvice advice = new RefTextResponseAdvice(new RefTextResolver(null));
        assertThat(advice.supports(paramOf(PlainController.class),
            MappingJackson2HttpMessageConverter.class)).isTrue();
    }

    @Test
    void beforeBodyWriteShouldReturnSameBody() {
        RefTextResponseAdvice advice = new RefTextResponseAdvice(new RefTextResolver(null));
        Object body = new Object();
        Object result = advice.beforeBodyWrite(body, null, MediaType.APPLICATION_JSON,
            MappingJackson2HttpMessageConverter.class, null, null);
        assertThat(result).isSameAs(body);
    }

    @Test
    void dictCacheShouldTranslateAndRefresh() {
        DictProvider provider = new DictProvider() {
            @Override
            public List<DictItem> getItems(String dictType) {
                return List.of(new DictItem("1", "启用"), new DictItem("2", "禁用"));
            }
        };
        DictCache cache = new DictCache(provider);
        assertThat(cache.translate("status", "1")).isEqualTo("启用");
        assertThat(cache.translate("status", "2")).isEqualTo("禁用");
        assertThat(cache.translate("status", "9")).isEqualTo("9"); // 未知值透传原值
        assertThat(cache.getItems("status")).hasSize(2);
        cache.refresh();
        cache.refresh("status");
    }

    @Test
    void dictUtilsShouldBindAndTranslate() {
        DictProvider provider = new DictProvider() {
            @Override
            public List<DictItem> getItems(String dictType) {
                return List.of(new DictItem("1", "启用"));
            }
        };
        DictCache cache = new DictCache(provider);
        DictUtils.bind(cache);
        assertThat(DictUtils.isReady()).isTrue();
        assertThat(DictUtils.translate("status", "1")).isEqualTo("启用");
        assertThat(DictUtils.getItems("status")).hasSize(1);
        DictUtils.refresh();
    }

    @Test
    void refTextCacheShouldStoreWithTtl() throws Exception {
        RefTextCache cache = new RefTextCache(60_000, 100);
        cache.put("user", 1L, "张三");
        assertThat(cache.contains("user", 1L)).isTrue();
        assertThat(cache.get("user", 1L)).isEqualTo("张三");
        cache.clear();
        assertThat(cache.get("user", 1L)).isNull();
    }
}

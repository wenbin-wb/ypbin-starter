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
package cn.ypbin.starter.cache.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.ypbin.starter.cache.core.CacheService;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 缓存静态工具测试：委托 CacheService 的读写删与 getOrLoad 兜底。
 *
 * @author wenbin
 * @since 2026-08-31
 */
class CacheUtilsTest {

    private final CacheService cacheService = mock(CacheService.class);

    @BeforeEach
    void setUp() throws Exception {
        // 反射注入 mock，绕开 SpringUtils 容器依赖
        java.lang.reflect.Field field = CacheUtils.class.getDeclaredField("cacheService");
        field.setAccessible(true);
        field.set(null, cacheService);
    }

    @Test
    void setShouldDelegate() {
        CacheUtils.set("k", "v");
        verify(cacheService).set("k", "v");
        CacheUtils.set("k", "v", Duration.ofSeconds(10));
        verify(cacheService).set("k", "v", Duration.ofSeconds(10));
    }

    @Test
    void getShouldReturnTypedValue() {
        when(cacheService.get("k", String.class)).thenReturn("value");
        assertThat(CacheUtils.get("k", String.class)).isEqualTo("value");
    }

    @Test
    void deleteShouldDelegate() {
        when(cacheService.delete("k")).thenReturn(true);
        assertThat(CacheUtils.delete("k")).isTrue();
        when(cacheService.delete(any(List.class))).thenReturn(2L);
        assertThat(CacheUtils.delete(List.of("a", "b"))).isEqualTo(2L);
    }

    @Test
    void existsShouldDelegate() {
        when(cacheService.exists("k")).thenReturn(true);
        assertThat(CacheUtils.exists("k")).isTrue();
    }

    @Test
    void getOrLoadShouldDelegateToService() {
        AtomicInteger loads = new AtomicInteger();
        when(cacheService.getOrLoad(anyString(), any(Class.class), any(), any(Duration.class)))
            .thenAnswer(inv -> "loaded-" + loads.incrementAndGet());
        String result = CacheUtils.getOrLoad(
            "k", String.class, () -> "never", Duration.ofSeconds(5));
        assertThat(result).isEqualTo("loaded-1");
    }

    @Test
    void compareAndDeleteShouldDelegate() {
        when(cacheService.compareAndDelete("k", "expected")).thenReturn(true);
        assertThat(CacheUtils.compareAndDelete("k", "expected")).isTrue();
    }
}

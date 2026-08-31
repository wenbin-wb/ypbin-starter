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
package cn.ypbin.starter.sensitivewords.util;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.sensitivewords.autoconfigure.SensitiveWordProperties;
import cn.ypbin.starter.sensitivewords.core.SensitiveWordService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;

/**
 * 敏感词工具与配置测试。
 *
 * @author wenbin
 * @since 2026-08-31
 */
class SensitiveWordUtilsTest {

    private SensitiveWordService service;

    @BeforeEach
    void setUp() throws Exception {
        // 注入带敏感词服务的容器到 SpringUtils
        service = org.mockito.Mockito.mock(SensitiveWordService.class);
        StaticApplicationContext context = new StaticApplicationContext();
        context.getBeanFactory().registerSingleton("sensitiveWordService", service);
        context.refresh();
        java.lang.reflect.Field field = cn.ypbin.starter.core.util.SpringUtils.class
            .getDeclaredField("applicationContext");
        field.setAccessible(true);
        field.set(null, context);
    }

    @Test
    void shouldDetectAndFilterWords() {
        org.mockito.Mockito.when(service.contains("这句话包含敏感词")).thenReturn(true);
        org.mockito.Mockito.when(service.findAll("敏感和测试")).thenReturn(List.of("敏感", "测试"));
        org.mockito.Mockito.when(service.filter("包含敏感词", '*')).thenReturn("包含**词");
        assertThat(SensitiveWordUtils.contains("这句话包含敏感词")).isTrue();
        assertThat(SensitiveWordUtils.findAll("敏感和测试")).containsExactly("敏感", "测试");
        assertThat(SensitiveWordUtils.filter("包含敏感词", '*')).isEqualTo("包含**词");
        SensitiveWordUtils.reload(List.of("敏感", "测试"));
    }

    @Test
    void shouldHandleNoWords() {
        org.mockito.Mockito.when(service.contains("干净文本")).thenReturn(false);
        org.mockito.Mockito.when(service.findAll("干净文本")).thenReturn(List.of());
        assertThat(SensitiveWordUtils.contains("干净文本")).isFalse();
        assertThat(SensitiveWordUtils.findAll("干净文本")).isEmpty();
        SensitiveWordUtils.reload(List.of());
    }

    @Test
    void propertiesShouldExposeDefaults() {
        SensitiveWordProperties props = new SensitiveWordProperties();
        assertThat(props.isEnabled()).isTrue();
        assertThat(props.getReplacement()).isEqualTo('*');
        assertThat(props.getWords()).isEmpty();
        assertThat(SensitiveWordProperties.PREFIX).isEqualTo("ypbin.sensitive-words");
    }
}

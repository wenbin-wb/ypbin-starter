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

import cn.ypbin.starter.json.dict.DictItem;
import org.junit.jupiter.api.Test;

/**
 * 配置属性与字典项模型测试。
 *
 * @author wenbin
 * @since 2026-08-31
 */
class JacksonPropertiesTest {

    @Test
    void shouldExposeDefaults() {
        JacksonProperties props = new JacksonProperties();
        assertThat(props.isEnabled()).isTrue();
        assertThat(props.isWriteBigNumberAsString()).isTrue();
        assertThat(props.getDateTimeFormat()).isEqualTo("yyyy-MM-dd HH:mm:ss");
        assertThat(props.getDateFormat()).isEqualTo("yyyy-MM-dd");
        assertThat(props.getTimeFormat()).isEqualTo("HH:mm:ss");
        assertThat(props.getRefText()).isNotNull();
        assertThat(props.getRefText().isAutoResolve()).isTrue();
        assertThat(props.getRefText().getTtlSeconds()).isGreaterThan(0);
        assertThat(props.getRefText().getMaxSize()).isGreaterThan(0);
    }

    @Test
    void shouldAllowOverride() {
        JacksonProperties props = new JacksonProperties();
        props.setEnabled(false);
        props.setDateTimeFormat("yyyy/MM/dd HH:mm");
        props.getRefText().setTtlSeconds(10L);
        assertThat(props.isEnabled()).isFalse();
        assertThat(props.getDateTimeFormat()).isEqualTo("yyyy/MM/dd HH:mm");
        assertThat(props.getRefText().getTtlSeconds()).isEqualTo(10L);
    }

    @Test
    void dictItemShouldCarryFields() {
        DictItem item = new DictItem("1", "启用");
        assertThat(item.getValue()).isEqualTo("1");
        assertThat(item.getLabel()).isEqualTo("启用");
        item.setColor("green");
        item.setSort(3);
        assertThat(item.getColor()).isEqualTo("green");
        assertThat(item.getSort()).isEqualTo(3);
        DictItem empty = new DictItem();
        assertThat(empty.getValue()).isNull();
    }
}

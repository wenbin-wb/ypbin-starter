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
package cn.ypbin.starter.crud.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link PageResult} 分页计算单元测试。
 *
 * @author wenbin
 * @since 2026-07-30
 */
class PageResultTest {

    @Test
    void pages_roundsUp() {
        // 25 条、每页 10 => 3 页
        assertThat(PageResult.of(List.of(), 25, 1, 10).getPages()).isEqualTo(3);
    }

    @Test
    void pages_exactMultiple() {
        // 20 条、每页 10 => 2 页
        assertThat(PageResult.of(List.of(), 20, 1, 10).getPages()).isEqualTo(2);
    }

    @Test
    void pages_zeroTotal() {
        assertThat(PageResult.of(List.of(), 0, 1, 10).getPages()).isEqualTo(0);
    }

    @Test
    void pages_zeroSize_avoidsDivideByZero() {
        assertThat(PageResult.of(List.of(), 100, 1, 0).getPages()).isEqualTo(0);
    }

    @Test
    void of_preservesFields() {
        PageResult<String> r = PageResult.of(List.of("a", "b"), 2, 1, 10);
        assertThat(r.getRecords()).containsExactly("a", "b");
        assertThat(r.getTotal()).isEqualTo(2);
        assertThat(r.getPage()).isEqualTo(1);
        assertThat(r.getSize()).isEqualTo(10);
    }

    @Test
    void nullRecords_becomesEmptyList() {
        PageResult<String> r = new PageResult<>(null, 0, 1, 10);
        assertThat(r.getRecords()).isNotNull().isEmpty();
    }
}

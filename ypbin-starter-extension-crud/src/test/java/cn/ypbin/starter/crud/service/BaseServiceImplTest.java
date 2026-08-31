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
package cn.ypbin.starter.crud.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.ypbin.starter.crud.model.PageQuery;
import cn.ypbin.starter.crud.model.PageResult;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 基础服务分页与排序校验测试。
 *
 * @author wenbin
 * @since 2026-08-31
 */
class BaseServiceImplTest {

    static class Demo {
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


    static class DemoService extends BaseServiceImpl<BaseMapper<Demo>, Demo> {
    }

    private void injectBaseMapper(DemoService service, BaseMapper<Demo> mapper) throws Exception {
        Class<?> current = service.getClass();
        java.lang.reflect.Field field = null;
        while (current != null && field == null) {
            try {
                field = current.getDeclaredField("baseMapper");
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        if (field == null) {
            throw new IllegalStateException("baseMapper 字段不存在");
        }
        field.setAccessible(true);
        field.set(service, mapper);
    }

    @Test
    void pageShouldReturnResult() throws Exception {
        DemoService service = new DemoService();
        @SuppressWarnings("unchecked")
        BaseMapper<Demo> mapper = mock(BaseMapper.class);
        when(mapper.selectPage(any(Page.class), any()))
            .thenAnswer(inv -> {
                Page<Demo> page = inv.getArgument(0);
                page.setRecords(List.of(new Demo()));
                page.setTotal(1);
                return page;
            });
        injectBaseMapper(service, mapper);

        PageQuery query = new PageQuery();
        query.setPage(1);
        query.setPageSize(10);
        PageResult<Demo> result = service.page(query);
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getTotal()).isEqualTo(1);
    }

    @Test
    void pageShouldRejectUnsafeSortColumn() {
        DemoService service = new DemoService();
        PageQuery query = new PageQuery();
        query.setPage(1);
        query.setPageSize(10);
        query.setSortField("name; drop table");
        assertThatThrownBy(() -> service.page(query))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pageShouldAcceptSafeSortColumn() throws Exception {
        DemoService service = new DemoService();
        @SuppressWarnings("unchecked")
        BaseMapper<Demo> mapper = mock(BaseMapper.class);
        when(mapper.selectPage(any(Page.class), any()))
            .thenAnswer(inv -> {
                Page<Demo> page = inv.getArgument(0);
                page.setRecords(List.of());
                page.setTotal(0);
                return page;
            });
        injectBaseMapper(service, mapper);

        PageQuery query = new PageQuery();
        query.setPage(1);
        query.setPageSize(10);
        query.setSortField("name");
        query.setAsc(false);
        PageResult<Demo> result = service.page(query);
        assertThat(result.getItems()).isEmpty();
    }
}

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
package cn.ypbin.starter.crud.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.ypbin.starter.crud.model.PageQuery;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.crud.service.BaseService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 分页查询参数校验测试。
 *
 * @author wenbin
 * @since 2026-08-09
 */
class PageQueryValidationTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController(mockService())).build();
    }

    @Test
    void acceptsBoundaryValues() throws Exception {
        mockMvc.perform(get("/test-pages").param("page", "1").param("pageSize", "1"))
            .andExpect(status().isOk());
        mockMvc.perform(get("/test-pages").param("page", "1").param("pageSize", "100"))
            .andExpect(status().isOk());
    }

    @Test
    void rejectsValuesOutsideBoundaries() throws Exception {
        mockMvc.perform(get("/test-pages").param("page", "0").param("pageSize", "10"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/test-pages").param("page", "1").param("pageSize", "0"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/test-pages").param("page", "1").param("pageSize", "101"))
            .andExpect(status().isBadRequest());
    }

    @SuppressWarnings("unchecked")
    private BaseService<TestEntity> mockService() {
        BaseService<TestEntity> service = Mockito.mock(BaseService.class);
        Mockito.when(service.page(Mockito.any(PageQuery.class), Mockito.any()))
            .thenReturn(PageResult.of(List.of(), 0, 1, 10));
        return service;
    }

    static class TestEntity {
    }

    @RestController
    @RequestMapping("/test-pages")
    static class TestController
        extends CrudController<TestEntity, Long, TestEntity, TestEntity, PageQuery> {

        private final BaseService<TestEntity> service;

        TestController(BaseService<TestEntity> service) {
            this.service = service;
        }

        @Override
        protected BaseService<TestEntity> getBaseService() {
            return service;
        }
    }
}

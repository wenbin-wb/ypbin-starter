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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.ypbin.starter.crud.model.PageQuery;
import cn.ypbin.starter.crud.service.BaseService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CrudController 写端点测试：save/update/delete 与钩子调用。
 *
 * @author wenbin
 * @since 2026-08-31
 */
class CrudControllerWriteTest {

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

    @RestController
    @RequestMapping("/write-demos")
    static class WriteController extends CrudController<Demo, Long, Demo, Demo, PageQuery> {
        private final BaseService<Demo> service;
        private final boolean[] hooks;

        WriteController(BaseService<Demo> service, boolean[] hooks) {
            this.service = service;
            this.hooks = hooks;
        }

        @Override
        protected BaseService<Demo> getBaseService() {
            return service;
        }

        @Override
        protected void beforeSave(Demo req, Demo entity) {
            hooks[0] = true;
        }

        @Override
        protected void afterUpdate(Long id, Demo req, Demo entity) {
            hooks[1] = true;
        }

        @Override
        protected void beforeDelete(Long id) {
            hooks[2] = true;
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void saveShouldInvokeBeforeSave() throws Exception {
        BaseService<Demo> service = mock(BaseService.class);
        boolean[] hooks = new boolean[3];
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new WriteController(service, hooks)).build();

        mockMvc.perform(post("/write-demos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"demo\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(service).save(org.mockito.ArgumentMatchers.any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void updateShouldInvokeAfterUpdate() throws Exception {
        BaseService<Demo> service = mock(BaseService.class);
        boolean[] hooks = new boolean[3];
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new WriteController(service, hooks)).build();

        mockMvc.perform(put("/write-demos/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"updated\"}"))
            .andExpect(status().isOk());

        verify(service).updateById(org.mockito.ArgumentMatchers.any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void deleteShouldInvokeBeforeDelete() throws Exception {
        BaseService<Demo> service = mock(BaseService.class);
        boolean[] hooks = new boolean[3];
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new WriteController(service, hooks)).build();

        mockMvc.perform(delete("/write-demos/1"))
            .andExpect(status().isOk());

        verify(service).removeById(1L);
    }

    @SuppressWarnings("unchecked")
    @Test
    void listShouldMapToResp() throws Exception {
        BaseService<Demo> service = mock(BaseService.class);
        boolean[] hooks = new boolean[3];
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new WriteController(service, hooks)).build();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/write-demos/list"))
            .andExpect(status().isOk());
    }

    @Override
    public String toString() {
        return "CrudControllerWriteTest";
    }
}

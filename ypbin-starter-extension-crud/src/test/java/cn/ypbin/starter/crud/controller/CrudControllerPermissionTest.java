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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.ypbin.starter.crud.model.PageQuery;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.crud.service.BaseService;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 验证 {@link CrudController} 的权限前缀机制。
 *
 * <p>本模块类路径无 Sa-Token，用于验证：覆盖 {@code permissionPrefix()} 后端点会调用 {@code checkPermission}
 * 拼出正确权限码；而 Sa-Token 不在类路径时静默跳过、不抛异常（端点正常放行）。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
class CrudControllerPermissionTest {

    /** 记录 checkPermission 实际拼出的权限码，间接验证前缀+动作 */
    static final AtomicReference<String> LAST_ACTION = new AtomicReference<>();

    static class Demo {
    }

    /** 覆盖 permissionPrefix 的控制器；同时覆盖 checkPermission 观测拼码（不依赖真实 Sa-Token） */
    @RestController
    @RequestMapping("/perm-demos")
    static class PrefixedController extends CrudController<Demo, Long, Demo, Demo, PageQuery> {
        private final BaseService<Demo> service;

        PrefixedController(BaseService<Demo> service) {
            this.service = service;
        }

        @Override
        protected BaseService<Demo> getBaseService() {
            return service;
        }

        @Override
        protected String permissionPrefix() {
            return "system:demo";
        }

        @Override
        protected void checkPermission(String action) {
            LAST_ACTION.set(permissionPrefix() + ":" + action);
            super.checkPermission(action); // 走真实反射：无 sa-token 应静默跳过
        }
    }

    /** 不覆盖 permissionPrefix 的控制器（默认 null，不校验） */
    @RestController
    @RequestMapping("/plain-demos")
    static class PlainController extends CrudController<Demo, Long, Demo, Demo, PageQuery> {
        private final BaseService<Demo> service;

        PlainController(BaseService<Demo> service) {
            this.service = service;
        }

        @Override
        protected BaseService<Demo> getBaseService() {
            return service;
        }
    }

    @SuppressWarnings("unchecked")
    private BaseService<Demo> mockService() {
        BaseService<Demo> service = Mockito.mock(BaseService.class);
        Mockito.when(service.page(Mockito.any(PageQuery.class), Mockito.any()))
            .thenReturn(PageResult.of(java.util.List.of(), 0, 1, 10));
        Mockito.when(service.list()).thenReturn(java.util.List.of());
        return service;
    }

    @Test
    void prefixedEndpointsComputePermissionCode() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PrefixedController(mockService())).build();

        LAST_ACTION.set(null);
        mockMvc.perform(get("/perm-demos")).andExpect(status().isOk());
        // 分页端点走 list 动作，前缀+动作拼接正确
        assertThat(LAST_ACTION.get()).isEqualTo("system:demo:list");

        LAST_ACTION.set(null);
        mockMvc.perform(get("/perm-demos/list")).andExpect(status().isOk());
        assertThat(LAST_ACTION.get()).isEqualTo("system:demo:list");
    }

    @Test
    void noSaTokenDoesNotBreakEndpoints() throws Exception {
        // 无 Sa-Token 时 super.checkPermission 静默跳过，端点仍正常放行
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PrefixedController(mockService())).build();
        mockMvc.perform(get("/perm-demos")).andExpect(status().isOk());
    }

    @Test
    void defaultPrefixIsNullNoCheck() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PlainController(mockService())).build();
        // 未覆盖 permissionPrefix，端点正常放行
        mockMvc.perform(get("/plain-demos")).andExpect(status().isOk());
        mockMvc.perform(get("/plain-demos/list")).andExpect(status().isOk());
    }
}

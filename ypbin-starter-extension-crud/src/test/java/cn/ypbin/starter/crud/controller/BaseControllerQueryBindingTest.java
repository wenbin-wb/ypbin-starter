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
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 验证 {@link CrudController} 泛型查询参数 {@code Q} 能被 Spring 绑定到具体子类型的字段。
 *
 * <p>核心断言：请求携带 {@code username=tom&status=1}，控制器 {@code buildQueryWrapper} 收到的
 * {@code DemoQuery} 应能取到这两个字段值——证明泛型 Q 经桥接方法正确参与了参数绑定，
 * 而非退化为基类 {@link PageQuery} 导致过滤字段丢失。用 standalone MockMvc，不依赖 Boot 上下文。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
class BaseControllerQueryBindingTest {

    /** 记录 buildQueryWrapper 实际收到的查询对象 */
    static final AtomicReference<Object> CAPTURED = new AtomicReference<>();

    /** 演示实体 */
    static class Demo {
    }

    /** 携带业务过滤字段的查询对象 */
    public static class DemoQuery extends PageQuery {
        private String username;
        private Integer status;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public Integer getStatus() {
            return status;
        }

        public void setStatus(Integer status) {
            this.status = status;
        }
    }

    /** 继承 CrudController，Q 指定为具体的 DemoQuery */
    @RestController
    @RequestMapping("/demos")
    static class DemoController extends CrudController<Demo, Long, Demo, Demo, DemoQuery> {
        private final BaseService<Demo> service;

        DemoController(BaseService<Demo> service) {
            this.service = service;
        }

        @Override
        protected BaseService<Demo> getBaseService() {
            return service;
        }

        @Override
        protected Wrapper<Demo> buildQueryWrapper(DemoQuery query) {
            CAPTURED.set(query);
            return null;
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void pageShouldBindConcreteQueryFields() throws Exception {
        CAPTURED.set(null);
        BaseService<Demo> service = Mockito.mock(BaseService.class);
        Mockito.when(service.page(Mockito.any(PageQuery.class), Mockito.<Wrapper<Demo>>any()))
            .thenReturn(PageResult.of(java.util.List.of(), 0, 1, 10));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DemoController(service)).build();

        mockMvc.perform(get("/demos")
                .param("page", "2").param("pageSize", "20")
                .param("username", "tom").param("status", "1"))
            .andExpect(status().isOk());

        Object captured = CAPTURED.get();
        assertThat(captured).isInstanceOf(DemoQuery.class);
        DemoQuery q = (DemoQuery) captured;
        // 关键：业务过滤字段确实被绑定，而非退化为基类丢失
        assertThat(q.getUsername()).isEqualTo("tom");
        assertThat(q.getStatus()).isEqualTo(1);
        // 基类分页字段同样正确
        assertThat(q.getPage()).isEqualTo(2);
        assertThat(q.getPageSize()).isEqualTo(20);
    }
}

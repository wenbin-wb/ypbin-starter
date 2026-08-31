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

import cn.ypbin.starter.crud.model.PageQuery;
import cn.ypbin.starter.crud.service.BaseService;
import org.junit.jupiter.api.Test;

/**
 * CrudController 模型转换测试：REQ→实体、实体→RESP 的同名拷贝。
 *
 * @author wenbin
 * @since 2026-08-31
 */
class CrudControllerConversionTest {

    static class Entity {
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

    static class Req {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    static class Resp {
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

    static class ConvertController extends CrudController<Entity, Long, Req, Resp, PageQuery> {
        private final BaseService<Entity> service;

        ConvertController(BaseService<Entity> service) {
            this.service = service;
        }

        @Override
        protected BaseService<Entity> getBaseService() {
            return service;
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void toEntityShouldCopySameNameFields() {
        ConvertController controller = new ConvertController(
            org.mockito.Mockito.mock(BaseService.class));
        Req req = new Req();
        req.setName("demo");

        Entity entity = controller.toEntity(req);

        assertThat(entity).isNotNull();
        assertThat(entity.getName()).isEqualTo("demo");
    }

    @SuppressWarnings("unchecked")
    @Test
    void toRespShouldCopySameNameFields() {
        ConvertController controller = new ConvertController(
            org.mockito.Mockito.mock(BaseService.class));
        Entity entity = new Entity();
        entity.setId(1L);
        entity.setName("demo");

        Resp resp = controller.toResp(entity);

        assertThat(resp).isNotNull();
        assertThat(resp.getId()).isEqualTo(1L);
        assertThat(resp.getName()).isEqualTo("demo");
    }

}

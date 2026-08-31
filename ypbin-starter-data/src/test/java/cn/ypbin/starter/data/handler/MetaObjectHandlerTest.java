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
package cn.ypbin.starter.data.handler;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.Test;

/**
 * 审计字段自动填充测试：插入/更新时创建人、时间等字段应被填充。
 *
 * @author wenbin
 * @since 2026-08-31
 */
class MetaObjectHandlerTest {

    @com.baomidou.mybatisplus.annotation.TableName("audit_entity")
    static class AuditEntity {
        @com.baomidou.mybatisplus.annotation.TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
        private LocalDateTime createTime;
        @com.baomidou.mybatisplus.annotation.TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
        private LocalDateTime updateTime;
        @com.baomidou.mybatisplus.annotation.TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
        private Long createUser;
        @com.baomidou.mybatisplus.annotation.TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
        private Long updateUser;

        public LocalDateTime getCreateTime() {
            return createTime;
        }

        public void setCreateTime(LocalDateTime createTime) {
            this.createTime = createTime;
        }

        public LocalDateTime getUpdateTime() {
            return updateTime;
        }

        public void setUpdateTime(LocalDateTime updateTime) {
            this.updateTime = updateTime;
        }

        public Long getCreateUser() {
            return createUser;
        }

        public void setCreateUser(Long createUser) {
            this.createUser = createUser;
        }

        public Long getUpdateUser() {
            return updateUser;
        }

        public void setUpdateUser(Long updateUser) {
            this.updateUser = updateUser;
        }
    }

    @Test
    void insertFillShouldSetTimeAndAuditor() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
            new org.apache.ibatis.builder.MapperBuilderAssistant(new org.apache.ibatis.session.Configuration(), ""),
            AuditEntity.class);
        DefaultMetaObjectHandler handler =
            new DefaultMetaObjectHandler(() -> java.util.Optional.of(42L));
        AuditEntity entity = new AuditEntity();
        MetaObject metaObject = SystemMetaObject.forObject(entity);

        handler.insertFill(metaObject);

        assertThat(entity.getCreateTime()).isNotNull();
        assertThat(entity.getUpdateTime()).isNotNull();
        assertThat(entity.getCreateUser()).isEqualTo(42L);
        assertThat(entity.getUpdateUser()).isEqualTo(42L);
    }

    @Test
    void insertFillShouldSkipAuditorWhenAbsent() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
            new org.apache.ibatis.builder.MapperBuilderAssistant(new org.apache.ibatis.session.Configuration(), ""),
            AuditEntity.class);
        DefaultMetaObjectHandler handler =
            new DefaultMetaObjectHandler(java.util.Optional::empty);
        AuditEntity entity = new AuditEntity();
        MetaObject metaObject = SystemMetaObject.forObject(entity);

        handler.insertFill(metaObject);

        assertThat(entity.getCreateTime()).isNotNull();
        assertThat(entity.getCreateUser()).isNull();
    }

    @Test
    void updateFillShouldForceRefreshTime() {
        DefaultMetaObjectHandler handler =
            new DefaultMetaObjectHandler(() -> java.util.Optional.of(7L));
        AuditEntity entity = new AuditEntity();
        entity.setCreateTime(LocalDateTime.now().minusDays(1));
        MetaObject metaObject = SystemMetaObject.forObject(entity);

        handler.updateFill(metaObject);

        assertThat(entity.getUpdateTime()).isNotNull();
        assertThat(entity.getUpdateUser()).isEqualTo(7L);
    }
}

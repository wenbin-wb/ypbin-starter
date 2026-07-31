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
package cn.ypbin.starter.data.core;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体基类，携带主键、通用审计字段与逻辑删除标记。
 *
 * <p>主键 {@link #id} 默认用雪花算法（{@link IdType#ASSIGN_ID}），业务实体可在自己的字段上重写
 * {@code @TableId} 注解改用自增、UUID 等策略；主键类型由泛型 {@code <ID>} 指定（Long / String 等）。
 * 创建人/时间、更新人/时间由 {@link cn.ypbin.starter.data.handler.DefaultMetaObjectHandler} 自动填充。</p>
 *
 * <p>逻辑删除字段 {@link #deleted} 配合 MyBatis-Plus 的 {@code @TableLogic} 生效（默认 0 未删、1 已删），
 * 需在配置中开启逻辑删除全局规则。不希望逻辑删除的表，其对应实体可不继承本类或忽略该字段。</p>
 *
 * @param <ID> 主键类型
 * @author wenbin
 * @since 2026-07-30
 */
public abstract class BaseEntity<ID extends Serializable> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键，默认雪花算法生成；业务实体可重写 @TableId 改用其它策略 */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private ID id;

    /** 创建人 */
    @TableField(value = "create_user", fill = FieldFill.INSERT)
    private Long createUser;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新人 */
    @TableField(value = "update_user", fill = FieldFill.INSERT_UPDATE)
    private Long updateUser;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除标记：0 未删除、1 已删除。需开启逻辑删除规则后生效 */
    @TableLogic
    @TableField(value = "deleted")
    private Integer deleted;

    public ID getId() {
        return id;
    }

    public void setId(ID id) {
        this.id = id;
    }

    public Long getCreateUser() {
        return createUser;
    }

    public void setCreateUser(Long createUser) {
        this.createUser = createUser;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public Long getUpdateUser() {
        return updateUser;
    }

    public void setUpdateUser(Long updateUser) {
        this.updateUser = updateUser;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}

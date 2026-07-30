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
import com.baomidou.mybatisplus.annotation.TableField;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体基类，携带通用审计字段。
 *
 * <p>创建人/创建时间/更新人/更新时间由 {@link cn.ypbin.starter.data.handler.DefaultMetaObjectHandler}
 * 自动填充，业务实体继承本类即可获得审计能力。主键交由具体实体声明，避免绑定固定的 ID 策略。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public abstract class BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

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
}

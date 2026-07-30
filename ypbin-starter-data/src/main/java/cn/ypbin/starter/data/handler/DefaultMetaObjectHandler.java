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

import cn.ypbin.starter.data.core.AuditorProvider;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import java.time.LocalDateTime;
import org.apache.ibatis.reflection.MetaObject;

/**
 * 审计字段自动填充处理器。
 *
 * <p>在 INSERT / UPDATE 时自动填充创建人、创建时间、更新人、更新时间。
 * 操作人 ID 通过 {@link AuditorProvider} 获取，避免与安全模块强耦合。</p>
 *
 * <p>注意：更新场景使用 {@link #setFieldValByName} 强制覆盖，而非 {@code strictUpdateFill}。
 * 因为 strict 系列方法在字段已有值（如先查询再更新）时会跳过填充，导致更新时间/更新人
 * 无法刷新。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class DefaultMetaObjectHandler implements MetaObjectHandler {

    private static final String CREATE_TIME = "createTime";
    private static final String CREATE_USER = "createUser";
    private static final String UPDATE_TIME = "updateTime";
    private static final String UPDATE_USER = "updateUser";

    private final AuditorProvider auditorProvider;

    public DefaultMetaObjectHandler(AuditorProvider auditorProvider) {
        this.auditorProvider = auditorProvider;
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        Long auditor = currentAuditor();
        strictInsertFill(metaObject, CREATE_TIME, LocalDateTime.class, now);
        strictInsertFill(metaObject, UPDATE_TIME, LocalDateTime.class, now);
        if (auditor != null) {
            strictInsertFill(metaObject, CREATE_USER, Long.class, auditor);
            strictInsertFill(metaObject, UPDATE_USER, Long.class, auditor);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // 更新场景强制覆盖：即便字段已有旧值也要刷新为最新
        setFieldValByName(UPDATE_TIME, LocalDateTime.now(), metaObject);
        Long auditor = currentAuditor();
        if (auditor != null) {
            setFieldValByName(UPDATE_USER, auditor, metaObject);
        }
    }

    private Long currentAuditor() {
        return auditorProvider.getCurrentAuditor().orElse(null);
    }
}

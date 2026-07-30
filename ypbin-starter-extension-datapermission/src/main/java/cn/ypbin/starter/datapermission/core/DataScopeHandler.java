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
package cn.ypbin.starter.datapermission.core;

/**
 * 数据范围处理扩展点。
 *
 * <p>数据权限规则高度依赖业务（按部门、角色、本人、自定义等），本模块不预设规则，
 * 而是让业务方实现本接口，返回一段 SQL 条件片段（如 {@code dept_id IN (1,2,3)}），
 * 模块内部将其解析并追加到查询的 WHERE。返回 {@code null} 或空串表示当前查询不做数据范围限制。</p>
 *
 * <p>相比让业务方直接操作 JSqlParser 的 Expression，返回 SQL 字符串更直观易用；
 * 需要精细控制时仍可自行实现 MyBatis-Plus 的处理器覆盖本模块的适配 Bean。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@FunctionalInterface
public interface DataScopeHandler {

    /**
     * 计算数据范围 SQL 条件片段。
     *
     * @param mappedStatementId Mapper 方法全限定名（可据此按方法定制规则）
     * @param tableName         当前查询主表名
     * @return SQL 条件片段（不含 WHERE 关键字），无限制时返回 {@code null}
     */
    String getDataScopeSql(String mappedStatementId, String tableName);
}

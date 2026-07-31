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

import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.crud.model.PageQuery;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.crud.service.BaseService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.BeanUtils;
import org.springframework.core.GenericTypeResolver;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 通用 REST 控制器基类。
 *
 * <p>提供标准增删改查与分页接口。为规避 Over-Posting（过度提交）风险，接口层严格区分三类模型：
 * <ul>
 *     <li>{@code REQ} —— 接收前端提交的请求参数，只暴露允许写入的字段；</li>
 *     <li>{@code RESP} —— 返回给前端的视图，可隐藏敏感字段；</li>
 *     <li>{@code T} —— 数据库实体，永不直接暴露给前端。</li>
 * </ul>
 * REQ/RESP 与实体的转换默认用 {@link BeanUtils} 按同名字段拷贝（简单场景零成本），
 * 需要精细控制（字段改名、脱敏、MapStruct 等）时在子类覆盖 {@link #toEntity} / {@link #toResp}。</p>
 *
 * <p>若某实体无需区分模型，可将 REQ/RESP 直接指定为实体类型 T。</p>
 *
 * <p><b>操作级鉴权：</b>各端点方法为 {@code public} 可覆盖，子类只需 {@code @Override} 并挂上权限注解、
 * 方法体调用 {@code super.xxx(...)} 即可在复用父类逻辑的同时获得细粒度鉴权：
 * <pre>{@code
 * @Override
 * @SaCheckPermission("system:user:add")
 * public R<Void> save(@RequestBody UserReq req) {
 *     return super.save(req);
 * }
 * }</pre>
 *
 * <p><b>业务过滤：</b>查询参数类型由泛型 {@code Q} 指定（{@link PageQuery} 或其携带过滤字段的子类），
 * 子类覆盖 {@link #buildQueryWrapper(PageQuery)} 返回查询条件即可按业务字段过滤。分页端点形参声明为 {@code Q}，
 * Spring 经桥接方法绑定到具体子类型，故 {@code username}/{@code status} 等过滤字段能正确注入。
 * 无需业务过滤时 {@code Q} 直接用 {@code PageQuery}。</p>
 *
 * <p><b>写操作扩展：</b>覆盖 {@link #beforeSave}/{@link #afterSave}/{@link #beforeUpdate}/{@link #afterUpdate}
 * 模板钩子插入密码加密、查重、事务内分配角色等业务逻辑；需要事务时在子类覆盖方法上加 {@code @Transactional}。</p>
 *
 * @param <T>    数据库实体类型
 * @param <ID>   主键类型
 * @param <REQ>  请求参数类型
 * @param <RESP> 响应视图类型
 * @param <Q>    分页查询参数类型（{@link PageQuery} 或其子类）
 * @author wenbin
 * @since 2026-07-30
 */
public abstract class BaseController<T, ID extends Serializable, REQ, RESP, Q extends PageQuery> {

    /** 泛型参数解析结果缓存，避免每次请求都反射解析 */
    private static final Map<Class<?>, Class<?>[]> TYPE_ARG_CACHE = new ConcurrentHashMap<>();

    /**
     * 提供业务服务实例。
     *
     * @return 业务服务
     */
    protected abstract BaseService<T> getBaseService();

    @GetMapping("/{id}")
    public R<RESP> getById(@PathVariable ID id) {
        return R.ok(toResp(getBaseService().getById(id)));
    }

    @GetMapping
    public R<List<RESP>> list() {
        return R.ok(getBaseService().list().stream().map(this::toResp).toList());
    }

    @GetMapping("/page")
    public R<PageResult<RESP>> page(Q query) {
        PageResult<T> source = getBaseService().page(query, buildQueryWrapper(query));
        PageResult<RESP> view = PageResult.of(
            source.getRecords().stream().map(this::toResp).toList(),
            source.getTotal(), source.getPage(), source.getSize());
        return R.ok(view);
    }

    @PostMapping
    public R<Void> save(@RequestBody REQ req) {
        T entity = toEntity(req);
        beforeSave(req, entity);
        getBaseService().save(entity);
        afterSave(req, entity);
        return R.ok();
    }

    @PutMapping
    public R<Void> update(@RequestBody REQ req) {
        T entity = toEntity(req);
        beforeUpdate(req, entity);
        getBaseService().updateById(entity);
        afterUpdate(req, entity);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable ID id) {
        beforeDelete(id);
        getBaseService().removeById(id);
        afterDelete(id);
        return R.ok();
    }

    /**
     * 构建分页查询条件。默认返回 {@code null}（无业务过滤），子类覆盖以按业务字段过滤。
     *
     * <p>{@code query} 为泛型 {@code Q} 的实例，携带子类声明的过滤字段（如 {@code username}/{@code status}），
     * 例：{@code return Wrappers.<User>lambdaQuery().like(has(q.getName()), User::getName, q.getName());}</p>
     *
     * @param query 分页查询参数（含业务过滤字段）
     * @return 查询条件，{@code null} 表示无条件
     */
    protected Wrapper<T> buildQueryWrapper(Q query) {
        return null;
    }

    /**
     * 保存前置钩子。默认空实现，子类覆盖以插入密码加密、字段查重等逻辑。
     *
     * @param req    请求参数
     * @param entity 待保存实体（已由 {@link #toEntity} 转换）
     */
    protected void beforeSave(REQ req, T entity) {
    }

    /**
     * 保存后置钩子。默认空实现，子类覆盖以分配角色/菜单等关联写入（可在覆盖方法加 {@code @Transactional}）。
     *
     * @param req    请求参数
     * @param entity 已保存实体（此时主键已回填）
     */
    protected void afterSave(REQ req, T entity) {
    }

    /**
     * 更新前置钩子。默认空实现，子类覆盖以处理密码留空不更新、查重等逻辑。
     *
     * @param req    请求参数
     * @param entity 待更新实体
     */
    protected void beforeUpdate(REQ req, T entity) {
    }

    /**
     * 更新后置钩子。默认空实现。
     *
     * @param req    请求参数
     * @param entity 已更新实体
     */
    protected void afterUpdate(REQ req, T entity) {
    }

    /**
     * 删除前置钩子。默认空实现，子类覆盖以做关联校验（如存在子节点不允许删）。
     *
     * @param id 主键
     */
    protected void beforeDelete(ID id) {
    }

    /**
     * 删除后置钩子。默认空实现，子类覆盖以清理关联数据。
     *
     * @param id 主键
     */
    protected void afterDelete(ID id) {
    }

    /**
     * 请求参数转数据库实体。默认按同名字段拷贝，子类可覆盖以定制映射。
     *
     * @param req 请求参数
     * @return 数据库实体
     */
    @SuppressWarnings("unchecked")
    protected T toEntity(REQ req) {
        if (req == null) {
            return null;
        }
        Class<T> entityType = resolveTypeArg(0);
        // REQ 本身即实体时直接返回，避免无谓拷贝
        if (entityType.isInstance(req)) {
            return (T) req;
        }
        T entity = instantiate(entityType);
        BeanUtils.copyProperties(req, entity);
        return entity;
    }

    /**
     * 数据库实体转响应视图。默认按同名字段拷贝，子类可覆盖以定制映射与脱敏。
     *
     * @param entity 数据库实体
     * @return 响应视图
     */
    @SuppressWarnings("unchecked")
    protected RESP toResp(T entity) {
        if (entity == null) {
            return null;
        }
        Class<RESP> respType = resolveTypeArg(3);
        // RESP 即实体类型时直接返回
        if (respType.isInstance(entity)) {
            return (RESP) entity;
        }
        RESP resp = instantiate(respType);
        BeanUtils.copyProperties(entity, resp);
        return resp;
    }

    @SuppressWarnings("unchecked")
    private <X> Class<X> resolveTypeArg(int index) {
        // 用 Spring 的解析器：可跨多层继承、并正确处理 CGLIB 代理类（控制器被 @Log/@Transactional 等代理时
        // getClass().getGenericSuperclass() 会失效）。首次解析结果按类缓存。
        Class<?>[] args = TYPE_ARG_CACHE.computeIfAbsent(getClass(),
            clazz -> GenericTypeResolver.resolveTypeArguments(clazz, BaseController.class));
        if (args == null || index >= args.length || args[index] == null) {
            throw new IllegalStateException("无法解析泛型类型参数，请在子类覆盖 toEntity/toResp 方法");
        }
        return (Class<X>) args[index];
    }

    private <X> X instantiate(Class<X> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("无法实例化 " + type.getName() + "，请提供无参构造或覆盖转换方法", e);
        }
    }
}

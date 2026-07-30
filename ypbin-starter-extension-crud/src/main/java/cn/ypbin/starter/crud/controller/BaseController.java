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
 * @param <T>    数据库实体类型
 * @param <ID>   主键类型
 * @param <REQ>  请求参数类型
 * @param <RESP> 响应视图类型
 * @author wenbin
 * @since 2026-07-30
 */
public abstract class BaseController<T, ID extends Serializable, REQ, RESP> {

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
    public R<PageResult<RESP>> page(PageQuery query) {
        PageResult<T> source = getBaseService().page(query);
        PageResult<RESP> view = PageResult.of(
            source.getRecords().stream().map(this::toResp).toList(),
            source.getTotal(), source.getPage(), source.getSize());
        return R.ok(view);
    }

    @PostMapping
    public R<Void> save(@RequestBody REQ req) {
        getBaseService().save(toEntity(req));
        return R.ok();
    }

    @PutMapping
    public R<Void> update(@RequestBody REQ req) {
        getBaseService().updateById(toEntity(req));
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable ID id) {
        getBaseService().removeById(id);
        return R.ok();
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

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
import jakarta.validation.Valid;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
 * 标准 CRUD 控制器抽象类。
 *
 * <p>本类面向「接口形态稳定、业务逻辑较轻」的资源，提供 get/list/page/save/update/delete
 * 六个标准端点。复杂业务、非标准端点或需要精细编排鉴权的控制器，建议继承 {@link BaseController}
 * 后自行声明路由。</p>
 *
 * <p>为规避 Over-Posting（过度提交）风险，接口层严格区分三类模型：</p>
 * <ul>
 *     <li>{@code REQ} —— 接收前端提交的请求参数，只暴露允许写入的字段；</li>
 *     <li>{@code RESP} —— 返回给前端的视图，可隐藏敏感字段；</li>
 *     <li>{@code T} —— 数据库实体，永不直接暴露给前端。</li>
 * </ul>
 *
 * <p>REQ/RESP 与实体的转换默认用 {@link BeanUtils} 按同名字段拷贝（简单场景零成本）。
 * 需要精细控制时覆盖 {@link #toEntity} / {@link #toResp}；若某实体无需区分模型，REQ/RESP
 * 可直接指定为实体类型 T。</p>
 *
 * <p>查询参数类型由泛型 {@code Q} 指定（{@link PageQuery} 或其携带过滤字段的子类），
 * 覆盖 {@link #buildQueryWrapper(PageQuery)} 返回查询条件即可按业务字段过滤。</p>
 *
 * <p><b>权限校验：</b>覆盖 {@link #permissionPrefix()} 返回权限前缀（如 {@code "system:user"}），六个端点即
 * 自动按 {@code 前缀:list/add/edit/delete} 校验权限——一次声明、全端点覆盖，避免逐个端点挂注解时漏挂越权。
 * 默认不校验（仅受全局登录拦截）；也可继续用 {@code @Override} + {@code @SaCheckPermission} 精细控制。</p>
 *
 * @param <T>    数据库实体类型
 * @param <ID>   主键类型
 * @param <REQ>  请求参数类型
 * @param <RESP> 响应视图类型
 * @param <Q>    分页查询参数类型（{@link PageQuery} 或其子类）
 * @author wenbin
 * @since 2026-08-01
 */
public abstract class CrudController<T, ID extends Serializable, REQ, RESP, Q extends PageQuery>
    extends BaseController {

    /** 泛型参数解析结果缓存，避免每次请求都反射解析 */
    private static final Map<Class<?>, Class<?>[]> TYPE_ARG_CACHE = new ConcurrentHashMap<>();

    /** Sa-Token 权限校验入口，反射调用以避免 crud 模块强依赖 security/sa-token */
    private static final String STP_UTIL_CLASS = "cn.dev33.satoken.stp.StpUtil";

    /** 权限动作：查询详情/列表/分页归为 list（读），写操作各自区分 */
    private static final String ACTION_LIST = "list";
    private static final String ACTION_ADD = "add";
    private static final String ACTION_EDIT = "edit";
    private static final String ACTION_DELETE = "delete";

    /**
     * 提供业务服务实例。
     *
     * @return 业务服务
     */
    protected abstract BaseService<T> getBaseService();

    @GetMapping("/{id}")
    public R<RESP> get(@PathVariable ID id) {
        checkPermission(ACTION_LIST);
        return ok(toResp(getBaseService().getById(id)));
    }

    @GetMapping("/list")
    public R<List<RESP>> list() {
        checkPermission(ACTION_LIST);
        return ok(getBaseService().list().stream().map(this::toResp).toList());
    }

    @GetMapping
    public R<PageResult<RESP>> page(@Valid Q query) {
        checkPermission(ACTION_LIST);
        PageResult<T> source = getBaseService().page(query, buildQueryWrapper(query));
        PageResult<RESP> view = PageResult.of(
            source.getItems().stream().map(this::toResp).toList(),
            source.getTotal(), source.getPage(), source.getPageSize());
        return ok(view);
    }

    @PostMapping
    public R<Void> save(@RequestBody REQ req) {
        checkPermission(ACTION_ADD);
        T entity = toEntity(req);
        beforeSave(req, entity);
        getBaseService().save(entity);
        afterSave(req, entity);
        return ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable ID id, @RequestBody REQ req) {
        checkPermission(ACTION_EDIT);
        T entity = toEntity(req);
        beforeUpdate(id, req, entity);
        getBaseService().updateById(entity);
        afterUpdate(id, req, entity);
        return ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable ID id) {
        checkPermission(ACTION_DELETE);
        beforeDelete(id);
        getBaseService().removeById(id);
        afterDelete(id);
        return ok();
    }

    /**
     * 权限码前缀。返回非空即为六个标准端点自动挂权限校验：
     * {@code 前缀:list}（get/list/page）、{@code 前缀:add}（save）、{@code 前缀:edit}（update）、
     * {@code 前缀:delete}（delete）。例如返回 {@code "system:user"} 则分页需 {@code system:user:list}。
     *
     * <p><b>安全默认值意图：</b>默认返回 {@code null}（不校验，仅受全局登录拦截约束），保持向后兼容与
     * 「非受保护资源」场景可用；但对后台受保护资源，只需覆盖本方法返回前缀，即可让全部端点一次性获得
     * 细粒度权限校验，避免逐个 {@code @Override} 端点挂 {@code @SaCheckPermission} 时漏挂导致的越权风险。</p>
     *
     * <p>仍可继续用「{@code @Override} 端点 + {@code @SaCheckPermission} + {@code super.xxx()}」做更精细控制，
     * 两者可共存；本机制依赖 Sa-Token，未引入 security/sa-token 时自动跳过（不报错）。</p>
     *
     * @return 权限码前缀；{@code null} 或空表示不自动校验
     */
    protected String permissionPrefix() {
        return null;
    }

    /**
     * 按权限前缀 + 动作自动校验权限。前缀为空或 Sa-Token 不在类路径时静默跳过。
     *
     * @param action 动作（list/add/edit/delete）
     */
    protected void checkPermission(String action) {
        String prefix = permissionPrefix();
        if (prefix == null || prefix.isBlank()) {
            return;
        }
        try {
            Class<?> stpUtil = Class.forName(STP_UTIL_CLASS);
            Method method = stpUtil.getMethod("checkPermission", String.class);
            method.invoke(null, prefix + ":" + action);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            // 未引入 sa-token：不做权限校验（仅受全局登录拦截约束）
        } catch (InvocationTargetException e) {
            // Sa-Token 抛出的鉴权异常（无权限）需向上传播，交全局异常处理器转 403
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(cause);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 构建分页查询条件。默认返回 {@code null}（无业务过滤），子类覆盖以按业务字段过滤。
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
     * @param entity 待保存实体
     */
    protected void beforeSave(REQ req, T entity) {
    }

    /**
     * 保存后置钩子。默认空实现。
     *
     * @param req    请求参数
     * @param entity 已保存实体
     */
    protected void afterSave(REQ req, T entity) {
    }

    /**
     * 更新前置钩子。默认空实现。
     *
     * @param id     主键
     * @param req    请求参数
     * @param entity 待更新实体
     */
    protected void beforeUpdate(ID id, REQ req, T entity) {
        setEntityId(entity, id);
    }

    /**
     * 更新后置钩子。默认空实现。
     *
     * @param id     主键
     * @param req    请求参数
     * @param entity 已更新实体
     */
    protected void afterUpdate(ID id, REQ req, T entity) {
    }

    /**
     * 删除前置钩子。默认空实现。
     *
     * @param id 主键
     */
    protected void beforeDelete(ID id) {
    }

    /**
     * 删除后置钩子。默认空实现。
     *
     * @param id 主键
     */
    protected void afterDelete(ID id) {
    }

    /**
     * 请求参数转数据库实体。默认按同名字段拷贝，子类可覆盖以定制转换。
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
        if (entityType.isInstance(req)) {
            return (T) req;
        }
        T entity = instantiate(entityType);
        BeanUtils.copyProperties(req, entity);
        return entity;
    }

    /**
     * 数据库实体转响应视图。默认按同名字段拷贝，子类可覆盖以定制转换。
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
        if (respType.isInstance(entity)) {
            return (RESP) entity;
        }
        RESP resp = instantiate(respType);
        BeanUtils.copyProperties(entity, resp);
        return resp;
    }

    @SuppressWarnings("unchecked")
    private <X> Class<X> resolveTypeArg(int index) {
        Class<?>[] args = TYPE_ARG_CACHE.computeIfAbsent(getClass(),
            clazz -> GenericTypeResolver.resolveTypeArguments(clazz, CrudController.class));
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

    private void setEntityId(T entity, ID id) {
        try {
            entity.getClass().getMethod("setId", id.getClass()).invoke(entity, id);
        } catch (NoSuchMethodException ignored) {
            // 实体没有同类型 setId 方法时，交由业务自行在 beforeUpdate 覆盖处理。
        } catch (Exception e) {
            throw new IllegalStateException("设置实体主键失败，请覆盖 beforeUpdate 方法", e);
        }
    }
}

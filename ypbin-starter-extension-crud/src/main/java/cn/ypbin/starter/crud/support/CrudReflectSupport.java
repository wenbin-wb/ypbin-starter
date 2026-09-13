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
package cn.ypbin.starter.crud.support;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.GenericTypeResolver;

/**
 * CRUD 控制器的反射支撑工具。
 *
 * <p>泛型参数解析、实体实例化、路径主键写入这类反射细节不属于「路由分发与编排」，故从
 * {@code CrudController} 下沉到本工具类：Controller 保持极薄，只留端点方法与可覆盖的转换钩子。</p>
 *
 * <p>所有失败路径一律快速失败（{@link IllegalStateException}）并给出可操作提示，绝不静默跳过——
 * 静默失败会让 {@code updateById} 在无主键、或以请求体中被篡改的主键下执行，产生越权更新。</p>
 *
 * @author wenbin
 * @since 2026-09-13
 */
public final class CrudReflectSupport {

    /** 泛型参数解析结果缓存，避免每次请求都反射解析 */
    private static final Map<Class<?>, Class<?>[]> TYPE_ARG_CACHE = new ConcurrentHashMap<>();

    /** 实体主键写入方法名 */
    private static final String ID_SETTER_NAME = "setId";

    private CrudReflectSupport() {
    }

    /**
     * 解析具体控制器类在根泛型类上的第 {@code index} 个类型实参。
     *
     * @param concreteClass 具体控制器类
     * @param rootClass     泛型根类（如 {@code CrudController.class}）
     * @param index         类型参数下标
     * @param <X>           目标类型
     * @return 解析出的类型
     */
    @SuppressWarnings("unchecked")
    public static <X> Class<X> resolveTypeArg(Class<?> concreteClass, Class<?> rootClass, int index) {
        Class<?>[] args = TYPE_ARG_CACHE.computeIfAbsent(concreteClass,
            clazz -> GenericTypeResolver.resolveTypeArguments(clazz, rootClass));
        if (args == null || index >= args.length || args[index] == null) {
            throw new IllegalStateException("无法解析泛型类型参数，请在子类覆盖 toEntity/toResp 方法");
        }
        return (Class<X>) args[index];
    }

    /**
     * 通过无参构造实例化目标类型。
     *
     * @param type 目标类型
     * @param <X>  目标类型
     * @return 实例
     */
    public static <X> X instantiate(Class<X> type) {
        try {
            Constructor<X> constructor = type.getDeclaredConstructor();
            // 宿主常把 RESP/实体声明为包级私有或作为内部类；本工具类与原控制器不同包，
            // 不放开访问会以 IllegalAccessException 失败（原实现与宿主同包时可访问）。
            if (!constructor.canAccess(null)) {
                constructor.setAccessible(true);
            }
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法实例化 " + type.getName() + "，请提供无参构造或覆盖转换方法", e);
        }
    }

    /**
     * 把路径主键写入实体，供更新前的默认实现使用。
     *
     * <p>找不到可用 setter 或写入失败时一律快速失败，绝不放行：静默跳过会导致 {@code updateById}
     * 在无主键（或以请求体中被篡改的主键）下执行，产生越权更新或静默空更新。</p>
     *
     * @param entity 待更新实体
     * @param id     路径主键
     */
    public static void writeId(Object entity, Object id) {
        if (id == null) {
            throw new IllegalStateException("更新主键为空，已拒绝执行更新");
        }
        Method setter = resolveIdSetter(entity.getClass(), id);
        if (setter == null) {
            throw new IllegalStateException(
                "实体 " + entity.getClass().getName() + " 未找到可接收 "
                    + id.getClass().getName() + " 的 setId 方法，无法安全执行更新；"
                    + "请为实体提供 setId(Long) 或覆盖 beforeUpdate 方法");
        }
        try {
            // 同 instantiate：实体可能是包级私有类，跨包调用需放开访问
            if (!setter.canAccess(entity)) {
                setter.setAccessible(true);
            }
            setter.invoke(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("设置实体主键失败，请检查 setId 方法或覆盖 beforeUpdate", e);
        }
    }

    /**
     * 解析可写入该主键的 {@code setId} 方法：按参数类型兼容（父类型/接口亦可）匹配，
     * 避免仅按精确类型查找导致 {@code setId(Long)} 无法接收 {@code Serializable} 主键。
     *
     * @param entityType 实体类型
     * @param id         主键值
     * @return 可用 setter，找不到返回 {@code null}
     */
    private static Method resolveIdSetter(Class<?> entityType, Object id) {
        for (Method method : entityType.getMethods()) {
            if (!ID_SETTER_NAME.equals(method.getName()) || method.getParameterCount() != 1) {
                continue;
            }
            Class<?> paramType = method.getParameterTypes()[0];
            if (paramType.isInstance(id)) {
                return method;
            }
        }
        return null;
    }
}

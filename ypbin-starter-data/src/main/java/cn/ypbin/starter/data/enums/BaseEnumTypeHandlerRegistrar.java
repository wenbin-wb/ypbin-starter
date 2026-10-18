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
package cn.ypbin.starter.data.enums;

import cn.ypbin.starter.core.enums.BaseEnum;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.jspecify.annotations.Nullable;

/**
 * 把 {@link BaseEnumTypeHandler} 注册到 MyBatis 的 {@link TypeHandlerRegistry}。
 *
 * <p><b>为什么不能只注册「接口级」处理器</b>：MyBatis 的接口级注册
 * （{@code registry.register(BaseEnum.class, BaseEnumTypeHandler.class)}）只在「映射语句声明的
 * Java 类型<b>恰好</b>是 {@code BaseEnum}」时命中；实体字段声明的是<b>具体枚举</b>
 * （{@code DeviceStatus}），查表时走的是「按具体类型找」的路径，先命中 MyBatis-Plus 装配的
 * {@code CompositeEnumTypeHandler}（其默认委托是 {@code EnumTypeHandler}，即存 {@code name()}）。
 * 于是接口级注册对实体字段<b>不生效</b>，必须是「按每个具体枚举类型注册」。</p>
 *
 * <p><b>为什么逐个枚举注册而不是换默认枚举处理器</b>：MyBatis-Plus 的
 * {@code MybatisConfiguration#setDefaultEnumTypeHandler} 改的是<b>静态/全局</b>语义，会连带影响
 * 所有未实现 {@link BaseEnum} 的枚举（既有表按 {@code name()} 存的数据会读不出来）。
 * 逐个注册只影响实现了 {@link BaseEnum} 的类型，非 {@code BaseEnum} 枚举的行为一个字节都不变。</p>
 *
 * <p><b>为什么需要「候选枚举清单」</b>：MyBatis 的注册表是「类型 → 处理器」，没有「扫描所有实现了
 * 某接口的枚举」的能力。因此必须由装配层给出候选类型集合。装配层通过 Spring 的
 * {@code ClassPathScanningCandidateComponentProvider} 扫描 {@code BaseEnum} 的子类型（见
 * {@code DataAutoConfiguration#baseEnumTypeHandlerRegistrar}），此处只负责纯粹的注册动作，
 * 便于脱离 Spring 单测。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
public final class BaseEnumTypeHandlerRegistrar {

    private BaseEnumTypeHandlerRegistrar() {
    }

    /**
     * 为给定枚举类型集合注册 {@link BaseEnumTypeHandler}。
     *
     * <p>已注册过的类型会被<b>覆盖</b>为 {@link BaseEnumTypeHandler}：这既让「后装配的显式配置」生效，
     * 也保证重复调用幂等（同一个类型注册多次结果相同）。</p>
     *
     * @param configuration  MyBatis 配置（不可为 null）
     * @param enumTypes      候选枚举类型集合（可为空集合；null 元素被忽略）
     * @return 实际注册的枚举类型（顺序即入参顺序；非 null）
     */
    public static List<Class<?>> register(Configuration configuration, Iterable<Class<?>> enumTypes) {
        Objects.requireNonNull(configuration, "configuration");
        TypeHandlerRegistry registry = configuration.getTypeHandlerRegistry();
        List<Class<?>> registered = new ArrayList<>();
        for (Class<?> enumType : enumTypes) {
            Class<?> registrarType = registerOne(registry, enumType);
            if (registrarType != null) {
                registered.add(registrarType);
            }
        }
        return registered;
    }

    /**
     * 注册单个枚举类型。
     *
     * @param registry 类型处理器注册表
     * @param enumType 候选类型（可为 null / 非 BaseEnum 枚举 / 非枚举，均跳过）
     * @return 实际注册的类型；跳过时返回 {@code null}
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Nullable
    private static Class<?> registerOne(TypeHandlerRegistry registry, @Nullable Class<?> enumType) {
        if (enumType == null || !enumType.isEnum() || !BaseEnum.class.isAssignableFrom(enumType)) {
            return null;
        }
        registry.register((Class) enumType, new BaseEnumTypeHandler((Class) enumType));
        return enumType;
    }

    /**
     * 判断某个类型是否已被 {@link BaseEnumTypeHandler} 接管（供测试与诊断使用）。
     *
     * @param configuration MyBatis 配置
     * @param enumType      枚举类型
     * @return 已被接管返回 {@code true}
     */
    public static boolean isRegistered(Configuration configuration, Class<?> enumType) {
        return configuration.getTypeHandlerRegistry().getTypeHandler(enumType) instanceof BaseEnumTypeHandler;
    }
}

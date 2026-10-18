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
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

/**
 * 扫描 classpath 上所有 {@link BaseEnum} 的枚举实现。
 *
 * <p><b>为什么必须扫描</b>：MyBatis 的类型处理器注册是「类型 → 处理器」的显式映射，没有
 * 「按接口自动匹配」的能力。而实体的枚举字段是<b>具体枚举类</b>，要覆盖它们只能预先知道类型集合。
 * 扫描是唯一能让 starter「装上就生效」的办法——否则每个业务枚举都要手写
 * {@code @TableField(typeHandler = ...)}，这正是 #102 指出的「BaseEnum 零引用」的根源。</p>
 *
 * <p><b>为什么不用「classpath 通配所有 .class」的方式</b>：那会把整个 classpath 的字节码都读一遍
 * （含所有依赖 jar），启动代价高。{@link ClassPathScanningCandidateComponentProvider} 走 ASM 读元数据，
 * 不加载类，且用 {@link AssignableTypeFilter} 在字节码层判断继承关系，只对候选类做反射校验。</p>
 *
 * <p><b>扫不到的边界</b>：JDK 模块、非目录/非 jar 的类加载器（如某些动态代理）可能扫不到。
 * 这类枚举仍需业务显式标注 {@code @TableField(typeHandler = BaseEnumTypeHandler.class)}
 * 或自行注册；这是已知局限，不在运行时假装成功。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
public final class BaseEnumScanner {

    private static final Logger log = LoggerFactory.getLogger(BaseEnumScanner.class);

    private BaseEnumScanner() {
    }

    /**
     * 扫描指定包下所有 {@link BaseEnum} 的枚举实现。
     *
     * @param basePackages 扫描根包（不可为 null；空集合返回空列表）
     * @return 枚举类型集合（顺序稳定；非 null；不含接口与抽象类型）
     */
    public static List<Class<?>> scan(Iterable<String> basePackages) {
        Objects.requireNonNull(basePackages, "basePackages");
        ClassPathScanningCandidateComponentProvider scanner =
            new ClassPathScanningCandidateComponentProvider(false);
        // 只认「实现了 BaseEnum」的候选，避免把全 classpath 的类都反射一遍
        scanner.addIncludeFilter(new AssignableTypeFilter(BaseEnum.class));
        Set<Class<?>> found = new LinkedHashSet<>();
        for (String basePackage : basePackages) {
            if (basePackage == null || basePackage.isBlank()) {
                continue;
            }
            for (BeanDefinition candidate : scanner.findCandidateComponents(basePackage)) {
                Class<?> enumType = resolveEnumType(candidate.getBeanClassName());
                if (enumType != null) {
                    found.add(enumType);
                }
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("[ypbin-starter] base-enum scan finished, {} enum type(s) found.", found.size());
        }
        return List.copyOf(found);
    }

    /**
     * 校验候选类确实是 {@link BaseEnum} 的具体枚举。
     *
     * <p>{@link AssignableTypeFilter} 只能判断「是 BaseEnum 的子类型」，也会把实现了 BaseEnum 的
     * 普通类（非枚举）纳入；此处再按「枚举 + 非抽象」过滤，避免把非枚举类型注册给 MyBatis
     * 的类型处理器（MyBatis 会因此报错或静默不匹配）。</p>
     *
     * @param className 候选类名（可为 {@code null}：{@code BeanDefinition#getBeanClassName} 在
     *                  「由工厂方法/Supplier 定义」的候选上返回 null，扫描器必须容忍）
     * @return 枚举类型；不是具体枚举时返回 {@code null}
     */
    @Nullable
    private static Class<?> resolveEnumType(@Nullable String className) {
        if (className == null) {
            return null;
        }
        try {
            Class<?> candidate = Class.forName(className, false, classLoader());
            if (!candidate.isEnum() || Modifier.isAbstract(candidate.getModifiers())) {
                return null;
            }
            return candidate;
        } catch (ClassNotFoundException ex) {
            // 扫描到的候选类加载失败属于「环境不一致」，记录后跳过而不是让整个应用启动失败：
            // 该枚举会退回 MyBatis 默认枚举处理（name 存储），业务可用显式注册修正
            log.warn("[ypbin-starter] base-enum candidate '{}' cannot be loaded, skipped.", className, ex);
            return null;
        }
    }

    private static ClassLoader classLoader() {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        return contextClassLoader != null ? contextClassLoader : BaseEnumScanner.class.getClassLoader();
    }
}

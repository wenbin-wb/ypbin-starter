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
package cn.ypbin.starter.arch;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 自动配置 {@code @Bean} 方法名全局唯一约束。
 *
 * <p><b>为什么必须有这条静态规则</b>：多级缓存 P0——{@code CacheAutoConfiguration#cacheService} 与
 * {@code MultiLevelCacheAutoConfiguration#cacheService} 同名，宿主一开多级缓存就因
 * {@code BeanDefinitionOverrideException} 启动失败。这类缺陷「每个模块自己的用例全绿」，
 * 只有把多个自动配置放进同一个容器才暴露；而 Bean 名默认取方法名，属纯静态事实，
 * 用不着启动容器就能拦住同类问题。</p>
 *
 * <p>规则把同名的自动配置 Bean 方法视为冲突，除非登记在白名单并写明「为什么合法」
 * （当前唯一一类合法情形是同一能力的两个实现由配置属性严格互斥）。这样新增重复名时
 * 必须显式 review，而不是等宿主启动时炸。</p>
 *
 * @author wenbin
 * @since 2026-11-06
 */
class AutoConfigurationBeanNamesTest {

    /**
     * 允许重名的 Bean 名 → 重名理由。
     *
     * <p>只接受「两者在运行期严格互斥」的事实，不接受「一般不会同时开启」这类猜测。</p>
     */
    private static final Map<String, String> ALLOWED_DUPLICATE_NAMES = Map.of(
        "cacheService",
        "单级缓存（CacheAutoConfiguration）与多级缓存（MultiLevelCacheAutoConfiguration）互斥："
            + "前者带 @ConditionalOnProperty(ypbin.cache.multi-level.enabled=false, matchIfMissing=true)，"
            + "同一容器内只会注册其中一个，且宿主按 cacheService 名字注入不受影响",
        "passwordAttemptStore",
        "Redis 实现与内存兜底刻意共用同一 Bean 名：Redis 配置经 @Import 先注册且两者都带 "
            + "@ConditionalOnMissingBean(PasswordAttemptStore.class)，后者必然在前者生效时退让；"
            + "同名保证宿主按 passwordAttemptStore 注入时无论哪种实现都可用",
        "sseFanoutCodec",
        "Jackson 实现（SseAutoConfiguration$JacksonCodecConfiguration）与 Text 兜底刻意同名："
            + "嵌套配置经 @Import 先注册、两者都带 @ConditionalOnMissingBean(SseFanoutCodec.class)，"
            + "后注册一方退让；同名保证宿主按名注入与升级前后一致"
    );

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("cn.ypbin.starter");
    }

    @Test
    @DisplayName("自动配置的 @Bean 方法名必须全局唯一（白名单外）")
    void autoConfigurationBeanNamesShouldBeGloballyUnique() {
        Map<String, Set<String>> ownersByBeanName = new TreeMap<>();
        for (JavaClass clazz : classes) {
            if (!isAutoConfigurationScope(clazz)) {
                continue;
            }
            for (JavaMethod method : clazz.getMethods()) {
                Optional<JavaAnnotation<JavaMethod>> bean = beanAnnotation(method);
                if (bean.isEmpty()) {
                    continue;
                }
                String owner = clazz.getName() + "#" + method.getName();
                for (String beanName : beanNames(bean.get(), method.getName())) {
                    ownersByBeanName.computeIfAbsent(beanName, key -> new TreeSet<>()).add(owner);
                }
            }
        }

        List<String> conflicts = new ArrayList<>();
        ownersByBeanName.forEach((beanName, owners) -> {
            if (owners.size() > 1 && !ALLOWED_DUPLICATE_NAMES.containsKey(beanName)) {
                conflicts.add(beanName + " ← " + owners);
            }
        });

        assertThat(conflicts)
            .as("自动配置 Bean 重名：Spring Boot 默认禁止 Bean 定义覆盖，两者同时装配即 BeanDefinitionOverrideException。"
                + "请改名；若确属运行期互斥，请登记到 ALLOWED_DUPLICATE_NAMES 并写明互斥依据")
            .isEmpty();
    }

    @Test
    @DisplayName("白名单本身必须仍然有效（对应 Bean 名确实仍被多处声明）")
    void allowListShouldNotContainStaleEntries() {
        List<String> stale = new ArrayList<>();
        ALLOWED_DUPLICATE_NAMES.keySet().forEach(beanName -> {
            long count = 0;
            for (JavaClass clazz : classes) {
                if (!isAutoConfigurationScope(clazz)) {
                    continue;
                }
                count += clazz.getMethods().stream()
                    .filter(method -> beanAnnotation(method)
                        .map(annotation -> beanNames(annotation, method.getName()).contains(beanName))
                        .orElse(false))
                    .count();
            }
            if (count < 2) {
                stale.add(beanName);
            }
        });

        assertThat(stale)
            .as("白名单条目已失效：对应 Bean 名不再重复声明，请清理白名单")
            .isEmpty();
    }

    /**
     * 该类的 {@code @Bean} 方法参与全局 Bean 命名空间判定：自动配置类本身，
     * 以及自动配置类内部的嵌套 {@code @Configuration}（如类级 {@code @ConditionalOnClass} 隔离的 Redis 实现）。
     *
     * @param clazz 待判定类
     * @return 属于自动配置作用域返回 {@code true}
     */
    private static boolean isAutoConfigurationScope(JavaClass clazz) {
        JavaClass current = clazz;
        while (current != null) {
            if (current.isAnnotatedWith(AutoConfiguration.class)) {
                return true;
            }
            if (!current.isAnnotatedWith(Configuration.class)) {
                return false;
            }
            current = current.getEnclosingClass().orElse(null);
        }
        return false;
    }

    /**
     * 取方法上的 {@code @Bean} 注解实例（ArchUnit 形态，可读取属性值）。
     *
     * @param method 待检查方法
     * @return 存在则返回注解实例
     */
    private static Optional<JavaAnnotation<JavaMethod>> beanAnnotation(JavaMethod method) {
        return method.getAnnotations().stream()
            .filter(annotation -> annotation.getRawType().isEquivalentTo(Bean.class))
            .findFirst();
    }

    /**
     * 解析 {@code @Bean} 实际注册的 Bean 名：显式 {@code name}/{@code value} 优先，否则取方法名。
     *
     * @param annotation {@code @Bean} 注解实例
     * @param methodName {@code @Bean} 方法名
     * @return Bean 名列表
     */
    private static List<String> beanNames(JavaAnnotation<JavaMethod> annotation, String methodName) {
        List<String> names = new ArrayList<>();
        for (String property : List.of("name", "value")) {
            annotation.get(property).ifPresent(raw -> names.addAll(toStrings(raw)));
        }
        List<String> nonBlank = names.stream().filter(name -> !name.isBlank()).toList();
        return nonBlank.isEmpty() ? List.of(methodName) : nonBlank;
    }

    private static List<String> toStrings(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof String text) {
            return List.of(text);
        }
        if (raw instanceof Collection<?> values) {
            return values.stream().filter(value -> value != null).map(Object::toString).toList();
        }
        if (raw.getClass().isArray()) {
            int length = Array.getLength(raw);
            List<String> values = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                Object value = Array.get(raw, index);
                if (value != null) {
                    values.add(value.toString());
                }
            }
            return values;
        }
        return List.of(raw.toString());
    }
}

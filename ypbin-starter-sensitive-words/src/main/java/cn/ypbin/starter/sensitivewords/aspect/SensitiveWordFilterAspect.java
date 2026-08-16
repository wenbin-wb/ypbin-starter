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
package cn.ypbin.starter.sensitivewords.aspect;

import cn.ypbin.starter.sensitivewords.annotation.SensitiveWordFilter;
import cn.ypbin.starter.sensitivewords.autoconfigure.SensitiveWordProperties;
import cn.ypbin.starter.sensitivewords.core.SensitiveWordService;
import java.lang.reflect.Field;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 敏感词过滤切面。
 *
 * <p>拦截标注了 {@link SensitiveWordFilter} 的方法，在方法执行前遍历所有入参：
 * 对每个参数对象，反射找出其标注了 {@code @SensitiveWordFilter} 的 {@code String} 字段，
 * 将字段值替换为敏感词掩码（按 {@code ypbin.sensitive-words.replacement} 配置，默认 {@code *}）。
 * 替换原地写回字段，不影响后续方法执行。</p>
 *
 * <p>处理规则：</p>
 * <ul>
 *   <li>只处理第一层字段，不递归深层对象</li>
 *   <li>字段值为 {@code null} 或空字符串时跳过</li>
 *   <li>非 {@code String} 类型的标注字段忽略（不报错）</li>
 *   <li>反射访问异常记录 warn 日志后跳过该字段，不影响整体流程</li>
 * </ul>
 *
 * <p>不标注 {@code @Component}，由 {@link cn.ypbin.starter.sensitivewords.autoconfigure.SensitiveWordAutoConfiguration}
 * 的 {@code @Bean} 方法创建，支持 {@code @ConditionalOnMissingBean} 覆盖。</p>
 *
 * @author wenbin
 * @since 2026-08-14
 */
@Aspect
public class SensitiveWordFilterAspect {

    private static final Logger log = LoggerFactory.getLogger(SensitiveWordFilterAspect.class);

    private final SensitiveWordService sensitiveWordService;
    private final SensitiveWordProperties properties;

    public SensitiveWordFilterAspect(SensitiveWordService sensitiveWordService,
                                     SensitiveWordProperties properties) {
        this.sensitiveWordService = sensitiveWordService;
        this.properties = properties;
    }

    /**
     * 环绕拦截标注了 {@link SensitiveWordFilter} 的方法，对入参进行敏感词过滤。
     */
    @Around("@annotation(cn.ypbin.starter.sensitivewords.annotation.SensitiveWordFilter)")
    public Object filterArgs(ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = pjp.getArgs();
        if (args != null) {
            for (Object arg : args) {
                filterObject(arg);
            }
        }
        return pjp.proceed(args);
    }

    /**
     * 遍历对象的第一层字段，将标注了 {@link SensitiveWordFilter} 的 String 字段做敏感词替换。
     */
    private void filterObject(Object obj) {
        if (obj == null) {
            return;
        }
        Class<?> clazz = obj.getClass();
        // 只处理业务 POJO（排除 Java 内置类型、基本类型包装等）
        if (clazz.isPrimitive() || clazz.getName().startsWith("java.")) {
            return;
        }
        char replacement = properties.getReplacement();
        for (Field field : clazz.getDeclaredFields()) {
            if (!field.isAnnotationPresent(SensitiveWordFilter.class)) {
                continue;
            }
            if (!String.class.equals(field.getType())) {
                continue;
            }
            try {
                field.setAccessible(true);
                String value = (String) field.get(obj);
                if (value != null && !value.isEmpty()) {
                    field.set(obj, sensitiveWordService.filter(value, replacement));
                }
            } catch (IllegalAccessException e) {
                log.warn("[ypbin] 敏感词过滤反射访问失败：字段={}#{}", clazz.getSimpleName(),
                    field.getName(), e);
            }
        }
    }
}

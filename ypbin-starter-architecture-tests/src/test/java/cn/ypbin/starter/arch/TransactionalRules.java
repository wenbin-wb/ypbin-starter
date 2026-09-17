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

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code @Transactional} 显式回滚约束的共享判定逻辑。
 *
 * <p>独立成类是为了让 {@code CodingRulesTest}（真实规则）与 {@code ArchRuleSelfCheckTest}（规则有效性自检）
 * 共用**同一份**判定代码——自检若复刻一份副本，规则本身改错了自检仍然会绿，这正是本规则曾经的失效方式。</p>
 *
 * <p><b>为什么按「取值」判定，而不是 {@code getProperties().containsKey("rollbackFor")}</b>：
 * ArchUnit 的 {@link JavaAnnotation#getProperties()} 会把注解**默认值**一并填入，因此
 * {@code containsKey("rollbackFor")} 对任何 {@code @Transactional} 恒为 {@code true}
 * （缺省值为空数组 {@code {}}），规则会永远放行。实测：普通 {@code @Transactional} 方法
 * 的 key 集合含 {@code rollbackFor}、值为 {@code []}；显式声明则为
 * {@code [JavaClass{name='java.lang.Exception'}]}。取值判定天然区分二者。</p>
 *
 * <p><b>覆盖范围与边界</b>：同时检查<b>类级</b>与<b>方法级</b>注解（原实现只查方法，类级
 * {@code @Transactional} 完全不受约束）。要求 {@code rollbackFor} 取值中含 {@code java.lang.Exception}：
 * 空数组（未声明）与仅含 {@code RuntimeException} 等更窄类型都判违规；比 {@code Exception} 更宽的
 * {@code Throwable} 同样不接受——铁律的字面要求就是 {@code rollbackFor = Exception.class}。
 * 未覆盖 JTA 的 {@code jakarta.transaction.Transactional}（无 {@code rollbackFor} 属性，属另一套语义，
 * 本仓为 Spring 栈，如需约束应另立规则）。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
final class TransactionalRules {

    /** Spring 事务注解全限定名（按名匹配，避免分析模块与事务 API 产生编译期耦合）。 */
    static final String SPRING_TRANSACTIONAL = "org.springframework.transaction.annotation.Transactional";

    /** 回滚异常属性名。 */
    static final String ROLLBACK_FOR = "rollbackFor";

    /** 铁律要求的回滚异常类型全限定名。 */
    static final String EXCEPTION = "java.lang.Exception";

    private TransactionalRules() {
    }

    /** 判定注解是否为 Spring 的 {@code @Transactional}。 */
    static boolean isSpringTransactional(JavaAnnotation<?> annotation) {
        return SPRING_TRANSACTIONAL.equals(annotation.getRawType().getName());
    }

    /**
     * 判定注解是否显式声明了 {@code rollbackFor = Exception.class}。
     *
     * <p>ArchUnit 用 {@code JavaClass[]} 承载注解的 Class 数组属性；空数组即「未显式声明」。</p>
     */
    static boolean declaresRollbackForException(JavaAnnotation<?> annotation) {
        Object value = annotation.getProperties().get(ROLLBACK_FOR);
        if (!(value instanceof Object[] classes)) {
            return false;
        }
        for (Object item : classes) {
            if (item instanceof JavaClass exception && EXCEPTION.equals(exception.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 收集「{@code @Transactional} 未显式声明 {@code rollbackFor = Exception.class}」的违规点。
     *
     * @param classes 待检查的字节码集合
     * @return 违规点描述（类级 / 方法级），为空表示全部合规
     */
    static List<String> findMissingRollbackFor(JavaClasses classes) {
        List<String> violations = new ArrayList<>();
        for (JavaClass clazz : classes) {
            for (JavaAnnotation<?> annotation : clazz.getAnnotations()) {
                if (isSpringTransactional(annotation) && !declaresRollbackForException(annotation)) {
                    violations.add(clazz.getName() + "（类级 @Transactional 未显式声明 rollbackFor = Exception.class）");
                }
            }
            for (JavaMethod method : clazz.getMethods()) {
                for (JavaAnnotation<?> annotation : method.getAnnotations()) {
                    if (isSpringTransactional(annotation) && !declaresRollbackForException(annotation)) {
                        violations.add(clazz.getName() + "#" + method.getName());
                    }
                }
            }
        }
        return violations;
    }
}

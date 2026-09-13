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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 架构规则「有效性自检」。
 *
 * <p>架构测试最大的风险是**规则写错却永远通过**（例如 ArchUnit 匹配不到目标方法，断言恒真）——
 * 这种测试给出虚假安全感，比没有测试更危险。本测试用合成的违规类反向验证每条关键规则确实会失败。</p>
 *
 * <p>这些自检并非形式主义，它们已实际发现两处规则缺陷：调用点 owner 是子类导致
 * {@code callMethod(Throwable.class, ...)} 全部漏判；Lombok {@code @Data} 为 SOURCE 保留、
 * 字节码层无法校验（故该规则改为源码扫描）。</p>
 *
 * @author wenbin
 * @since 2026-09-13
 */
class ArchRuleSelfCheckTest {


    /** 谓词：调用 printStackTrace()（owner 多为 Throwable 子类，故按可赋值判定） */
    private static final DescribedPredicate<JavaMethodCall> CALLS_PRINT_STACK_TRACE =
        DescribedPredicate.describe("调用 printStackTrace()", call ->
            call.getName().equals("printStackTrace") && call.getTargetOwner().isAssignableTo(Throwable.class));

    /** 谓词：访问 System.out */
    private static final DescribedPredicate<JavaFieldAccess> ACCESSES_SYSTEM_OUT =
        DescribedPredicate.describe("访问 System.out", access ->
            access.getName().equals("out") && access.getTargetOwner().isAssignableTo(System.class));

    /** 谓词：访问 System.err */
    private static final DescribedPredicate<JavaFieldAccess> ACCESSES_SYSTEM_ERR =
        DescribedPredicate.describe("访问 System.err", access ->
            access.getName().equals("err") && access.getTargetOwner().isAssignableTo(System.class));

    private static JavaClasses syntheticClasses() {
        return new ClassFileImporter().importClasses(
            PrintStackViolation.class, SystemOutViolation.class);
    }

    @Test
    @DisplayName("printStackTrace 规则应能捕获违规（owner 为子类情形）")
    void printStackTraceRuleShouldCatchViolation() {
        ArchRule rule = ArchRuleDefinition.noClasses()
            .should().callMethodWhere(CALLS_PRINT_STACK_TRACE);
        assertThatThrownBy(() -> rule.check(syntheticClasses()))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("System.out 规则应能捕获违规")
    void systemOutRuleShouldCatchViolation() {
        ArchRule rule = ArchRuleDefinition.noClasses()
            .should().accessFieldWhere(ACCESSES_SYSTEM_OUT);
        assertThatThrownBy(() -> rule.check(syntheticClasses()))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("规则在无违规的类上应通过（避免恒真/恒假）")
    void ruleShouldPassOnCleanClasses() {
        ArchRule rule = ArchRuleDefinition.noClasses()
            .should().callMethodWhere(CALLS_PRINT_STACK_TRACE);
        assertThat(rule.evaluate(syntheticClasses()).hasViolation()).isTrue();
        JavaClasses clean = new ClassFileImporter().importClasses(String.class);
        assertThat(rule.evaluate(clean).hasViolation()).isFalse();
    }

    /** 合成违规：printStackTrace() */
    static class PrintStackViolation {

        void report(Exception e) {
            e.printStackTrace();
        }
    }

    /** 合成违规：System.out */
    static class SystemOutViolation {

        void report(String message) {
            System.out.println(message);
        }
    }
}

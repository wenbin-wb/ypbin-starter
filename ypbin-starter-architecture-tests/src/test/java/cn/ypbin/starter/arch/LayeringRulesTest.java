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

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 分层依赖约束测试。
 *
 * <p>starter 的三层结构（L1 基础能力 / L2 业务骨架 / L3 微服务能力）是模块划分的根基：
 * 上层可以依赖下层，下层**不得**反向依赖上层，否则「按需引入」会退化为「引入一个就必须拖入全部」。
 * 本测试在字节码层面校验，比检查 pom 更严格——能发现「pom 没写但代码真的用了」的隐式耦合。</p>
 *
 * <p>说明：BOM（{@code -bom}）、依赖管理（{@code -dependencies}）与聚合预设（{@code app-*}）
 * 三类模块职责就是横向罗列全部坐标，不参与分层约束（它们不含业务代码）。</p>
 *
 * @author wenbin
 * @since 2026-09-13
 */
class LayeringRulesTest {

    /** L2 业务骨架包 */
    private static final String[] L2 = {
        "cn.ypbin.starter.crud..",
        "cn.ypbin.starter.tenant..",
        "cn.ypbin.starter.datapermission..",
    };

    /** L3 微服务能力包 */
    private static final String[] L3 = {
        "cn.ypbin.starter.cloud..",
    };

    /** 聚合/示例应用包：允许横跨各层（它们的作用就是把能力组装成可运行应用） */
    private static final String[] AGGREGATE = {
        "cn.ypbin.starter.app..",
    };

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("cn.ypbin.starter");
    }

    @Test
    @DisplayName("L1 基础能力不得依赖 L2 业务骨架与 L3 微服务能力")
    void baseLayerShouldNotDependOnUpperLayers() {
        noClasses()
            .that().resideInAPackage("cn.ypbin.starter..")
            .and().resideOutsideOfPackages(L2)
            .and().resideOutsideOfPackages(L3)
            .and().resideOutsideOfPackages(AGGREGATE)
            .should().dependOnClassesThat().resideInAnyPackage(L2)
            .orShould().dependOnClassesThat().resideInAnyPackage(L3)
            .because("L1 是底座：一旦依赖 L2/L3，按需引入即失效，且会形成反向依赖")
            .check(classes);
    }

    @Test
    @DisplayName("L2 业务骨架不得依赖 L3 微服务能力")
    void businessLayerShouldNotDependOnCloudLayer() {
        noClasses()
            .that().resideInAnyPackage(L2)
            .should().dependOnClassesThat().resideInAnyPackage(L3)
            .because("L2 需同时适用于单体与微服务，依赖 L3 会把单体宿主拖入 Spring Cloud")
            .check(classes);
    }
}

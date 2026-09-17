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
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 编码铁律约束测试（字节码级）。
 *
 * <p>覆盖 starter 开发规范中可静态判定的条款：{@code @Data} 边界、枚举 ordinal 禁用、
 * 禁用 printStackTrace/System.out、{@code @Transactional} 显式 rollbackFor、
 * 条件装配语义（{@code @Bean} 覆盖语义白名单）。</p>
 *
 * <p>这些条款此前只靠 code review 保障，本测试把它们变成构建失败；白名单机制保证任何新增缺口
 * 都必须被显式review 并补充理由，而不是悄悄漏过。</p>
 *
 * @author wenbin
 * @since 2026-09-13
 */
class CodingRulesTest {


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

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("cn.ypbin.starter");
    }

    // 说明：Lombok @Data 为 SOURCE 级保留，编译后注解信息不存在，ArchUnit 无法在字节码层校验，
    // 该规则改由 SourceConventionTest 做源码扫描（ARCH 自检已确认字节码方案不可行）。

    // 说明：不在字节码层校验 ordinal——javac 会把 switch(enum) 编译成 ordinal() 查表，
    // 并生成 $SwitchMap 合成类，字节码规则会把项目鼓励的 switch 模式匹配全部误判为违规。
    // 该规则改由 SourceConventionTest 做源码扫描（只查显式 .ordinal() 调用）。

    @Test
    @DisplayName("禁止 printStackTrace 与直接使用 System.out/System.err")
    void shouldNotPrintStackTraceOrUseSystemStreams() {
        // 用 callMethodWhere 而非 callMethod(Throwable.class, ...)：调用点 owner 通常是子类
        // （如 Exception/IllegalStateException），按精确 owner 匹配会静默漏掉全部违规
        noClasses()
            .should().callMethodWhere(CALLS_PRINT_STACK_TRACE)
            .because("必须走日志框架并传完整堆栈（log.error(\"...\", ex)），printStackTrace 丢失日志上下文")
            .check(classes);

        noClasses()
            .should().accessFieldWhere(ACCESSES_SYSTEM_OUT)
            .orShould().accessFieldWhere(ACCESSES_SYSTEM_ERR)
            .because("标准输出绕过日志框架与统一格式，生产环境不可追踪")
            .check(classes);
    }

    @Test
    @DisplayName("@Transactional 必须显式声明 rollbackFor = Exception.class（类级与方法级）")
    void transactionalShouldAlwaysDeclareRollbackFor() {
        // 判定逻辑抽到 TransactionalRules，供本测试与 ArchRuleSelfCheckTest 共用同一份实现：
        // 曾经这里用 annotation.getProperties().containsKey("rollbackFor") 判定「是否显式声明」，
        // 而 ArchUnit 会把注解默认值一并填入 getProperties()，导致 containsKey 恒为 true —— 规则假绿。
        // 详见 TransactionalRules 的类级说明与其自检用例。
        assertThat(TransactionalRules.findMissingRollbackFor(classes))
            .as("@Transactional 未显式声明 rollbackFor = Exception.class（受检异常将导致漏回滚）")
            .isEmpty();
    }

    /**
     * {@code @Bean} 的「可覆盖语义」白名单。
     *
     * <p>规范要求装配 Bean 带 {@code @ConditionalOnMissingBean} 以便宿主覆盖。但有两类情况**必须**不带：
     * 内部装配/生命周期 Bean（允许被宿主关掉会造成功能残缺），以及集合贡献者 Bean
     * （如 {@code WebMvcConfigurer}、{@code SecurityExcludePathProvider}——带了会让宿主只要定义任意一个
     * 同类 Bean 就把内置能力挤掉）。这两类在此显式登记并说明理由，其余一律必须带该注解。</p>
     */
    private static final Set<String> ALLOWED_WITHOUT_CONDITIONAL_ON_MISSING_BEAN = Set.of(
        // 内部初始化/注册器：宿主不应覆盖，否则装配残缺
        "cn.ypbin.starter.async.autoconfigure.AsyncAutoConfiguration#asyncHolderInitializer",
        "cn.ypbin.starter.core.autoconfigure.CoreAutoConfiguration#springUtils",
        "cn.ypbin.starter.data.autoconfigure.DataAutoConfiguration#fieldEncryptorRegistrar",
        "cn.ypbin.starter.i18n.autoconfigure.I18nAutoConfiguration#i18nUtilInitializer",
        "cn.ypbin.starter.security.autoconfigure.SecurityAutoConfiguration#loginClientHolderInitializer",
        "cn.ypbin.starter.storage.autoconfigure.StorageAutoConfiguration#localStorageRegistrar",
        "cn.ypbin.starter.storage.autoconfigure.StorageAutoConfiguration$OssStorageConfiguration#ossStorageRegistrar",
        "cn.ypbin.starter.messaging.autoconfigure.MqttAutoConfiguration#mqttClientShutdown",
        // 集合贡献者：带 @ConditionalOnMissingBean 会被宿主任意同类 Bean 挤掉
        "cn.ypbin.starter.security.autoconfigure.SecuritySseAutoConfiguration#sseSubscribeExcludePathProvider",
        "cn.ypbin.starter.web.autoconfigure.ApiVersionAutoConfiguration#ypbinApiVersionWebMvcConfigurer",
        // Filter 注册 Bean：宿主可自行定义同名类型覆盖，但语义上必须保证默认注册存在
        "cn.ypbin.starter.security.identity.IdentityAutoConfiguration#identityHeaderFilterRegistration"
    );

    @Test
    @DisplayName("@Bean 方法必须具备覆盖语义（白名单外不得缺失 @ConditionalOnMissingBean）")
    void beanMethodsShouldBeOverridableExceptAllowList() {
        Set<String> missing = new TreeSet<>();
        for (JavaClass clazz : classes) {
            for (JavaMethod method : clazz.getMethods()) {
                if (method.tryGetAnnotationOfType(Bean.class).isEmpty()) {
                    continue;
                }
                if (method.tryGetAnnotationOfType(ConditionalOnMissingBean.class).isPresent()) {
                    continue;
                }
                String id = clazz.getName() + "#" + method.getName();
                if (!ALLOWED_WITHOUT_CONDITIONAL_ON_MISSING_BEAN.contains(id)) {
                    missing.add(id);
                }
            }
        }
        assertThat(missing)
            .as("@Bean 缺少 @ConditionalOnMissingBean（如确属内部装配 Bean 或集合贡献者，请加入本测试白名单并说明理由）")
            .isEmpty();
    }

    @Test
    @DisplayName("白名单本身必须仍然有效（防止条目过期后误放行）")
    void allowListShouldNotContainStaleEntries() {
        Set<String> actual = new TreeSet<>();
        for (JavaClass clazz : classes) {
            for (JavaMethod method : clazz.getMethods()) {
                if (method.tryGetAnnotationOfType(Bean.class).isEmpty()) {
                    continue;
                }
                if (method.tryGetAnnotationOfType(ConditionalOnMissingBean.class).isEmpty()) {
                    actual.add(clazz.getName() + "#" + method.getName());
                }
            }
        }
        assertThat(ALLOWED_WITHOUT_CONDITIONAL_ON_MISSING_BEAN)
            .as("白名单存在已失效条目：对应方法已加注解或已删除，请清理白名单")
            .isSubsetOf(actual);
    }

    @Test
    @DisplayName("禁止字段注入（@Autowired/@Resource 标注字段）")
    void shouldNotUseFieldInjection() {
        List<String> violations = new ArrayList<>();
        for (JavaClass clazz : classes) {
            clazz.getFields().forEach(field -> {
                boolean injected = field.isAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
                    || field.isAnnotatedWith("jakarta.annotation.Resource");
                if (injected) {
                    violations.add(clazz.getName() + "#" + field.getName());
                }
            });
        }
        assertThat(violations)
            .as("字段注入不可测且隐藏依赖关系，统一改构造器注入（@RequiredArgsConstructor + final 字段）")
            .isEmpty();
    }

    @Test
    @DisplayName("@Transactional 只能标注在 public 方法/类上（非 public 不被 AOP 代理，静默失效）")
    void transactionalShouldOnlyBeDeclaredOnPublicMembers() {
        String transactionalType = "org.springframework.transaction.annotation.Transactional";
        List<String> violations = new ArrayList<>();
        for (JavaClass clazz : classes) {
            boolean classAnnotated = clazz.getAnnotations().stream()
                .anyMatch(annotation -> annotation.getRawType().getName().equals(transactionalType));
            if (classAnnotated && !clazz.getModifiers().contains(JavaModifier.PUBLIC)) {
                violations.add(clazz.getName() + "（类级注解，但类非 public）");
            }
            for (JavaMethod method : clazz.getMethods()) {
                boolean annotated = method.getAnnotations().stream()
                    .anyMatch(annotation -> annotation.getRawType().getName().equals(transactionalType));
                if (annotated && !method.getModifiers().contains(JavaModifier.PUBLIC)) {
                    violations.add(clazz.getName() + "#" + method.getName());
                }
            }
        }
        assertThat(violations)
            .as("Spring AOP 只代理 public 成员：非 public 上的 @Transactional 不会生效，事务「看起来加了其实没加」")
            .isEmpty();
    }

    /**
     * 配置元数据资源位置（各模块由 spring-boot-configuration-processor 生成）。
     */
    private static final String CONFIG_METADATA_RESOURCE = "META-INF/spring-configuration-metadata.json";

    @Test
    @DisplayName("每个 @ConfigurationProperties 前缀都必须有配置元数据（缺 processor 会静默失去 IDE 提示）")
    void configurationPropertiesShouldHaveMetadata() throws IOException {
        Set<String> known = new TreeSet<>();
        Enumeration<URL> resources =
            Thread.currentThread().getContextClassLoader().getResources(CONFIG_METADATA_RESOURCE);
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            try (InputStream in = url.openStream()) {
                String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                // 不引入 JSON 依赖：元数据里 group 与 property 的 name 都以 "name" 键出现，
                // 收集全部 name 即可覆盖「前缀本身」与「前缀下的属性」两种形态。
                Matcher matcher = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"").matcher(text);
                while (matcher.find()) {
                    known.add(matcher.group(1));
                }
            }
        }

        Set<String> missing = new TreeSet<>();
        for (JavaClass clazz : classes) {
            clazz.tryGetAnnotationOfType(ConfigurationProperties.class)
                .ifPresent(annotation -> collectMissingPrefix(annotation.prefix(), clazz.getName(), known, missing));
            for (JavaMethod method : clazz.getMethods()) {
                method.tryGetAnnotationOfType(ConfigurationProperties.class)
                    .ifPresent(annotation -> collectMissingPrefix(
                        annotation.prefix(), clazz.getName() + "#" + method.getName(), known, missing));
            }
        }
        assertThat(missing)
            .as("这些 @ConfigurationProperties 前缀没有任何配置元数据：模块缺少 spring-boot-configuration-processor 依赖，"
                + "接入方将失去 IDE 提示与配置校验（修复方式：该模块 pom 加 spring-boot-configuration-processor，optional=true）")
            .isEmpty();
    }

    private static void collectMissingPrefix(String prefix, String owner, Set<String> known, Set<String> missing) {
        if (prefix == null || prefix.isBlank()) {
            return;
        }
        boolean covered = known.stream().anyMatch(name -> name.equals(prefix) || name.startsWith(prefix + "."));
        if (!covered) {
            missing.add(prefix + "  ←  " + owner);
        }
    }
}

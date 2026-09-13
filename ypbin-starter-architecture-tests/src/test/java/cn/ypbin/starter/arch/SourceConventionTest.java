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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 源码级规范约束测试。
 *
 * <p>补齐字节码不可见的三类铁律：</p>
 * <ul>
 *   <li><b>禁内联全限定类名</b>——FQCN 在编译后不再出现，ArchUnit 无法校验；</li>
 *   <li><b>{@code @Data} 边界</b>——Lombok {@code @Data} 为 SOURCE 保留，编译后注解不存在；</li>
 *   <li><b>自动配置注册</b>——{@code @AutoConfiguration} 必须登记在其模块的 imports 文件中，否则永不生效。</li>
 * </ul>
 *
 * <p>扫描前会剥离注释与字符串字面量，避免把 Javadoc 里的示例、{@code Class.forName("cn.ypbin...")}
 * 这类合法用法误判为违规。</p>
 *
 * @author wenbin
 * @since 2026-09-13
 */
class SourceConventionTest {

    private static Path repoRoot;

    @BeforeAll
    static void locateRepoRoot() {
        // 本模块位于 <repo>/ypbin-starter-architecture-tests，向上定位含聚合 pom 且含本模块的目录
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml"))
                && Files.exists(current.resolve("ypbin-starter-architecture-tests"))) {
                repoRoot = current;
                return;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("未能定位 ypbin-starter 仓库根目录（当前目录：" + Path.of("").toAbsolutePath() + "）");
    }

    /** 收集仓库内全部主源码文件 */
    private static List<Path> mainSources() throws IOException {
        try (Stream<Path> stream = Files.walk(repoRoot)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> path.toString().replace('\\', '/').contains("/src/main/java/"))
                .filter(path -> !path.toString().contains("/target/"))
                .toList();
        }
    }

    /**
     * 剥离注释与字符串/字符字面量，返回"有效代码"文本（保留换行以维持行号）。
     *
     * <p>内联 FQCN 只可能出现在有效代码中；注释与字符串里的 {@code cn.ypbin.*} 属合法内容
     * （如 {@code Class.forName("cn.dev33...")}、Javadoc 引用）。</p>
     */
    static String stripCommentsAndLiterals(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        int n = source.length();
        while (i < n) {
            char ch = source.charAt(i);
            if (ch == '/' && i + 1 < n && source.charAt(i + 1) == '/') {
                while (i < n && source.charAt(i) != '\n') {
                    i++;
                }
            } else if (ch == '/' && i + 1 < n && source.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(source.charAt(i) == '*' && source.charAt(i + 1) == '/')) {
                    if (source.charAt(i) == '\n') {
                        out.append('\n');
                    }
                    i++;
                }
                i = Math.min(i + 2, n);
            } else if (ch == '"' || ch == '\'') {
                char quote = ch;
                i++;
                while (i < n) {
                    char current = source.charAt(i);
                    if (current == '\\') {
                        i += 2;
                        continue;
                    }
                    if (current == quote) {
                        i++;
                        break;
                    }
                    if (current == '\n') {
                        out.append('\n');
                    }
                    i++;
                }
            } else {
                out.append(ch);
                i++;
            }
        }
        return out.toString();
    }

    @Test
    @DisplayName("禁止内联全限定类名（import/package 行除外）")
    void shouldNotUseInlineFullyQualifiedClassNames() throws IOException {
        // 内联全限定类名：≥2 段小写包名 + 大写开头类名（如 java.util.Objects、tools.jackson.databind.json.JsonMapper）；
        // 不匹配 SomeEnum.VALUE（首段大写）与 obj.method()（含括号），避免误判
        Pattern inlineFqcn = Pattern.compile("\\b((?:[a-z][\\w$]*\\.){2,}[A-Z][\\w$]*(?:\\.[A-Z][\\w$]*)*)");
        List<String> violations = new ArrayList<>();
        for (Path file : mainSources()) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            String code = stripCommentsAndLiterals(source);
            String[] lines = code.split("\n", -1);
            for (int index = 0; index < lines.length; index++) {
                String line = lines[index].trim();
                if (line.startsWith("package ") || line.startsWith("import ") || line.startsWith("import static ")) {
                    continue;
                }
                Matcher matcher = inlineFqcn.matcher(lines[index]);
                if (matcher.find()) {
                    violations.add(repoRoot.relativize(file) + ":" + (index + 1) + " → " + matcher.group(1));
                }
            }
        }
        assertThat(violations)
            .as("存在内联全限定类名，请改为顶部 import 后使用简单类名（本规则的唯一例外：注解属性要求编译期常量，见各切面 Javadoc）")
            .isEmpty();
    }

    @Test
    @DisplayName("@Data 仅允许用于 @ConfigurationProperties 配置绑定类")
    void lombokDataShouldOnlyBeUsedOnConfigurationProperties() throws IOException {
        Pattern dataAnnotation = Pattern.compile("^@Data\\b", Pattern.MULTILINE);
        Pattern configurationProperties = Pattern.compile("@ConfigurationProperties\\b");
        List<String> violations = new ArrayList<>();
        for (Path file : mainSources()) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            if (dataAnnotation.matcher(source).find() && !configurationProperties.matcher(source).find()) {
                violations.add(repoRoot.relativize(file).toString());
            }
        }
        assertThat(violations)
            .as("业务实体/DTO 使用 @Data 会污染 equals/hashCode 并在 toString 泄露敏感字段，请改用 @Getter @Setter")
            .isEmpty();
    }

    /** 显式调用 Enum.ordinal() 的模式 */
    private static final Pattern ENUM_ORDINAL_CALL = Pattern.compile("\\.\\s*ordinal\\s*\\(\\s*\\)");

    @Test
    @DisplayName("禁止显式调用 Enum.ordinal()")
    void shouldNotCallEnumOrdinalExplicitly() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : mainSources()) {
            String code = stripCommentsAndLiterals(Files.readString(file, StandardCharsets.UTF_8));
            String[] lines = code.split("\n", -1);
            for (int index = 0; index < lines.length; index++) {
                if (ENUM_ORDINAL_CALL.matcher(lines[index]).find()) {
                    violations.add(repoRoot.relativize(file) + ":" + (index + 1));
                }
            }
        }
        assertThat(violations)
            .as("枚举必须显式声明 code/desc，存库与传参一律用 code；ordinal 会因枚举顺序调整而错乱")
            .isEmpty();
    }

    @Test
    @DisplayName("ordinal 检测正则应能命中违规（规则有效性自检）")
    void ordinalPatternShouldCatchViolation() {
        assertThat(ENUM_ORDINAL_CALL.matcher("return status.ordinal();").find()).isTrue();
        assertThat(ENUM_ORDINAL_CALL.matcher("return status . ordinal ( );").find()).isTrue();
        // switch (enum) 由 javac 生成的 ordinal 调用不应命中源码规则
        assertThat(ENUM_ORDINAL_CALL.matcher("switch (status) { case ENABLED -> 1; }").find()).isFalse();
    }

    /** 应统一为 List.of/Map.of/Set.of 的 Collections 工厂调用 */
    private static final Pattern LEGACY_COLLECTION_FACTORY =
        Pattern.compile("Collections\\.(?:emptyList|emptyMap|emptySet|singletonList|singletonMap|singleton)\\(");

    /** Spring Boot 4.1 起生效的 EnvironmentPostProcessor 注册键（接口同包名） */
    private static final String NEW_EP_KEY = "org.springframework.boot.EnvironmentPostProcessor";

    /** Spring Boot 4.1 起废弃待移除的旧注册键 */
    private static final String LEGACY_EP_KEY = "org.springframework.boot.env.EnvironmentPostProcessor";

    /** 类声明中是否直接实现 EnvironmentPostProcessor（仅看 implements 子句，排除 Javadoc/注释提及） */
    private static final Pattern IMPLEMENTS_ENVIRONMENT_POST_PROCESSOR =
        Pattern.compile("\\bimplements\\b[^{;]*\\bEnvironmentPostProcessor\\b");

    @Test
    @DisplayName("集合字面量统一用 List.of/Map.of/Set.of，禁用 Collections.emptyXxx/singletonXxx")
    void shouldUseImmutableFactoriesInsteadOfCollectionsHelpers() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : mainSources()) {
            String code = stripCommentsAndLiterals(Files.readString(file, StandardCharsets.UTF_8));
            String[] lines = code.split("\n", -1);
            for (int index = 0; index < lines.length; index++) {
                if (LEGACY_COLLECTION_FACTORY.matcher(lines[index]).find()) {
                    violations.add(repoRoot.relativize(file) + ":" + (index + 1));
                }
            }
        }
        assertThat(violations)
            .as("请改用 List.of()/Map.of()/Set.of()（语义等价且更简洁）；注意 List.of 不接受 null 元素")
            .isEmpty();
    }

    @Test
    @DisplayName("集合工厂检测正则应能命中违规（规则有效性自检）")
    void legacyFactoryPatternShouldCatchViolation() {
        assertThat(LEGACY_COLLECTION_FACTORY.matcher("return Collections.emptyList();").find()).isTrue();
        assertThat(LEGACY_COLLECTION_FACTORY.matcher("Collections.singletonList(key)").find()).isTrue();
        // 非目标 API 不应命中
        assertThat(LEGACY_COLLECTION_FACTORY.matcher("Collections.unmodifiableList(list)").find()).isFalse();
        assertThat(LEGACY_COLLECTION_FACTORY.matcher("List.of()").find()).isFalse();
    }

    @Test
    @DisplayName("每个 @AutoConfiguration 必须登记在其模块的 AutoConfiguration.imports 中")
    void autoConfigurationsShouldBeRegistered() throws IOException {
        Set<String> problems = new TreeSet<>();
        for (Path file : mainSources()) {
            String name = file.getFileName().toString();
            if (!name.endsWith("AutoConfiguration.java")) {
                continue;
            }
            String source = Files.readString(file, StandardCharsets.UTF_8);
            if (!source.contains("@AutoConfiguration")) {
                continue;
            }
            Matcher packageMatcher = Pattern.compile("package\\s+([\\w.]+);").matcher(source);
            String packageName = packageMatcher.find() ? packageMatcher.group(1) : null;
            if (packageName == null) {
                problems.add(repoRoot.relativize(file) + " → 缺少 package 声明");
                continue;
            }
            String fqcn = packageName + "." + name.substring(0, name.length() - ".java".length());
            Path moduleRoot = moduleRootOf(file);
            Path imports = moduleRoot.resolve(
                "src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
            if (!Files.exists(imports)) {
                problems.add(repoRoot.relativize(file) + " → 模块缺少 AutoConfiguration.imports 文件");
                continue;
            }
            String registered = Files.readString(imports, StandardCharsets.UTF_8);
            if (!registered.contains(fqcn)) {
                problems.add(fqcn + " → 未登记于 " + repoRoot.relativize(imports));
            }
        }
        assertThat(problems)
            .as("@AutoConfiguration 未注册则永不生效，属静默失效缺陷")
            .isEmpty();
    }

    @Test
    @DisplayName("每个 EnvironmentPostProcessor 实现必须用新键登记在 spring.factories 中")
    void environmentPostProcessorsShouldBeRegisteredWithNewKey() throws IOException {
        Set<String> problems = new TreeSet<>();
        for (Path file : mainSources()) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            String code = stripCommentsAndLiterals(source);
            if (!IMPLEMENTS_ENVIRONMENT_POST_PROCESSOR.matcher(code).find()) {
                continue;
            }
            String name = file.getFileName().toString();
            Matcher packageMatcher = Pattern.compile("package\\s+([\\w.]+);").matcher(source);
            if (!packageMatcher.find()) {
                continue;
            }
            String fqcn = packageMatcher.group(1) + "." + name.substring(0, name.length() - ".java".length());
            Path factories = moduleRootOf(file).resolve("src/main/resources/META-INF/spring.factories");
            if (!Files.exists(factories)) {
                problems.add(fqcn + " → 模块缺少 META-INF/spring.factories");
                continue;
            }
            String registered = Files.readString(factories, StandardCharsets.UTF_8);
            if (!registered.contains(NEW_EP_KEY + "=")) {
                problems.add(fqcn + " → spring.factories 缺少注册键 " + NEW_EP_KEY);
                continue;
            }
            if (!registered.contains(fqcn)) {
                problems.add(fqcn + " → 未登记于 " + repoRoot.relativize(factories));
            }
        }
        assertThat(problems)
            .as("Spring Boot 4.1 起 org.springframework.boot.env.EnvironmentPostProcessor 已废弃待移除，"
                + "必须改用 org.springframework.boot.EnvironmentPostProcessor 接口与同名注册键，否则默认值静默失效")
            .isEmpty();
    }

    @Test
    @DisplayName("spring.factories 不得再出现已废弃的 EnvironmentPostProcessor 注册键")
    void springFactoriesShouldNotUseLegacyEnvironmentPostProcessorKey() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(repoRoot)) {
            for (Path file : stream
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().equals("spring.factories"))
                .filter(path -> !path.toString().contains("/target/"))
                .toList()) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                if (content.contains(LEGACY_EP_KEY)) {
                    violations.add(repoRoot.relativize(file).toString());
                }
            }
        }
        assertThat(violations).isEmpty();
    }

    /** 从源码文件回溯所属模块目录（repo/<module>/src/main/java/...） */
    private static Path moduleRootOf(Path file) {
        Path current = file;
        while (current != null && !current.endsWith("src")) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("无法从路径回溯模块目录：" + file);
        }
        // current 指向 <module>/src，其父目录即模块根
        return current.getParent();
    }
}

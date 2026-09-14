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
 * <p>补齐字节码不可见或更易在源码层判定的铁律：</p>
 * <ul>
 *   <li><b>禁内联全限定类名</b>——FQCN 在编译后不再出现，ArchUnit 无法校验；</li>
 *   <li><b>{@code @Data} 边界</b>——Lombok {@code @Data} 为 SOURCE 保留，编译后注解不存在；</li>
 *   <li><b>自动配置注册</b>——{@code @AutoConfiguration} 必须登记在其模块的 imports 文件中，否则永不生效；</li>
 *   <li><b>环境后置处理器注册</b>——接口与 spring.factories 注册键必须同时为新版，否则默认值静默失效；</li>
 *   <li><b>集合字面量工厂</b>——统一 {@code List.of()/Map.of()/Set.of()}，禁用 Collections 旧工厂；</li>
 *   <li><b>禁裸 {@code java.util.Date}</b>——时间字段统一 {@code LocalDateTime}；</li>
 *   <li><b>Controller 极薄</b>——单文件 ≤400 行且不得含私有方法；</li>
 *   <li><b>实体等值语义</b>——{@code equals/hashCode} 必须且仅基于主键 id。</li>
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
            } else if (ch == '"' && i + 2 < n && source.charAt(i + 1) == '"' && source.charAt(i + 2) == '"') {
                // 文本块：必须整体跳过，否则内容里的引号会让剥离器与后续代码错位
                // （奇数个引号时，文本块之后的代码会被整段吞掉，所有消费剥离文本的规则静默失明）
                i += 3;
                while (i < n) {
                    if (source.charAt(i) == '\\') {
                        i += 2;
                        continue;
                    }
                    if (source.charAt(i) == '"' && i + 2 < n
                        && source.charAt(i + 1) == '"' && source.charAt(i + 2) == '"') {
                        i += 3;
                        break;
                    }
                    if (source.charAt(i) == '\n') {
                        out.append('\n');
                    }
                    i++;
                }
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

    /** 禁止的裸日期类型（时间字段统一 LocalDateTime + 全局 yyyy-MM-dd HH:mm:ss） */
    private static final Pattern BARE_DATE_TYPE = Pattern.compile(
        "\\bjava\\.util\\.Date\\b|^\\s*import\\s+java\\.util\\.Date\\s*;", Pattern.MULTILINE);

    /** 通配导入（该项会让下面的裸 Date 判定生效） */
    private static final Pattern WILDCARD_UTIL_IMPORT = Pattern.compile(
        "^\\s*import\\s+java\\.util\\.\\*\\s*;", Pattern.MULTILINE);

    /** 裸 Date 记号：排除 LocalDate/LocalDateTime 等相邻写法与成员访问 */
    private static final Pattern BARE_DATE_TOKEN = Pattern.compile("(?<![\\w$.])Date(?![\\w$])");

    @Test
    @DisplayName("禁止裸 java.util.Date（时间字段统一 LocalDateTime）")
    void shouldNotUseBareJavaUtilDate() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : mainSources()) {
            String code = stripCommentsAndLiterals(Files.readString(file, StandardCharsets.UTF_8));
            for (int line : bareDateLines(code)) {
                violations.add(repoRoot.relativize(file) + ":" + line);
            }
        }
        assertThat(violations)
            .as("裸 java.util.Date 缺时区语义、序列化格式无法统一；请用 LocalDateTime（必要时 Instant 表达时刻）")
            .isEmpty();
    }

    /**
     * 找出裸 {@code java.util.Date} 使用所在行号。
     *
     * <p>除显式引用外还要覆盖 {@code import java.util.*;} 的情形：只有通配导入时
     * 「裸 Date 记号」才足以判定为 java.util.Date（否则可能来自其它包，会误判）。</p>
     *
     * @param code 已剥离注释/字面量的源码
     * @return 去重后的行号（1-based）
     */
    static List<Integer> bareDateLines(String code) {
        Set<Integer> lines = new TreeSet<>();
        Matcher explicit = BARE_DATE_TYPE.matcher(code);
        while (explicit.find()) {
            lines.add(lineOf(code, explicit.start()));
        }
        if (WILDCARD_UTIL_IMPORT.matcher(code).find()) {
            Matcher bare = BARE_DATE_TOKEN.matcher(code);
            while (bare.find()) {
                lines.add(lineOf(code, bare.start()));
            }
        }
        return new ArrayList<>(lines);
    }

    /** 计算下标所在行号（1-based） */
    private static int lineOf(String code, int index) {
        return (int) code.substring(0, index).chars().filter(ch -> ch == '\n').count() + 1;
    }

    @Test
    @DisplayName("裸 Date 检测正则应能命中违规（规则有效性自检）")
    void bareDatePatternShouldCatchViolation() {
        assertThat(BARE_DATE_TYPE.matcher("import java.util.Date;").find()).isTrue();
        assertThat(BARE_DATE_TYPE.matcher("private java.util.Date createdAt;").find()).isTrue();
        // 非目标：LocalDateTime、java.sql.Date、注释里的说明
        assertThat(BARE_DATE_TYPE.matcher("import java.time.LocalDateTime;").find()).isFalse();
        assertThat(BARE_DATE_TYPE.matcher("private LocalDateTime createdAt;").find()).isFalse();

        // 通配导入下的裸 Date 使用必须能抓到（显式 import 之外的漏判路径）
        String wildcardUse = "import java.util.*;\nclass A {\n    private Date createdAt;\n}\n";
        assertThat(bareDateLines(wildcardUse)).hasSize(1);
        // 通配导入但未用 Date（含 LocalDateTime）不应误判
        String wildcardNoDate = "import java.util.*;\nclass A {\n    private List<String> tags;\n"
            + "    private LocalDateTime createdAt;\n}\n";
        assertThat(bareDateLines(wildcardNoDate)).isEmpty();
    }

    /** Controller 单文件行数上限（铁律：Controller 极薄，单类严禁超 400 行） */
    private static final int CONTROLLER_MAX_LINES = 400;

    /** Controller 中的私有方法声明（铁律：严禁私有长方法，逻辑应下沉到 Service） */
    private static final Pattern PRIVATE_METHOD = Pattern.compile(
        "^[ \\t]*(?:@\\w+(?:\\([^)]*\\))?[ \\t]+)*private\\s+(?!static\\s+final\\b)"
            + "[\\w<>,.\\[\\]\\s]*\\s+\\w+\\s*\\(",
        Pattern.MULTILINE);

    @Test
    @DisplayName("Controller 必须极薄：单文件 ≤400 行且不得有私有方法")
    void controllersShouldBeThin() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : mainSources()) {
            if (!file.getFileName().toString().endsWith("Controller.java")) {
                continue;
            }
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            long lines = raw.lines().count();
            if (lines > CONTROLLER_MAX_LINES) {
                violations.add(repoRoot.relativize(file) + " → " + lines + " 行（上限 " + CONTROLLER_MAX_LINES + "）");
            }
            String code = stripCommentsAndLiterals(raw);
            Matcher matcher = PRIVATE_METHOD.matcher(code);
            while (matcher.find()) {
                int line = (int) code.substring(0, matcher.start()).chars().filter(ch -> ch == '\n').count() + 1;
                violations.add(repoRoot.relativize(file) + ":" + line + " → Controller 内私有方法");
            }
        }
        assertThat(violations)
            .as("Controller 只做路由分发与 Service 编排：超长或含私有方法说明业务逻辑没下沉到 Service")
            .isEmpty();
    }

    @Test
    @DisplayName("Controller 私有方法检测正则应能命中违规（规则有效性自检）")
    void privateMethodPatternShouldCatchViolation() {
        assertThat(PRIVATE_METHOD.matcher("    private String buildKey(Long id) {").find()).isTrue();
        assertThat(PRIVATE_METHOD.matcher("private void check() throws Exception {").find()).isTrue();
        // 注解与签名同行（原正则同样漏判）
        assertThat(PRIVATE_METHOD.matcher("@Override private void hook() {").find()).isTrue();
        assertThat(PRIVATE_METHOD.matcher("    @PostConstruct private void init() {").find()).isTrue();
        // 非目标：public 方法、常量字段（无括号）
        assertThat(PRIVATE_METHOD.matcher("    public R<Void> save() {").find()).isFalse();
        assertThat(PRIVATE_METHOD.matcher("    private static final String PREFIX = \"x\";").find()).isFalse();
    }

    /** 实体判定：继承实体基类、标注 @TableName、或类名以 Entity 结尾 */
    private static final Pattern ENTITY_DECLARATION = Pattern.compile(
        "\\bclass\\s+(\\w*Entity\\w*)\\b|\\bextends\\s+\\w*BaseEntity\\b|@TableName\\b");

    /** 未经约束的 Lombok 等值注解（未声明 onlyExplicitlyIncluded 时默认纳入全部字段） */
    private static final Pattern UNCONSTRAINED_EQUALS_ANNOTATION = Pattern.compile(
        "@EqualsAndHashCode\\s*(?:$|\\((?![^)]*(?:onlyExplicitlyIncluded\\s*=\\s*true|of\\s*=)))",
        Pattern.MULTILINE);

    /** 手写 equals/hashCode */
    private static final Pattern HANDWRITTEN_EQUALS = Pattern.compile(
        "\\bpublic\\s+boolean\\s+equals\\s*\\(|\\bpublic\\s+int\\s+hashCode\\s*\\(\\s*\\)");

    @Test
    @DisplayName("实体 equals/hashCode 必须仅基于主键 id（禁默认全字段、禁手写）")
    void entityEqualsShouldBeBasedOnIdOnly() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : mainSources()) {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            String code = stripCommentsAndLiterals(raw);
            if (!ENTITY_DECLARATION.matcher(code).find()) {
                continue;
            }
            if (UNCONSTRAINED_EQUALS_ANNOTATION.matcher(code).find()) {
                violations.add(repoRoot.relativize(file)
                    + " → @EqualsAndHashCode 未声明 onlyExplicitlyIncluded = true（会纳入集合/关联/非表字段）");
            }
            Matcher handwritten = HANDWRITTEN_EQUALS.matcher(code);
            while (handwritten.find()) {
                int line = (int) code.substring(0, handwritten.start()).chars().filter(ch -> ch == '\n').count() + 1;
                violations.add(repoRoot.relativize(file) + ":" + line + " → 手写 equals/hashCode");
            }
        }
        assertThat(violations)
            .as("实体等值必须且仅基于主键 id：@EqualsAndHashCode(onlyExplicitlyIncluded = true) + 仅 id 标 @Include，"
                + "且不得手写 equals/hashCode（易纳入集合或 @TableField(exist = false) 字段）")
            .isEmpty();
    }

    @Test
    @DisplayName("实体等值检测正则应能命中违规（规则有效性自检）")
    void entityEqualsPatternShouldCatchViolation() {
        assertThat(ENTITY_DECLARATION.matcher("public class DemoEntity extends BaseEntity {").find()).isTrue();
        assertThat(ENTITY_DECLARATION.matcher("@TableName(\"sys_user\")\nclass SysUser {").find()).isTrue();
        assertThat(UNCONSTRAINED_EQUALS_ANNOTATION.matcher("@EqualsAndHashCode\nclass A {}").find()).isTrue();
        assertThat(UNCONSTRAINED_EQUALS_ANNOTATION.matcher("@EqualsAndHashCode(callSuper = true)").find()).isTrue();
        // 非目标：显式限定只纳入 Include 字段，或用 of 明确指定字段（等价合法写法，原实现误判）
        assertThat(UNCONSTRAINED_EQUALS_ANNOTATION
            .matcher("@EqualsAndHashCode(onlyExplicitlyIncluded = true)").find()).isFalse();
        assertThat(UNCONSTRAINED_EQUALS_ANNOTATION.matcher("@EqualsAndHashCode(of = \"id\")").find()).isFalse();
        assertThat(HANDWRITTEN_EQUALS.matcher("public boolean equals(Object o) {").find()).isTrue();
        assertThat(HANDWRITTEN_EQUALS.matcher("public int hashCode() {").find()).isTrue();
    }

    /**
     * 方法签名前缀：允许「同行注解 + 访问修饰符」。
     *
     * <p>原实现以 {@code ^[ \t]*(?:public|protected|private)} 起锚，导致
     * {@code @Override private void x()} 这类「注解与签名同行」的写法完全漏判。</p>
     */
    private static final String MODIFIER_PREFIX =
        "^[ \\t]*(?:@\\w+(?:\\([^)]*\\))?[ \\t]+)*(?:public|protected|private)[ \\t]+"
            + "(?:static[ \\t]+)?(?:final[ \\t]+)?(?:synchronized[ \\t]+)*";

    /** 集合返回类型的方法签名；group(1)=返回类型，group(2)=方法名 */
    private static final Pattern COLLECTION_METHOD_SIGNATURE = Pattern.compile(
        MODIFIER_PREFIX + "([^;{}=\\n]*?)[ \\t]+(\\w+)[ \\t]*\\(",
        Pattern.MULTILINE);

    /** 返回类型中出现集合类型：泛型（含 {@code Optional<集合>} 嵌套）与 raw 类型都要覆盖 */
    private static final Pattern COLLECTION_RETURN_TYPE = Pattern.compile(
        "\\b(?:List|Set|Map|Collection|Iterable|Queue|Deque)\\b");

    /** 方法体内的 return null */
    private static final Pattern RETURN_NULL = Pattern.compile("\\breturn\\s+null\\s*;");

    @Test
    @DisplayName("集合返回类型的方法不得返回 null（查无数据须返回空集合）")
    void collectionReturningMethodsShouldNotReturnNull() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : mainSources()) {
            String code = stripCommentsAndLiterals(Files.readString(file, StandardCharsets.UTF_8));
            // 排除 Object 的 equals(Object) 之类的非集合返回：由返回类型正则保证
            for (int line : collectionMethodsReturningNull(code)) {
                violations.add(repoRoot.relativize(file) + ":" + line);
            }
        }
        assertThat(violations)
            .as("返回集合的方法（含 Optional<集合>、raw 集合类型）查到空数据时一律返回 List.of()/Map.of()/Set.of()；"
                + "若语义是「无结果/解析失败」而非「空集合」，请用 Optional 包裹集合本体。"
                + "已知边界：方法体内嵌套类型（匿名类/局部类）里的 return null 也会被计入外层方法——"
                + "源码扫描不区分嵌套类型归属，如遇误报请把该嵌套实现提取为独立类/方法")
            .isEmpty();
    }

    @Test
    @DisplayName("集合返回 null 检测应能命中/放过预期样例（规则有效性自检）")
    void collectionReturnNullDetectionShouldBeAccurate() {
        String violation = "public List<String> list() {\n    try {\n        return read();\n    } catch (Exception e) {\n"
            + "        return null;\n    }\n}\n";
        assertThat(collectionMethodsReturningNull(stripCommentsAndLiterals(violation))).hasSize(1);

        // Optional 包裹集合本体：返回 Optional.empty() 不违规，但返回 null 仍违规（口径已写进规则文案）
        String optionalOk = "public Optional<List<String>> list() {\n    return Optional.empty();\n}\n";
        assertThat(collectionMethodsReturningNull(stripCommentsAndLiterals(optionalOk))).isEmpty();
        String optionalNull = "public Optional<List<String>> list() {\n    return null;\n}\n";
        assertThat(collectionMethodsReturningNull(stripCommentsAndLiterals(optionalNull))).hasSize(1);

        // 注解与签名同行（原正则要求行首即修饰符 → 曾漏判）
        String annotated = "@Override\n@SuppressWarnings(\"unchecked\")\npublic List<String> b() { return null; }\n";
        assertThat(collectionMethodsReturningNull(stripCommentsAndLiterals(annotated))).hasSize(1);
        String annotatedSameLine = "@SuppressWarnings(\"unchecked\") public List<String> b() { return null; }\n";
        assertThat(collectionMethodsReturningNull(stripCommentsAndLiterals(annotatedSameLine))).hasSize(1);

        // 参数里带注解数组：旧实现会把注解的 { 当成方法体起点而漏判
        String annotatedParam = "public List<String> i(@Foo({1, 2}) int a) {\n    return null;\n}\n";
        assertThat(collectionMethodsReturningNull(stripCommentsAndLiterals(annotatedParam))).hasSize(1);

        // raw 集合类型（无泛型）也要判
        String rawType = "public Map c() {\n    return null;\n}\n";
        assertThat(collectionMethodsReturningNull(stripCommentsAndLiterals(rawType))).hasSize(1);

        // 非集合返回类型不参与判定
        String scalar = "private String name() {\n    return null;\n}\n";
        assertThat(collectionMethodsReturningNull(stripCommentsAndLiterals(scalar))).isEmpty();

        // 注释与字符串里的 return null 不应误判
        String inComment = "public List<String> list() {\n    // return null;\n    return List.of();\n}\n";
        assertThat(collectionMethodsReturningNull(stripCommentsAndLiterals(inComment))).isEmpty();
    }

    /**
     * 定位方法体起始的 {@code \{}：先扫到参数表闭合的 {@code )}，再取其后的第一个 {@code \{}。
     *
     * <p>直接 {@code indexOf('{')} 会被参数里的注解数组（如 {@code @Foo({1,2})}）截胡，
     * 把「方法体」取成注解内容，从而漏判。</p>
     *
     * @param code          已剥离注释/字面量的源码
     * @param afterOpenParen 方法名后 {@code (} 之后的下标
     * @return 方法体起始下标；无方法体返回 -1
     */
    private static int methodBodyStart(String code, int afterOpenParen) {
        int depth = 0;
        int i = afterOpenParen - 1;
        for (; i < code.length(); i++) {
            char ch = code.charAt(i);
            if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
                if (depth == 0) {
                    i++;
                    break;
                }
            }
        }
        return code.indexOf('{', i);
    }

    /** 返回「集合返回类型且方法体内出现 return null」所在行号（1-based） */
    static List<Integer> collectionMethodsReturningNull(String code) {
        List<Integer> lines = new ArrayList<>();
        Matcher signature = COLLECTION_METHOD_SIGNATURE.matcher(code);
        while (signature.find()) {
            if (!COLLECTION_RETURN_TYPE.matcher(signature.group(1)).find()) {
                continue;
            }
            int brace = methodBodyStart(code, signature.end());
            if (brace < 0) {
                continue; // 接口/抽象方法：无方法体
            }
            int depth = 0;
            int end = -1;
            for (int i = brace; i < code.length(); i++) {
                char ch = code.charAt(i);
                if (ch == '{') {
                    depth++;
                } else if (ch == '}') {
                    depth--;
                    if (depth == 0) {
                        end = i;
                        break;
                    }
                }
            }
            if (end < 0) {
                continue;
            }
            if (RETURN_NULL.matcher(code.substring(brace, end + 1)).find()) {
                lines.add((int) code.substring(0, signature.start()).chars().filter(ch -> ch == '\n').count() + 1);
            }
        }
        return lines;
    }

    @Test
    @DisplayName("源码剥离后大括号必须平衡（否则多条规则会静默失明）")
    void strippingShouldPreserveBraceBalance() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : mainSources()) {
            String code = stripCommentsAndLiterals(Files.readString(file, StandardCharsets.UTF_8));
            long open = code.chars().filter(ch -> ch == '{').count();
            long close = code.chars().filter(ch -> ch == '}').count();
            if (open != close) {
                violations.add(repoRoot.relativize(file) + " → 剥离后 { =" + open + " 而 } =" + close);
            }
        }
        assertThat(violations)
            .as("剥离器一旦吞掉代码，内联 FQCN/Date/Controller/实体/集合等规则会静默失效；"
                + "常见成因是字符串/字符/文本块字面量未被正确跳过")
            .isEmpty();
    }

    @Test
    @DisplayName("文本块剥离正误样例（规则有效性自检）")
    void textBlockStrippingShouldBeAccurate() {
        // 文本块内含奇数个引号：旧实现会从这里开始与后续代码错位，把 hidden 整段吞掉
        String source = "class A {\n    String s = \"\"\"\n    he said \"hi and left\n    \"\"\";\n"
            + "    void hidden() {}\n}\n";
        String stripped = stripCommentsAndLiterals(source);
        assertThat(stripped).contains("void hidden()");
        assertThat(stripped.chars().filter(ch -> ch == '{').count())
            .isEqualTo(stripped.chars().filter(ch -> ch == '}').count());
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

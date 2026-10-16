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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 自动配置「顺序陷阱」门禁：{@code @ConditionalOnBean(X)} 若判定的是<b>由 Boot 自动配置提供</b>的类型，
 * 则所在自动配置类<b>必须</b>显式声明与 Boot 对应自动配置的顺序（{@code @AutoConfigureAfter}/{@code Before}）。
 *
 * <p><b>这条规则来自一次线上事故（2026-10-15）</b>：{@code LeaseLockGuard} 的装配条件写成
 * {@code @ConditionalOnBean(MeterRegistry.class)}，而 {@code @ConditionalOnBean} <b>只看得见「先于它
 * 处理完」的 Bean 定义</b>。本仓包名 {@code cn.ypbin.*} 在 Boot 的自动配置排序中排在
 * {@code org.springframework.*} <b>之前</b>（无显式顺序时按类名字典序），于是判定时
 * {@code MeterRegistry} 尚未注册 ⇒ 守卫 Bean 整块消失 ⇒ 下游所有注入它的业务 Bean 以
 * {@code UnsatisfiedDependencyException} <b>启动失败</b>。</p>
 *
 * <p>同一根因还会造成更隐蔽的形态：{@code @ConditionalOnBean(StringRedisTemplate.class)} 恒假 ⇒
 * 分布式限流/幂等/锁/SSE 票据与扇出/nonce/密码错误计数<b>静默退化为内存实现</b>——
 * 编译通过、单测全绿、启动成功，只有多副本部署时功能悄悄失效。本仓当时共有 5 处这样的判定。</p>
 *
 * <p><b>为什么只能做成源码门禁</b>：这类缺陷在「单模块的 ApplicationContextRunner」里都可能复现，
 * 但只要测试把被依赖的 Bean 作为<b>用户配置</b>塞进上下文（Bean 定义先于自动配置处理就存在），
 * 顺序问题就被掩盖。真正能拦住它的是「断言必须显式声明顺序」这条<b>结构性</b>约束——
 * 它不依赖任何人写出正确的运行时用例。</p>
 *
 * <p><b>覆盖范围与边界（有意收窄，避免误报）</b>：只检查判定类型落在 {@link #BOOT_PROVIDED_TYPES}
 * 或 {@link #THIRD_PARTY_PROVIDED_TYPES} 白名单内的情形。两张表只收「由 Boot 自身 / 第三方自动配置提供、
 * 且本仓确实依赖其顺序」的类型；仓库内部类型（如 {@code DataScopeHandler}、
 * {@code SseUserIdResolver}）之间的顺序由各自的 {@code @AutoConfigureBefore/After} 单独表达，
 * 不在本规则射程内（判错会制造大量误报，反而逼人加宽泛白名单）。</p>
 *
 * <p><b>2026-10-16 扩展</b>：新增第三方类型表，支持「一个类型多个提供者」。起因是同日发现的
 * 同类第 5/6 例（{@code VectorStore}、{@code ChatModel}/{@code ChatMemory}）均由 <b>Spring AI 第三方</b>
 * 自动配置提供且无顺序声明；若把第三方混进 Boot 表并把提供者写成<b>仓内</b>类，报错指引会把人引向
 * 「给已经有序的那一侧加注解」。</p>
 *
 * @author wenbin
 * @since 2026-10-15
 */
class AutoConfigurationOrderingTest {

    /**
     * 由 Boot 自动配置提供、且本仓会自动判定其存在性的类型（简单名 → 提供它的 Boot 自动配置）。
     *
     * <p>只登记「本仓实际出现过判定」的类型。新增类型时必须同时给出 Boot 侧的提供者，
     * 否则规则无从判断该加哪个 {@code @AutoConfigureAfter}。</p>
     */
    private static final Map<String, String> BOOT_PROVIDED_TYPES = buildBootProvidedTypes();

    /**
     * 由<b>第三方</b>自动配置（非 Boot 自身）提供、且本仓会自动判定其存在性的类型
     * （简单名 → 提供它的第三方自动配置 FQCN <b>集合</b>）。
     *
     * <p><b>为何与 {@link #BOOT_PROVIDED_TYPES} 分表</b>：两者性质不同，混在一张表里会产出<b>错误的报错指引</b>——
     * Boot 侧类型是「一对一定位提供者」，而第三方类型常见<b>多提供者</b>，且同一类型还可能由<b>仓内</b>自动配置
     * 以 {@code @AutoConfigureBefore} 反向约束（如 {@code VectorStore}、{@code ChatModel} 各有仓内/第三方两条路径）。
     * 若把第三方混进 Boot 表并把提供者写成仓内类，会把人引向「给已经有序的那一侧加注解」。</p>
     *
     * <p><b>只登记「本仓实际出现过判定」的类型</b>，新增时必须给出全部已知第三方提供者，否则规则会漏判
     * （2026-10-16 第 6 例即因 {@code ChatModel} 未登记而只被运行时用例把守）。</p>
     */
    private static final Map<String, Set<String>> THIRD_PARTY_PROVIDED_TYPES = buildThirdPartyProvidedTypes();

    /**
     * 例外清单（键 = 自动配置类简单名，值 = 理由）。
     *
     * <p>刻意保持为空并逐条要求理由：宽泛白名单会让门禁退化成「谁都放行」，
     * 那比没有门禁更危险（给出虚假安全感）。若确需例外，必须写明「为何顺序不构成风险」。</p>
     */
    private static final Map<String, String> EXEMPTIONS = Map.of();

    /** {@code @ConditionalOnBean(...)} 注解块（捕获括号内原文，含多类型写法）。 */
    private static final Pattern CONDITIONAL_ON_BEAN =
        Pattern.compile("@ConditionalOnBean\\s*\\(([^)]*)\\)");

    /** class 声明（用于把注解归属到类）。 */
    private static final Pattern CLASS_DECLARATION = Pattern.compile(
        "(?m)^\\s*(?:public\\s+|abstract\\s+|final\\s+|static\\s+)*"
            + "(?:class|interface|enum|record)\\s+(\\w+)");

    private static Path repoRoot;

    @BeforeAll
    static void locateRepoRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml"))
                && Files.exists(current.resolve("ypbin-starter-architecture-tests"))) {
                repoRoot = current;
                return;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("未能定位 ypbin-starter 仓库根目录（当前目录："
            + Path.of("").toAbsolutePath() + "）");
    }

    /**
     * 构建 Boot 提供的类型清单。
     *
     * @return 类型简单名 → Boot 侧提供者（用于报错指引）
     */
    private static Map<String, String> buildBootProvidedTypes() {
        Map<String, String> types = new LinkedHashMap<>();
        types.put("StringRedisTemplate",
            "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration");
        types.put("ReactiveStringRedisTemplate",
            "org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration");
        types.put("RedisConnectionFactory",
            "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration");
        types.put("StringReactiveRedisTemplate",
            "org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration");
        types.put("MeterRegistry",
            "org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration");
        // 2026-10-16 补登：一手取证（javap 读 spring-cloud-gateway-server-webflux-5.0.3.jar）确认
        // org.springframework.cloud.gateway.config.GatewayAutoConfiguration 注册了
        // inMemoryRouteDefinitionRepository()（InMemoryRouteDefinitionRepository 实现
        // RouteDefinitionRepository，后者继承 RouteDefinitionLocator 与 RouteDefinitionWriter）、
        // propertiesRouteDefinitionLocator() 与 routeDefinitionLocator(List)。
        // 故这两个类型属「第三方自动配置提供、本仓会判定其存在性」的射程。
        // 提供者登记为 GatewayAutoConfiguration：它是这两个类型的主提供者，且自身带
        // @AutoConfigureAfter(GatewayReactiveLoadBalancerClientAutoConfiguration, GatewayClassPathWarningAutoConfiguration)，
        // 排到它之后即可覆盖其全部路由存储 Bean；另有 GatewayRedisAutoConfiguration
        // （@AutoConfigureBefore(GatewayAutoConfiguration)）经 redisRouteDefinitionRepository()
        // 提供 RedisRouteDefinitionRepository——同一个 RouteDefinitionRepository 接口，
        // 故也产出这两类 Bean，但因为它排在 GatewayAutoConfiguration 之前，
        // 跟着 GatewayAutoConfiguration 走即可一并覆盖，无需重复登记。
        // 关于「用户自定义 Bean 会不会造成误报」：用户 Bean 不会让本规则误报——
        // 规则要求的是「必须显式声明顺序」这条结构性约束，而声明
        // @AutoConfigureAfter(GatewayAutoConfiguration) 对用户自定义来源同样无害且必需
        // （否则用户 Bean 若由别的自动配置注册，条件仍会先于它求值）。
        types.put("RouteDefinitionLocator",
            "org.springframework.cloud.gateway.config.GatewayAutoConfiguration");
        types.put("RouteDefinitionWriter",
            "org.springframework.cloud.gateway.config.GatewayAutoConfiguration");
        return Map.copyOf(types);
    }

    /**
     * 构建「第三方自动配置提供」的类型清单（简单名 → 提供者 FQCN 集合，集合非空）。
     *
     * <p>每一条都须有一手证据（本机 jar 的 {@code AutoConfiguration.imports} 或 {@code javap}），
     * 并在注释里写明该类型的仓内/第三方两条路径分工。</p>
     *
     * @return 类型简单名 → 第三方提供者 FQCN 集合
     */
    private static Map<String, Set<String>> buildThirdPartyProvidedTypes() {
        Map<String, Set<String>> types = new LinkedHashMap<>();
        // 2026-10-16 登记（独立验收保留项 E 的跟进）：一手取证确认 VectorStore 由第三方自动配置提供——
        // spring-ai-autoconfigure-vector-store-redis-2.0.1 / -pgvector-2.0.1 两个 jar 的
        // AutoConfiguration.imports 各自只含 RedisVectorStoreAutoConfiguration / PgVectorStoreAutoConfiguration，
        // 且二者类级均无与本仓 AiRagAutoConfiguration 的顺序声明 ⇒ 后者的
        // @ConditionalOnBean(VectorStore.class) 曾恒假（同类第 5 例，已由 fix/ai-rag-order 修复 + 变异证伪）。
        // 仓内路径另有 AiVectorStoreAutoConfiguration 经 @AutoConfigureBefore(AiRagAutoConfiguration.class)
        // 反向有序——属另一种已覆盖形态，故不在此登记（登记它会把人引向已有序的那一侧）。
        types.put("VectorStore", Set.of(
            "org.springframework.ai.vectorstore.redis.autoconfigure.RedisVectorStoreAutoConfiguration",
            "org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"));
        // 2026-10-16 登记：同类第 6 例（AiChatAutoConfiguration）。一手取证：三个模型 starter 与
        // chat-memory 自动配置的 imports 均注册自身，且 javap -v 显示四者类级 @AutoConfigure{After,Before}
        // 出现次数均为 0。后果：宿主引入任一模型 starter 并配好 yml 后，chatClient 静默不装配；
        // 第三方 chat-memory 在场时 aiChatService 亦整块消失（仓内 AiMemoryAutoConfiguration 路径
        // 由既有 value=Class 约束，仍成立）。
        types.put("ChatModel", Set.of(
            "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration",
            "org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatAutoConfiguration",
            "org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration"));
        types.put("ChatMemory", Set.of(
            "org.springframework.ai.model.chat.memory.autoconfigure.ChatMemoryAutoConfiguration"));
        return Map.copyOf(types);
    }

    /** 收集仓库内全部主源码。 */
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

    /** 取文件所属模块根目录（含 src/main 的那一级）。 */
    private static Path moduleRootOf(Path file) {
        Path current = file;
        while (current != null) {
            if (Files.exists(current.resolve("src/main"))) {
                return current;
            }
            current = current.getParent();
        }
        return file.getParent();
    }

    /**
     * 检查一份自动配置源码，返回违规说明（空 = 合规）。
     *
     * <p>抽成静态方法是为了让「规则有效性自检」能直接喂合成源码——架构测试最大的风险是
     * <b>规则写错却永远通过</b>，那比没有规则更危险。</p>
     *
     * @param source 源码原文（内部会剥离注释与字面量）
     * @return 违规说明列表
     */
    static List<String> violationsOf(String source) {
        List<String> violations = new ArrayList<>();
        if (!source.contains("@AutoConfiguration")) {
            return violations;
        }
        String code = SourceConventionTest.stripCommentsAndLiterals(source);

        // 收集本文件里所有 @ConditionalOnBean 判定到的「外部自动配置提供」类型
        Set<String> risky = new LinkedHashSet<>();
        Matcher matcher = CONDITIONAL_ON_BEAN.matcher(code);
        while (matcher.find()) {
            String arguments = matcher.group(1);
            for (String type : BOOT_PROVIDED_TYPES.keySet()) {
                // 用词边界避免 StringRedisTemplate 命中 StringRedisTemplates 之类的前缀误判
                if (matchesType(arguments, type)) {
                    risky.add(type);
                }
            }
            for (String type : THIRD_PARTY_PROVIDED_TYPES.keySet()) {
                if (matchesType(arguments, type)) {
                    risky.add(type);
                }
            }
        }
        if (risky.isEmpty()) {
            return violations;
        }

        Matcher classMatcher = CLASS_DECLARATION.matcher(code);
        String className = classMatcher.find() ? classMatcher.group(1) : "<未知类>";
        if (EXEMPTIONS.containsKey(className)) {
            return violations;
        }

        // 顺序注解里的 FQCN 是**字符串字面量**，会被 stripCommentsAndLiterals 抹掉 ⇒ 必须回原文取注解块
        String declaredOrdering = declaredOrderingText(source);

        for (String type : risky) {
            boolean bootProvided = BOOT_PROVIDED_TYPES.containsKey(type);
            Set<String> providers = bootProvided
                ? Set.of(BOOT_PROVIDED_TYPES.get(type))
                : THIRD_PARTY_PROVIDED_TYPES.get(type);
            // 要求「声明的顺序」真的点名了该类型的提供者：只声明了无关顺序不算数。
            // 必须覆盖全部已知提供者——只点名其中一个时，宿主若用另一个提供者，条件依旧恒假。
            List<String> missing = new ArrayList<>();
            for (String provider : new TreeSet<>(providers)) {
                if (!declaredOrdering.contains(provider)
                    && !declaredOrdering.contains(simpleName(provider))) {
                    missing.add(provider);
                }
            }
            if (missing.isEmpty()) {
                continue;
            }
            violations.add(className + " → @ConditionalOnBean(" + type + ") 判定的是"
                + (bootProvided ? "Boot" : "第三方") + "自动配置提供的类型，但本类未声明指向其提供者的"
                + "@AutoConfigureAfter/@AutoConfigureBefore（缺：" + String.join(" / ", missing) + "）。"
                + "@ConditionalOnBean 只看得见「先于它处理完」的 Bean 定义，而 cn.ypbin.* 在自动配置排序中"
                + "排在 org.springframework.* 之前 ⇒ 条件恒假 ⇒ 静默退化或 Bean 整块消失（2026-10-15 事故）");
        }
        return violations;
    }

    /**
     * 提取源码中 {@code @AutoConfigureAfter}/{@code @AutoConfigureBefore} 注解的完整正文
     * （含括号内的字符串字面量）。
     *
     * <p><b>为何不能直接用剥注释/字面量后的代码</b>：顺序注解用的是 {@code name = "全限定类名"} 字符串形式，
     * 而这些字面量会被 {@link SourceConventionTest#stripCommentsAndLiterals} 抹掉——那样规则就只能判断
     * 「是否存在某个顺序注解」，无法判断它是否<b>指向正确的提供者</b>（2026-10-16 变异测试实测到这一假保护：
     * 一个已有无关顺序注解的类，删掉真正关键的那条仍会全绿）。</p>
     *
     * @param rawSource 源码原文
     * @return 全部顺序注解正文的拼接
     */
    private static String declaredOrderingText(String rawSource) {
        StringBuilder text = new StringBuilder();
        for (String annotation : List.of("@AutoConfigureAfter", "@AutoConfigureBefore")) {
            int index = rawSource.indexOf(annotation);
            while (index >= 0) {
                int open = rawSource.indexOf('(', index + annotation.length());
                if (open > 0) {
                    int depth = 0;
                    for (int i = open; i < rawSource.length(); i++) {
                        char c = rawSource.charAt(i);
                        if (c == '(') {
                            depth++;
                        } else if (c == ')') {
                            depth--;
                            if (depth == 0) {
                                text.append(rawSource, open, i + 1).append('\n');
                                break;
                            }
                        }
                    }
                }
                index = rawSource.indexOf(annotation, index + annotation.length());
            }
        }
        return text.toString();
    }

    /**
     * 取全限定类名的简单名。
     *
     * @param fqcn 全限定类名
     * @return 简单名（无包名时原样返回）
     */
    private static String simpleName(String fqcn) {
        int dot = fqcn.lastIndexOf('.');
        return dot < 0 ? fqcn : fqcn.substring(dot + 1);
    }

    /**
     * 注解参数是否判定到指定类型（按词边界匹配，避免 {@code StringRedisTemplate} 命中
     * {@code StringRedisTemplates} 之类的前缀误判）。
     *
     * @param arguments {@code @ConditionalOnBean(...)} 的括号内原文
     * @param type      类型简单名
     * @return 命中返回 {@code true}
     */
    private static boolean matchesType(String arguments, String type) {
        return Pattern.compile("\\b" + Pattern.quote(type) + "\\b").matcher(arguments).find();
    }

    @Test
    @DisplayName("★ 判定外部提供类型的 @ConditionalOnBean 必须显式声明自动配置顺序")
    void conditionalOnBeanOfBootTypesRequiresExplicitOrder() throws IOException {
        Set<String> problems = new TreeSet<>();
        int scanned = 0;
        for (Path file : mainSources()) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            if (!source.contains("@AutoConfiguration")) {
                continue;
            }
            scanned++;
            for (String violation : violationsOf(source)) {
                problems.add(repoRoot.relativize(file) + " → " + violation);
            }
        }
        // 防「0 违规 = 没跑到」：本仓有数十个自动配置类
        assertThat(scanned)
            .as("一个自动配置类都没扫到 ⇒ 本门禁是空跑（假绿）")
            .isPositive();
        assertThat(problems)
            .as("@ConditionalOnBean 判定外部（Boot/第三方）提供类型却不声明顺序 ⇒ 条件恒假，静默退化或 Bean 整块消失。"
                + "修法：类级加 @AutoConfigureAfter(提供该类型的自动配置)")
            .isEmpty();
    }

    @Test
    @DisplayName("★ 覆盖自证：本仓确实存在「判定外部提供类型」的自动配置（防规则因目标消失而空转）")
    void ruleMustHaveRealTargets() throws IOException {
        int matched = 0;
        for (Path file : mainSources()) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            if (!source.contains("@AutoConfiguration")) {
                continue;
            }
            String code = SourceConventionTest.stripCommentsAndLiterals(source);
            Matcher matcher = CONDITIONAL_ON_BEAN.matcher(code);
            while (matcher.find()) {
                String arguments = matcher.group(1);
                for (String type : BOOT_PROVIDED_TYPES.keySet()) {
                    if (matchesType(arguments, type)) {
                        matched++;
                    }
                }
                for (String type : THIRD_PARTY_PROVIDED_TYPES.keySet()) {
                    if (matchesType(arguments, type)) {
                        matched++;
                    }
                }
            }
        }
        assertThat(matched)
            .as("本仓当前应有若干处「判定 Boot/第三方提供类型」的 @ConditionalOnBean；若为 0 说明目标已消失，"
                + "本门禁已成空转，应重新评估它是否还有存在价值")
            .isPositive();
    }

    @Test
    @DisplayName("★ 规则自检：缺顺序声明的合成样例必须被命中，已声明顺序的必须放过")
    void ruleMustCatchViolationAndSpareCompliant() {
        String violating = """
            @AutoConfiguration
            public class SampleAutoConfiguration {
                @Bean
                @ConditionalOnBean(StringRedisTemplate.class)
                public Object store(StringRedisTemplate template) {
                    return template;
                }
            }
            """;
        assertThat(violationsOf(violating))
            .as("缺 @AutoConfigureAfter 的样例必须被判违规（否则规则是假绿）")
            .hasSize(1);

        String compliant = """
            @AutoConfiguration
            @AutoConfigureAfter(name = "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration")
            public class SampleAutoConfiguration {
                @Bean
                @ConditionalOnBean(StringRedisTemplate.class)
                public Object store(StringRedisTemplate template) {
                    return template;
                }
            }
            """;
        assertThat(violationsOf(compliant))
            .as("已声明 @AutoConfigureAfter 的样例必须放过")
            .isEmpty();

        String nonBootType = """
            @AutoConfiguration
            public class SampleAutoConfiguration {
                @Bean
                @ConditionalOnBean(DataScopeHandler.class)
                public Object store(DataScopeHandler handler) {
                    return handler;
                }
            }
            """;
        assertThat(violationsOf(nonBootType))
            .as("仓库内部类型的判定不在本规则射程内（避免误报逼人加宽泛白名单）")
            .isEmpty();

        // 注释里提到该类型不得被判违规（门禁必须作用在剥离注释后的代码上）
        String commentedOnly = """
            @AutoConfiguration
            public class SampleAutoConfiguration {
                /** 说明：本类不判定 StringRedisTemplate（历史演进备注） */
                @Bean
                public Object store() {
                    return null;
                }
            }
            """;
        assertThat(violationsOf(commentedOnly))
            .as("注释里提到类型不得被判违规")
            .isEmpty();

        // 第三方提供类型：同样必须被命中（2026-10-16 第 6 例的回归护栏）
        String thirdPartyViolating = """
            @AutoConfiguration
            public class SampleAutoConfiguration {
                @Bean
                @ConditionalOnBean(ChatModel.class)
                public Object chatClient(ChatModel model) {
                    return model;
                }
            }
            """;
        assertThat(violationsOf(thirdPartyViolating))
            .as("判定第三方提供类型却不声明顺序的样例必须被判违规（且报错应指向第三方提供者而非仓内类）")
            .singleElement()
            .asString()
            .contains("第三方自动配置提供")
            .contains("OpenAiChatAutoConfiguration");

        // 合规样例必须覆盖该类型的**全部**已知提供者（多提供者类型少点名一个仍会判违规，见下）
        String thirdPartyCompliant = """
            @AutoConfiguration
            @AutoConfigureAfter(name = {
                "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration",
                "org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatAutoConfiguration",
                "org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration"
            })
            public class SampleAutoConfiguration {
                @Bean
                @ConditionalOnBean(ChatModel.class)
                public Object chatClient(ChatModel model) {
                    return model;
                }
            }
            """;
        assertThat(violationsOf(thirdPartyCompliant))
            .as("已声明与该类型全部提供者的顺序 ⇒ 必须放过")
            .isEmpty();

        // 2026-10-16 变异测试发现的假保护：只声明「某个无关的顺序注解」不得算合规。
        // 真实案例：AiChatAutoConfiguration 原本已有 @AutoConfigureAfter(AiMemoryAutoConfiguration.class)，
        // 若规则只判断「是否存在顺序注解」，则删掉真正关键的第三方 name={...} 后门禁仍会全绿。
        String unrelatedOrderOnly = """
            @AutoConfiguration
            @AutoConfigureAfter(SomeUnrelatedAutoConfiguration.class)
            public class SampleAutoConfiguration {
                @Bean
                @ConditionalOnBean(ChatModel.class)
                public Object chatClient(ChatModel model) {
                    return model;
                }
            }
            """;
        assertThat(violationsOf(unrelatedOrderOnly))
            .as("只声明了与提供者无关的顺序注解 ⇒ 必须仍判违规（否则门禁是假保护）")
            .singleElement()
            .asString()
            .contains("缺：")
            .contains("OpenAiChatAutoConfiguration");

        // 多提供者类型：只点名其中一个提供者不算合规——宿主用另一个时条件依旧恒假
        String partiallyOrdered = """
            @AutoConfiguration
            @AutoConfigureAfter(name = "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration")
            public class SampleAutoConfiguration {
                @Bean
                @ConditionalOnBean(ChatModel.class)
                public Object chatClient(ChatModel model) {
                    return model;
                }
            }
            """;
        assertThat(violationsOf(partiallyOrdered))
            .as("多提供者类型只覆盖其中一个 ⇒ 必须报出缺失的其余提供者")
            .singleElement()
            .asString()
            .contains("DeepSeekChatAutoConfiguration")
            .contains("OllamaChatAutoConfiguration");
    }
}

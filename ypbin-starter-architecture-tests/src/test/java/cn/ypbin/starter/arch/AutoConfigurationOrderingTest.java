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
 * <p><b>覆盖范围与边界（有意收窄，避免误报）</b>：只检查判定类型落在
 * {@link #BOOT_PROVIDED_TYPES} 白名单内的情形。该白名单只收「由 Boot 自身自动配置提供、
 * 且本仓确实依赖其顺序」的类型；仓库内部类型（如 {@code DataScopeHandler}、
 * {@code SseUserIdResolver}）之间的顺序由各自的 {@code @AutoConfigureBefore/After} 单独表达，
 * 不在本规则射程内（判错会制造大量误报，反而逼人加宽泛白名单）。</p>
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

        boolean declaresOrder = code.contains("@AutoConfigureAfter") || code.contains("@AutoConfigureBefore");
        if (declaresOrder) {
            // 已显式声明顺序：满足本规则的全部要求（具体方向是否正确由阅读与运行时用例保证）
            return violations;
        }

        // 收集本文件里所有 @ConditionalOnBean 判定到的 Boot 提供类型
        Set<String> risky = new LinkedHashSet<>();
        Matcher matcher = CONDITIONAL_ON_BEAN.matcher(code);
        while (matcher.find()) {
            String arguments = matcher.group(1);
            for (String type : BOOT_PROVIDED_TYPES.keySet()) {
                // 用词边界避免 StringRedisTemplate 命中 StringRedisTemplates 之类的前缀误判
                if (Pattern.compile("\\b" + Pattern.quote(type) + "\\b").matcher(arguments).find()) {
                    risky.add(type);
                }
            }
        }

        Matcher classMatcher = CLASS_DECLARATION.matcher(code);
        String className = classMatcher.find() ? classMatcher.group(1) : "<未知类>";
        if (EXEMPTIONS.containsKey(className)) {
            return violations;
        }

        for (String type : risky) {
            violations.add(className + " → @ConditionalOnBean(" + type + ") 判定的是 Boot 自动配置提供的类型（"
                + BOOT_PROVIDED_TYPES.get(type) + "），但本类未声明 @AutoConfigureAfter/@AutoConfigureBefore。"
                + "@ConditionalOnBean 只看得见「先于它处理完」的 Bean 定义，而 cn.ypbin.* 在 Boot 排序中"
                + "排在 org.springframework.* 之前 ⇒ 条件恒假 ⇒ 静默退化或 Bean 整块消失（2026-10-15 事故）");
        }
        return violations;
    }

    @Test
    @DisplayName("★ 判定 Boot 提供类型的 @ConditionalOnBean 必须显式声明自动配置顺序")
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
            .as("@ConditionalOnBean 判定 Boot 提供类型却不声明顺序 ⇒ 条件恒假，静默退化或多副本功能失效。"
                + "修法：类级加 @AutoConfigureAfter(提供该类型的 Boot 自动配置)")
            .isEmpty();
    }

    @Test
    @DisplayName("★ 覆盖自证：本仓确实存在「判定 Boot 提供类型」的自动配置（防规则因目标消失而空转）")
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
                    if (Pattern.compile("\\b" + Pattern.quote(type) + "\\b").matcher(arguments).find()) {
                        matched++;
                    }
                }
            }
        }
        assertThat(matched)
            .as("本仓当前应有若干处「判定 Boot 提供类型」的 @ConditionalOnBean；若为 0 说明目标已消失，"
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
    }
}

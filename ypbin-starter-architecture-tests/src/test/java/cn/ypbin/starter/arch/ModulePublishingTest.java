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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 发布模块清单的一致性约束。
 *
 * <p><strong>为什么需要这条门禁</strong>：{@code maven.deploy.skip=true} 对 Central 发布插件
 * （{@code central-publishing-maven-plugin}，以 {@code extensions=true} 接管 deploy 生命周期）**无效**，
 * 它的 {@code excludeArtifacts} 也只按 artifactId 比对、覆盖不到新模块。于是「非发布模块写进根 pom 主
 * {@code <modules>}」这种最自然的写法，会让空 jar（无签名、无 sources/javadoc）混进 Central 上传包，
 * 而**只能在发布上传后由 Central 校验阶段暴露**（3.0.0 首次发布正是这样失败的）。</p>
 *
 * <p>因此约定：非发布模块必须由根 pom 的 {@code dev-only}（{@code activeByDefault}）profile 承载，
 * {@code -Prelease} 激活 release profile 时该 profile 自动失效，这些模块便不进入发布反应堆。
 * 本测试把该约定变成构建失败。</p>
 *
 * @author wenbin
 * @since 2026-09-14
 */
class ModulePublishingTest {

    private static Path repoRoot;

    private static String rootPom;

    @BeforeAll
    static void loadRootPom() throws IOException {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml"))
                && Files.exists(current.resolve("ypbin-starter-architecture-tests"))) {
                repoRoot = current;
                break;
            }
            current = current.getParent();
        }
        if (repoRoot == null) {
            throw new IllegalStateException("未能定位仓库根目录：" + Path.of("").toAbsolutePath());
        }
        rootPom = Files.readString(repoRoot.resolve("pom.xml"), StandardCharsets.UTF_8);
    }

    /** 取根 pom 顶层（profiles 之前）的 modules 列表 */
    static List<String> topLevelModules(String pom) {
        Matcher matcher = Pattern.compile("<modules>(.*?)</modules>", Pattern.DOTALL).matcher(pom);
        return matcher.find() ? moduleEntries(matcher.group(1)) : List.of();
    }

    /** 取指定 profile 的 modules 列表 */
    static List<String> profileModules(String pom, String profileId) {
        Matcher profile = Pattern.compile(
            "<profile>\\s*<id>" + Pattern.quote(profileId) + "</id>(.*?)</profile>", Pattern.DOTALL).matcher(pom);
        if (!profile.find()) {
            return List.of();
        }
        Matcher modules = Pattern.compile("<modules>(.*?)</modules>", Pattern.DOTALL).matcher(profile.group(1));
        return modules.find() ? moduleEntries(modules.group(1)) : List.of();
    }

    private static List<String> moduleEntries(String modulesBlock) {
        List<String> entries = new ArrayList<>();
        Matcher matcher = Pattern.compile("<module>([^<]+)</module>").matcher(modulesBlock);
        while (matcher.find()) {
            entries.add(matcher.group(1).trim());
        }
        return entries;
    }

    /** 该模块 pom 是否声明了「不发布」 */
    private static boolean skipPublishing(Path moduleDir) throws IOException {
        Path pom = moduleDir.resolve("pom.xml");
        if (!Files.exists(pom)) {
            return false;
        }
        String content = Files.readString(pom, StandardCharsets.UTF_8);
        return content.contains("<maven.deploy.skip>true</maven.deploy.skip>")
            || content.contains("<gpg.skip>true</gpg.skip>");
    }

    @Test
    @DisplayName("清单解析自检：顶层 modules 与 dev-only profile 应解析出预期内容")
    void moduleListParsingShouldBeAccurate() {
        List<String> top = topLevelModules(rootPom);
        List<String> devOnly = profileModules(rootPom, "dev-only");

        assertThat(top).as("顶层 modules 解析失败或为空").contains("ypbin-starter-core", "ypbin-starter-web");
        assertThat(devOnly).as("dev-only profile 未解析出模块，门禁会静默失效")
            .contains("ypbin-starter-architecture-tests", "ypbin-starter-benchmarks");
        assertThat(top).as("非发布模块不得出现在顶层 modules（否则会混进 Central 上传包）")
            .doesNotContain("ypbin-starter-architecture-tests", "ypbin-starter-benchmarks");
    }

    @Test
    @DisplayName("声明 maven.deploy.skip / gpg.skip 的模块必须由 dev-only profile 承载")
    void nonPublishedModulesShouldBeOutOfReleaseReactor() throws IOException {
        Set<String> top = new LinkedHashSet<>(topLevelModules(rootPom));
        Set<String> devOnly = new LinkedHashSet<>(profileModules(rootPom, "dev-only"));
        Set<String> problems = new LinkedHashSet<>();

        try (Stream<Path> stream = Files.walk(repoRoot)) {
            for (Path pom : stream
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().equals("pom.xml"))
                .filter(path -> !path.toString().contains("/target/"))
                .toList()) {
                Path moduleDir = pom.getParent();
                if (moduleDir.equals(repoRoot) || !skipPublishing(moduleDir)) {
                    continue;
                }
                String name = repoRoot.relativize(moduleDir).toString();
                if (top.contains(name)) {
                    problems.add(name + " → 在顶层 <modules> 中：会导致其未签名产物混进 Central 上传包，"
                        + "请移入 dev-only profile");
                } else if (!devOnly.contains(name)) {
                    problems.add(name + " → 既不在顶层也不在 dev-only profile 中，模块不会参与任何常规构建");
                }
            }
        }

        assertThat(problems)
            .as("非发布模块必须由根 pom 的 dev-only profile 承载（-Prelease 会跳过该 profile），"
                + "且 `-Psbom` 中也要补一份以保证 SBOM 覆盖完整")
            .isEmpty();
    }

    @Test
    @DisplayName("dev-only profile 里的模块必须确实是非发布模块（防止把发布模块误挪出去）")
    void devOnlyProfileShouldOnlyContainNonPublishedModules() throws IOException {
        Set<String> problems = new LinkedHashSet<>();
        for (String name : profileModules(rootPom, "dev-only")) {
            Path moduleDir = repoRoot.resolve(name);
            if (!Files.isDirectory(moduleDir)) {
                problems.add(name + " → 目录不存在");
            } else if (!skipPublishing(moduleDir)) {
                problems.add(name + " → 未声明 maven.deploy.skip/gpg.skip，却放在 dev-only 中，"
                    + "会导致它不会被发布");
            }
        }
        assertThat(problems).isEmpty();
    }
}

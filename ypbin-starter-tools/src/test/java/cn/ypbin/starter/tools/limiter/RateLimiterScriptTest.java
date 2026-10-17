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
package cn.ypbin.starter.tools.limiter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

/**
 * 限流 Lua 脚本结构测试（issue #92）。
 *
 * <p><b>为什么是「结构断言」而不是「真实 Redis 执行」</b>：本模块测试作用域没有 Testcontainers /
 * embedded-redis（Redis 相关用例一律 {@code mock(StringRedisTemplate.class)}），且不允许为此引入新依赖；
 * 本机也没有可用的 Redis 与 Lua 解释器。因此这里退而验证脚本的**控制流与返回语义**这一可复现的
 * 契约：{@code current==1} 与 {@code TTL==-1} 两个分支都必须补 {@code EXPIRE}，且返回值语义不变。
 * 真实 Redis 上的原子执行仍未被本测试覆盖，属明确登记的未核实点。</p>
 *
 * <p>断言前先剥掉注释行再压平空白，避免注释里的措辞或换行影响匹配。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
class RateLimiterScriptTest {

    /** 首次计数（current==1）设窗口过期。 */
    private static final Pattern FIRST_INCREMENT_EXPIRES = Pattern.compile(
        "if\\s+current\\s*==\\s*1\\s+then\\s+redis\\.call\\(\\s*['\"]EXPIRE['\"]\\s*,\\s*KEYS\\[1\\]\\s*,\\s*ARGV\\[1\\]\\s*\\)");

    /** TTL 兜底：键存在但无过期（TTL==-1）时补 EXPIRE，否则计数只增不减、键永久驻留。 */
    private static final Pattern TTL_FALLBACK_EXPIRES = Pattern.compile(
        "elseif\\s+redis\\.call\\(\\s*['\"]TTL['\"]\\s*,\\s*KEYS\\[1\\]\\s*\\)\\s*==\\s*-1\\s+then\\s+"
            + "redis\\.call\\(\\s*['\"]EXPIRE['\"]\\s*,\\s*KEYS\\[1\\]\\s*,\\s*ARGV\\[1\\]\\s*\\)");

    private static final Pattern TTL_PROBE = Pattern.compile("redis\\.call\\(\\s*['\"]TTL['\"]");

    private static final Pattern EXPIRE_CALL = Pattern.compile("redis\\.call\\(\\s*['\"]EXPIRE['\"]");

    private static final Pattern INCR_CALL = Pattern.compile("redis\\.call\\(\\s*['\"]INCR['\"]\\s*,\\s*KEYS\\[1\\]\\s*\\)");

    /** classpath 脚本（去掉注释行、压平空白后的可匹配形态）。 */
    private static final String SCRIPT = readScript();

    @Test
    @DisplayName("首次计数设置窗口过期（原行为保留）")
    void firstIncrementSetsExpiry() {
        assertThat(INCR_CALL.matcher(SCRIPT).find()).as("必须 INCR 计数键").isTrue();
        assertThat(FIRST_INCREMENT_EXPIRES.matcher(SCRIPT).find())
            .as("current==1 时必须 EXPIRE 建立窗口")
            .isTrue();
    }

    @Test
    @DisplayName("TTL==-1 兜底：键已存在但无过期时补 EXPIRE（issue #92 修复点）")
    void missingTtlIsBackfilled() {
        assertThat(TTL_PROBE.matcher(SCRIPT).find())
            .as("必须先探测 TTL，否则无法判断键是否缺失过期时间")
            .isTrue();
        assertThat(TTL_FALLBACK_EXPIRES.matcher(SCRIPT).find())
            .as("TTL==-1 时补 EXPIRE，否则残留的无过期键会让用户被永久限流")
            .isTrue();
    }

    @Test
    @DisplayName("EXPIRE 只在两处出现，且返回值语义保持不变（返回当前累计次数）")
    void expireOnlyOnTheTwoGuardBranches() {
        Matcher expire = EXPIRE_CALL.matcher(SCRIPT);
        int count = 0;
        while (expire.find()) {
            count++;
        }
        assertThat(count)
            .as("EXPIRE 必须恰好出现在 current==1 与 TTL==-1 两个分支，不得无条件执行")
            .isEqualTo(2);
        assertThat(SCRIPT.trim())
            .as("脚本以 return current 结束：返回值仍是窗口内累计次数，语义不变")
            .endsWith("return current");
    }

    private static String readScript() {
        ClassPathResource resource = new ClassPathResource("META-INF/ypbin/rate_limiter.lua");
        try (InputStream in = resource.getInputStream()) {
            String raw = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
            return raw.lines()
                .map(String::trim)
                .filter(line -> !line.startsWith("--"))
                .reduce((left, right) -> left + ' ' + right)
                .orElseThrow(() -> new IllegalStateException("限流 Lua 脚本为空"));
        } catch (IOException ex) {
            throw new IllegalStateException("读取限流 Lua 脚本失败", ex);
        }
    }
}

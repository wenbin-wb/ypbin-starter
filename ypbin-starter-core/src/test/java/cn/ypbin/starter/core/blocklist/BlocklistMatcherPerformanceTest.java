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
package cn.ypbin.starter.core.blocklist;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 匹配器性能基线（非严格基准；用于发现数量级退化）。
 *
 * <p>100 条路径规则、10_000 次匹配。门限刻意放宽到 5 秒，避免 CI 抖动误报；实测值打印在
 * stdout，评审时与 issue #134 的 148µs/100 条基线对比。</p>
 *
 * @author wenbin
 * @since 2026-11-04
 */
class BlocklistMatcherPerformanceTest {

    @Test
    @DisplayName("100 条规则 × 10_000 次匹配基线")
    void matchBaseline() {
        List<BlocklistRule> rules = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            rules.add(new BlocklistRule(String.valueOf(i), BlocklistMatchType.PATH,
                "/blocked/" + i, null, true, "benchmark"));
        }
        BlocklistMatcher matcher = new BlocklistMatcher();
        BlocklistRequest request = new BlocklistRequest("203.0.113.1", "Mozilla/5.0",
            "/system/%2e%2e/public/x");
        Instant now = Instant.now();
        for (int i = 0; i < 1_000; i++) {
            matcher.match(request, rules, now);
        }

        int iterations = 10_000;
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            matcher.match(request, rules, now);
        }
        long elapsedNanos = System.nanoTime() - start;
        long nanosPerMatch = elapsedNanos / iterations;
        System.out.printf("[baseline] blocklist match: %d rules, %d iterations, %d ns/match%n",
            rules.size(), iterations, nanosPerMatch);

        assertThat(elapsedNanos).isLessThan(5_000_000_000L);
    }
}

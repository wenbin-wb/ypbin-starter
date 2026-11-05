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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Redis key hash tag 约定用例（Cluster 同 slot 的低成本断言）。
 *
 * @author wenbin
 * @since 2026-11-04
 */
class BlocklistKeysTest {

    @Test
    @DisplayName("规则与临时键共用 {rules} hash tag")
    void rulesAndTempKeyShareTag() {
        assertThat(BlocklistKeys.RULES).contains("{rules}");
        assertThat(BlocklistKeys.RULES + ":tmp:abc").contains("{rules}");
        assertThat(BlocklistKeys.BAN_INDEX).contains("{rules}");
    }

    @Test
    @DisplayName("同一 IP 的封禁键与违规键共用 {ip:<ip>} hash tag")
    void perIpKeysShareTag() {
        assertThat(BlocklistKeys.ban("1.2.3.4")).contains("{ip:1.2.3.4}");
        assertThat(BlocklistKeys.violation("1.2.3.4")).contains("{ip:1.2.3.4}");
        String ipv6 = BlocklistKeys.canonicalIp("2001:db8::1");
        assertThat(BlocklistKeys.ban("2001:db8::1")).contains("{ip:" + ipv6 + "}");
        assertThat(BlocklistKeys.violation("2001:db8::1")).contains("{ip:" + ipv6 + "}");
    }

    @Test
    @DisplayName("非法 IP 统一落到 unknown，不能注入 hash tag")
    void invalidIpIsCanonicalized() {
        assertThat(BlocklistKeys.canonicalIp("face.de")).isEqualTo("unknown");
        assertThat(BlocklistKeys.ban("face.de")).contains("{ip:unknown}");
    }
}

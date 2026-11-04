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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 规则编解码用例。
 *
 * @author wenbin
 * @since 2026-11-04
 */
class BlocklistRuleCodecTest {

    private final BlocklistRuleCodec codec = new BlocklistRuleCodec();

    @Test
    @DisplayName("含分隔符/中文/换行的字段可无损往返")
    void roundTrip() {
        BlocklistRule rule = new BlocklistRule("42", BlocklistMatchType.USER_AGENT,
            "scrapy|bot\n第二行", Instant.parse("2026-11-05T00:00:00Z"), true, "恶意|爬虫");

        BlocklistRule decoded = codec.decode(codec.encode(rule));

        assertThat(decoded).isNotNull();
        assertThat(decoded.getId()).isEqualTo("42");
        assertThat(decoded.getType()).isEqualTo(BlocklistMatchType.USER_AGENT);
        assertThat(decoded.getValue()).isEqualTo("scrapy|bot\n第二行");
        assertThat(decoded.getExpireAt()).isEqualTo(rule.getExpireAt());
        assertThat(decoded.isEnabled()).isTrue();
        assertThat(decoded.getReason()).isEqualTo("恶意|爬虫");
    }

    @Test
    @DisplayName("无到期时间与空字段可往返")
    void roundTripNulls() {
        BlocklistRule rule = new BlocklistRule(null, BlocklistMatchType.IP, "10.0.0.1", null, false, null);

        BlocklistRule decoded = codec.decode(codec.encode(rule));

        assertThat(decoded).isNotNull();
        assertThat(decoded.getId()).isNull();
        assertThat(decoded.getExpireAt()).isNull();
        assertThat(decoded.isEnabled()).isFalse();
        assertThat(decoded.getReason()).isNull();
    }

    @Test
    @DisplayName("type 为空时编码给出可读异常")
    void nullTypeIsRejected() {
        BlocklistRule rule = new BlocklistRule("1", BlocklistMatchType.IP, "1.2.3.4",
            null, true, "x");
        rule.setType(null);

        assertThatThrownBy(() -> codec.encode(rule))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("匹配类型");
    }

    @Test
    @DisplayName("格式非法返回 null 而不是抛异常")
    void invalidFormat() {
        assertThat(codec.decode(null)).isNull();
        assertThat(codec.decode("")).isNull();
        assertThat(codec.decode("bad")).isNull();
        assertThat(codec.decode("UNKNOWN||x|-|1|")).isNull();
        assertThat(codec.decode("IP||x|not-a-number|1|")).isNull();
    }
}

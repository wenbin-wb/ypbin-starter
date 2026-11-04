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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.jspecify.annotations.Nullable;

/**
 * 黑名单规则的 Redis 存储编码。
 *
 * <p>用 {@code |} 分隔、自由文本字段做 Base64，避免规则值里的分隔符/换行破坏存储格式，
 * 也避免 starter-core 为了一个编码引入 JSON 依赖。编码格式属于内部契约，只在本类与
 * 管理端发布器之间使用。</p>
 *
 * @author wenbin
 * @since 2026-11-04
 */
public class BlocklistRuleCodec {

    private static final String SEPARATOR = "|";

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    /**
     * 编码一条规则。
     *
     * @param rule 规则
     * @return 可写入 Redis Hash 的字符串
     */
    public String encode(BlocklistRule rule) {
        if (rule.getType() == null) {
            throw new IllegalArgumentException("规则匹配类型不能为空");
        }
        String id = encodeText(rule.getId());
        String value = encodeText(rule.getValue());
        String reason = encodeText(rule.getReason());
        String expire = rule.getExpireAt() == null ? "-" : String.valueOf(rule.getExpireAt().toEpochMilli());
        return rule.getType().name() + SEPARATOR + id + SEPARATOR + value + SEPARATOR + expire
            + SEPARATOR + (rule.isEnabled() ? '1' : '0') + SEPARATOR + reason;
    }

    /**
     * 解码一条规则。
     *
     * @param encoded 编码字符串
     * @return 规则；格式非法时返回 {@code null}
     */
    @Nullable
    public BlocklistRule decode(@Nullable String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        String[] parts = encoded.split("\\" + SEPARATOR, -1);
        if (parts.length != 6) {
            return null;
        }
        try {
            BlocklistMatchType type = BlocklistMatchType.valueOf(parts[0]);
            String id = decodeText(parts[1]);
            String value = decodeText(parts[2]);
            Instant expireAt = "-".equals(parts[3]) ? null : Instant.ofEpochMilli(Long.parseLong(parts[3]));
            boolean enabled = "1".equals(parts[4]);
            String reason = decodeText(parts[5]);
            return new BlocklistRule(id, type, value == null ? "" : value, expireAt, enabled, reason);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String encodeText(@Nullable String text) {
        return text == null ? "" : ENCODER.encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    @Nullable
    private static String decodeText(String text) {
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new String(DECODER.decode(text), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}

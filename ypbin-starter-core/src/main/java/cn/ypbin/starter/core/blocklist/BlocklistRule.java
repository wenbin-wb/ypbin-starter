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

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

/**
 * 一条访问黑名单规则。
 *
 * <p>规则可以来自静态配置（{@code ypbin.security.blocklist.rules}）或管理端下发（Redis/内存），
 * 二者使用同一模型，匹配逻辑完全一致。</p>
 *
 * @author wenbin
 * @since 2026-11-04
 */
@Getter
@Setter
public class BlocklistRule {

    /** 规则标识；静态配置可为空，管理端下发时使用数据库主键。 */
    @Nullable
    private String id;

    /** 匹配维度。 */
    private BlocklistMatchType type = BlocklistMatchType.IP;

    /** 匹配值；语义由 {@link #type} 决定。 */
    private String value = "";

    /** 到期时间；为空表示长期有效。 */
    @Nullable
    private Instant expireAt;

    /** 是否启用；默认启用。 */
    private boolean enabled = true;

    /** 命中原因，用于日志与后台展示。 */
    @Nullable
    private String reason;

    public BlocklistRule() {
    }

    public BlocklistRule(@Nullable String id, BlocklistMatchType type, String value,
                         @Nullable Instant expireAt, boolean enabled, @Nullable String reason) {
        this.id = id;
        this.type = type;
        this.value = value;
        this.expireAt = expireAt;
        this.enabled = enabled;
        this.reason = reason;
    }

    /**
     * 判断规则在指定时刻是否生效。
     *
     * @param now 当前时刻
     * @return 已启用且未到期返回 {@code true}
     */
    public boolean isEffective(Instant now) {
        return enabled && (expireAt == null || expireAt.isAfter(now));
    }
}

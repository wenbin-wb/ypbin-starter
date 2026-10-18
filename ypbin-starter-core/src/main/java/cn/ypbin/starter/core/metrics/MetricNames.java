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
package cn.ypbin.starter.core.metrics;

import java.util.regex.Pattern;

/**
 * 指标命名约定（跨服务可观测性契约的单一来源）。
 *
 * <p><b>为什么需要它</b>：starter 与宿主此前各自拼指标名（{@code ypbin.cache.requests}、
 * {@code iot.access.egress.dropped}、{@code iot.alert.*} …），同一次故障会产出两个名字，
 * 值班不知道该看哪个。收敛为「<b>统一前缀 {@value #PREFIX}</b> + 模块段 + 名称段」后，
 * 至少保证 starter 自身与采纳本约定的宿主同前缀，便于按前缀做发现与看板聚合。</p>
 *
 * <p><b>约定</b>：</p>
 * <ul>
 *   <li><b>前缀</b>：全部指标以 {@value #PREFIX} 开头，格式
 *       {@code ypbin.<module>.<name>}（{@link #of(String, String)} 生成）；</li>
 *   <li><b>标签键</b>：统一用本类的 {@code TAG_*} 常量（{@code type} / {@code result} /
 *       {@code level} / {@code module}），<b>禁止散落字面量</b>——标签键拼错是静默分裂指标的高发原因；</li>
 *   <li><b>改名纪律</b>：指标名/标签值是<b>对外契约</b>（看板、告警规则、SLI 口径都依赖它），
 *       改名必须同步更新运维看板与告警并登记变更；不得为了「名字更好看」单方面改名。</li>
 * </ul>
 *
 * <p><b>兼容性</b>：本类是<b>新增</b>约定，不要求存量指标改名（改名属对外契约破坏，需单独评估）。
 * 存量 {@code ypbin.cache.*} / {@code ypbin.lock.*} 已符合前缀约定，可直接沿用。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
public final class MetricNames {

    /** 全部指标的公共前缀。 */
    public static final String PREFIX = "ypbin";

    /** 标签键：错误/事件类型。 */
    public static final String TAG_TYPE = "type";

    /** 标签键：结果（如 {@code hit} / {@code miss} / {@code success} / {@code failure}）。 */
    public static final String TAG_RESULT = "result";

    /** 标签键：层级（如多级缓存的 {@code l1} / {@code l2}）。 */
    public static final String TAG_LEVEL = "level";

    /** 标签键：模块名（由 {@link MetricsSupport} 自动补齐）。 */
    public static final String TAG_MODULE = "module";

    /** 名称段分隔符。 */
    private static final String SEPARATOR = ".";

    /** 单段名称合法形式：字母/数字/下划线/连字符，允许内部点号（如 {@code null.sentinel.hits}）。 */
    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9_.-]*[A-Za-z0-9])?");

    private static final Pattern CONSECUTIVE_DOTS = Pattern.compile("\\.\\.");

    private MetricNames() {
    }

    /**
     * 拼装指标名：{@code ypbin.<module>.<name>}。
     *
     * @param module 模块段（如 {@code cache} / {@code lock}），非空且为合法名称段
     * @param name   名称段（如 {@code requests} / {@code errors}），非空且为合法名称段
     * @return 完整指标名
     * @throws IllegalArgumentException 参数为空/空白/含非法字符（含连续点号）时
     */
    public static String of(String module, String name) {
        return PREFIX + SEPARATOR + requireSegment(module, "module") + SEPARATOR + requireSegment(name, "name");
    }

    /**
     * 校验名称段：空、空白、非法字符、连续点号一律拒绝。
     *
     * <p>拒绝而不是「清洗」是刻意的：指标名是契约，静默改写会让埋点与看板各拼一份，
     * 正是本约定要消灭的问题（禁静默降级）。</p>
     *
     * @param value 名称段
     * @param field 字段名（用于错误信息）
     * @return 原值
     */
    public static String requireSegment(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        if (value.length() != value.strip().length() || !SEGMENT.matcher(value).matches()
            || CONSECUTIVE_DOTS.matcher(value).find()) {
            throw new IllegalArgumentException(
                field + " 不是合法的指标名称段（只允许字母/数字/下划线/连字符与内部点号，且不得以点号开头或结尾）: " + value);
        }
        return value;
    }
}

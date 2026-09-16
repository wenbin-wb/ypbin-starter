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
package cn.ypbin.starter.tracking.core;

import cn.ypbin.starter.tracking.core.TrackingEventCatalog.EventSchema;
import cn.ypbin.starter.tracking.core.TrackingEventCatalog.PropertySchema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * 事件目录两层合并器：把宿主项目目录（project）叠加到 starter 基线目录（base）之上。
 *
 * <p><strong>冲突规则：同一事件码以 project 为准</strong>。覆盖是本方案里唯一有意偏离本仓
 *「禁静默降级」铁律的地方，因此本类在发生覆盖时<strong>必定逐字段打印差异</strong>——
 * description 变化、properties 新增/删除、属性 type/maxLength 变化，并在返回值里给出结构化的
 * {@link OverrideDiff}，供调用方在日志之外的可观测位置（如监控、管理界面）继续标注。
 * 两边定义完全相同的事件码不产生任何差异输出（避免噪音），也没有字段被改动。</p>
 *
 * <p>本类只做纯映射合并，<strong>不触碰类路径</strong>：资源发现与解析由
 * {@code TrackingCatalogLoader} 负责，故合并逻辑可脱离 Spring 容器直接单测。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
public final class TrackingCatalogMerger {

    private TrackingCatalogMerger() {
    }

    /**
     * 合并 base 与 project 两层目录。
     *
     * @param base    基线目录（starter 内置，不可为空）
     * @param project 宿主项目目录；未提供宿主目录时传空映射（此时不输出任何日志，行为与只有 base 时完全一致）
     * @param log     调用方日志（覆盖差异只在此处打印，绝不静默覆盖）
     * @return 合并结果（含被覆盖事件码及其字段差异）
     */
    public static MergeResult merge(Map<String, EventSchema> base, Map<String, EventSchema> project, Logger log) {
        if (project.isEmpty()) {
            return new MergeResult(base, List.of());
        }
        Map<String, EventSchema> merged = new LinkedHashMap<>(base);
        List<String> addedCodes = new ArrayList<>();
        List<OverrideDiff> overrides = new ArrayList<>();
        for (Map.Entry<String, EventSchema> entry : project.entrySet()) {
            String eventCode = entry.getKey();
            @Nullable EventSchema baseSchema = merged.put(eventCode, entry.getValue());
            if (baseSchema == null) {
                addedCodes.add(eventCode);
                continue;
            }
            List<String> changes = describeChanges(baseSchema, entry.getValue());
            if (!changes.isEmpty()) {
                overrides.add(new OverrideDiff(eventCode, changes));
            }
        }
        for (OverrideDiff override : overrides) {
            log.warn("[ypbin-starter] tracking event schema overridden by project catalog: code={} changes={}",
                override.eventCode(), override.changes());
        }
        for (String addedCode : addedCodes) {
            log.info("[ypbin-starter] tracking event code added by project catalog: code={}", addedCode);
        }
        log.info("[ypbin-starter] tracking event catalog merged: base={} project={} added={} overridden={} total={}",
            base.size(), project.size(), addedCodes.size(), overrides.size(), merged.size());
        return new MergeResult(Map.copyOf(merged), List.copyOf(overrides));
    }

    /**
     * 逐字段描述 project 相对 base 的差异（顺序确定，便于断言与审计）。
     *
     * @param baseSchema    base 侧定义
     * @param projectSchema project 侧定义
     * @return 差异描述列表；两边等价时为空列表
     */
    private static List<String> describeChanges(EventSchema baseSchema, EventSchema projectSchema) {
        List<String> changes = new ArrayList<>();
        if (!baseSchema.description().equals(projectSchema.description())) {
            changes.add("description: \"" + baseSchema.description() + "\" -> \"" + projectSchema.description() + "\"");
        }
        Set<String> baseNames = baseSchema.properties().keySet();
        Set<String> projectNames = projectSchema.properties().keySet();
        Set<String> addedNames = new TreeSet<>(projectNames);
        addedNames.removeAll(baseNames);
        if (!addedNames.isEmpty()) {
            changes.add("properties.added: " + addedNames);
        }
        Set<String> removedNames = new TreeSet<>(baseNames);
        removedNames.removeAll(projectNames);
        if (!removedNames.isEmpty()) {
            changes.add("properties.removed: " + removedNames);
        }
        Map<String, String> changedNames = new TreeMap<>();
        for (Map.Entry<String, PropertySchema> entry : projectSchema.properties().entrySet()) {
            @Nullable PropertySchema baseProperty = baseSchema.properties().get(entry.getKey());
            if (baseProperty == null) {
                continue;
            }
            PropertySchema projectProperty = entry.getValue();
            List<String> fieldChanges = new ArrayList<>();
            if (!baseProperty.type().equals(projectProperty.type())) {
                fieldChanges.add("type " + baseProperty.type() + " -> " + projectProperty.type());
            }
            if (baseProperty.maxLength() != projectProperty.maxLength()) {
                fieldChanges.add("maxLength " + baseProperty.maxLength() + " -> " + projectProperty.maxLength());
            }
            if (!fieldChanges.isEmpty()) {
                changedNames.put(entry.getKey(), String.join(", ", fieldChanges));
            }
        }
        for (Map.Entry<String, String> entry : changedNames.entrySet()) {
            changes.add("properties." + entry.getKey() + ": " + entry.getValue());
        }
        return List.copyOf(changes);
    }

    /**
     * 合并结果。
     *
     * @param schemas   合并后的完整目录（project 覆盖 base）
     * @param overrides 发生实际字段变化的事件码及其差异（schema 完全相同的事件码不列入，避免噪音）
     * @author wenbin
     * @since 2026-09-16
     */
    public record MergeResult(Map<String, EventSchema> schemas, List<OverrideDiff> overrides) {

        /**
         * 被 project 覆盖且 schema 确有变化的事件码集合。
         *
         * @return 不可变事件码集合；无覆盖时为空集合
         */
        public Set<String> overriddenCodes() {
            Set<String> codes = new LinkedHashSet<>();
            for (OverrideDiff override : overrides) {
                codes.add(override.eventCode());
            }
            return Set.copyOf(codes);
        }
    }

    /**
     * 单条事件的覆盖差异。
     *
     * @param eventCode 事件码
     * @param changes   逐字段差异描述（description / properties 新增删除 / 属性 type、maxLength）
     * @author wenbin
     * @since 2026-09-16
     */
    public record OverrideDiff(String eventCode, List<String> changes) {
    }
}

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

import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.ObjectMapper;

/**
 * 事件目录注册表：采集入口判定「事件码是否已登记、属性是否在白名单内」的唯一依据。
 *
 * <p>目录分两层加载，两者共用同一个类路径位置 {@code META-INF/ypbin/tracking-events.json}
 * （base 的事实源为仓库内 {@code docs/tracking-events.json}，由 {@code tools/export-tracking-events.mjs} 生成）：</p>
 * <ul>
 *   <li><strong>base</strong>：starter jar 内自带的那份，平台通用事件；</li>
 *   <li><strong>project</strong>：宿主项目 jar 内的同名资源，宿主自有事件。存在时<strong>叠加在 base 之上</strong>，
 *       同一事件码<strong>以 project 为准</strong>；覆盖发生时由 {@link TrackingCatalogMerger} 逐字段打印差异，
 *       绝不静默覆盖。</li>
 * </ul>
 *
 * <p>两层资源<strong>显式各读一份再合并</strong>：同名资源同时存在于两个 jar 时，
 * {@code ClassLoader#getResource} 只返回命中顺序里的一个，而顺序不可靠，故不使用单资源读取。</p>
 *
 * <p>构造期一次性载入并转为不可变结构。<strong>载入失败即失败</strong>（缺 base、project 多份、schemaVersion
 * 不识别、资源损坏、事件重复）：不做「空目录照常运行」的降级——那样会让所有事件静默变成「未登记」而被丢弃，
 * 是典型的静默失效。</p>
 *
 * @author wenbin
 * @since 2026-09-15
 */
public final class TrackingEventCatalog {

    /** 运行时资源路径（生成物）；base 与 project 两层共用同一路径，靠资源所在归档区分 */
    public static final String RESOURCE_PATH = "META-INF/ypbin/tracking-events.json";

    private final Map<String, EventSchema> schemas;

    /** 被宿主项目目录覆盖且 schema 确有变化的事件码（可观测的覆盖标注） */
    private final Set<String> overriddenCodes;

    /**
     * 创建注册表：加载 base 并叠加宿主项目目录（若类路径上存在）。
     *
     * @param objectMapper 用于解析目录资源的 Jackson 3 映射器
     */
    public TrackingEventCatalog(ObjectMapper objectMapper) {
        TrackingCatalogMerger.MergeResult merged = TrackingCatalogLoader.load(objectMapper);
        this.schemas = merged.schemas();
        this.overriddenCodes = merged.overriddenCodes();
    }

    /**
     * 用给定的 schema 直接创建注册表（宿主扩展点）。
     *
     * <p>宿主若要在目录之外登记自己的事件码，可以构造一份覆盖本模块的 Bean：默认实现只认 classpath 上的
     * 生成资源，**刻意不提供「运行时追加」**——让宿主显式给出完整集合，比暴露一个可变的全局注册表更可控
     * （后者会让「目录是唯一事实源」这条不变量失效）。</p>
     *
     * <p>以本构造器构造时<strong>不读 classpath、也不做两层合并</strong>：宿主给出的集合即完整目录，
     * {@link #overriddenCodes()} 恒为空集。</p>
     *
     * @param schemas 事件码到 schema 的映射（构造期做不可变复制）
     */
    public TrackingEventCatalog(Map<String, EventSchema> schemas) {
        this.schemas = Map.copyOf(schemas);
        this.overriddenCodes = Set.of();
    }

    /**
     * 查询事件码对应的 schema。
     *
     * @param eventCode 事件码
     * @return schema；未登记时为 {@code null}（调用方必须据此拒绝事件）
     */
    public @Nullable EventSchema schema(String eventCode) {
        return schemas.get(eventCode);
    }

    /**
     * 判断事件码是否已登记。
     *
     * @param eventCode 事件码
     * @return 已登记返回 {@code true}
     */
    public boolean isRegistered(String eventCode) {
        return schemas.containsKey(eventCode);
    }

    /**
     * 全部已登记事件码。
     *
     * @return 不可变事件码集合
     */
    public Set<String> codes() {
        return schemas.keySet();
    }

    /**
     * 被宿主项目目录覆盖、且 schema 相对 base 确有变化的事件码。
     *
     * <p>覆盖差异在加载期已按字段打印 WARN 日志，本方法把同一事实暴露给程序化使用方
     * （监控指标、管理界面等），使「该条已被项目覆盖」始终可观测。以
     * {@link #TrackingEventCatalog(Map)} 构造时不读两层目录，恒为空集。</p>
     *
     * @return 不可变事件码集合；无覆盖时为空集合
     */
    public Set<String> overriddenCodes() {
        return overriddenCodes;
    }

    /**
     * 单个事件的属性白名单定义。
     *
     * @param description 事件说明
     * @param properties  属性名到属性定义的映射（不可变）
     * @author wenbin
     * @since 2026-09-15
     */
    public record EventSchema(String description, Map<String, PropertySchema> properties) {
    }

    /**
     * 单个属性的约束。
     *
     * @param type      属性类型（string / integer / number / boolean）
     * @param maxLength 字符串属性的最大长度；非字符串或未声明时为 0（表示不限制）
     * @author wenbin
     * @since 2026-09-15
     */
    public record PropertySchema(String type, int maxLength) {
    }
}

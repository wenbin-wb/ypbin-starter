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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;

/**
 * 事件目录注册表：采集入口判定「事件码是否已登记、属性是否在白名单内」的唯一依据。
 *
 * <p>目录来源是生成物 {@code META-INF/ypbin/tracking-events.json}（事实源为仓库内
 * {@code docs/tracking-events.json}，由 {@code tools/export-tracking-events.mjs} 生成），
 * 在构造期一次性载入并转为不可变结构。</p>
 *
 * <p><strong>载入失败即失败</strong>（缺资源、schemaVersion 不识别、事件重复）：不做「空目录照常运行」
 * 的降级——那样会让所有事件静默变成「未登记」而被丢弃，是典型的静默失效。</p>
 *
 * @author wenbin
 * @since 2026-09-15
 */
public final class TrackingEventCatalog {

    /** 运行时资源路径（生成物） */
    public static final String RESOURCE_PATH = "META-INF/ypbin/tracking-events.json";

    /** 当前支持的目录 schema 版本 */
    private static final int SUPPORTED_SCHEMA_VERSION = 1;

    private final Map<String, EventSchema> schemas;

    /**
     * 创建注册表。
     *
     * @param objectMapper 用于解析目录资源的 Jackson 3 映射器
     */
    public TrackingEventCatalog(ObjectMapper objectMapper) {
        this.schemas = load(objectMapper);
    }

    /**
     * 用给定的 schema 直接创建注册表（宿主扩展点）。
     *
     * <p>宿主若要在目录之外登记自己的事件码，可以构造一份覆盖本模块的 Bean：默认实现只认 classpath 上的
     * 生成资源，**刻意不提供「运行时追加」**——让宿主显式给出完整集合，比暴露一个可变的全局注册表更可控
     * （后者会让「目录是唯一事实源」这条不变量失效）。</p>
     *
     * @param schemas 事件码到 schema 的映射（构造期做不可变复制）
     */
    public TrackingEventCatalog(Map<String, EventSchema> schemas) {
        this.schemas = Map.copyOf(schemas);
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

    private static Map<String, EventSchema> load(ObjectMapper objectMapper) {
        ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
        if (!resource.exists()) {
            throw new IllegalStateException(
                "[ypbin-starter] tracking event catalog resource not found: " + RESOURCE_PATH
                    + "；请执行 node tools/export-tracking-events.mjs 重新生成后重新构建");
        }
        CatalogDocument document;
        try (InputStream inputStream = resource.getInputStream()) {
            document = objectMapper.readValue(inputStream, CatalogDocument.class);
        } catch (IOException ex) {
            throw new UncheckedIOException("[ypbin-starter] failed to read tracking event catalog: " + RESOURCE_PATH, ex);
        }
        if (document == null || document.schemaVersion() != SUPPORTED_SCHEMA_VERSION) {
            throw new IllegalStateException("[ypbin-starter] unsupported tracking catalog schemaVersion: "
                + (document == null ? "null" : document.schemaVersion()));
        }
        Map<String, EventSchema> loaded = new LinkedHashMap<>();
        for (EventDefinition definition : document.events()) {
            Map<String, PropertySchema> properties = new LinkedHashMap<>();
            for (PropertyDefinition property : definition.properties()) {
                // 目录只为字符串属性声明 maxLength；非字符串缺省视为不限制（0）
                int maxLength = property.maxLength() == null ? 0 : property.maxLength();
                properties.put(property.name(), new PropertySchema(property.type(), maxLength));
            }
            EventSchema previous = loaded.put(definition.code(),
                new EventSchema(definition.description(), Map.copyOf(properties)));
            if (previous != null) {
                throw new IllegalStateException("[ypbin-starter] duplicated tracking event code: " + definition.code());
            }
        }
        return Map.copyOf(loaded);
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

    /**
     * 目录资源文档结构（仅供 Jackson 绑定，不对外暴露）。
     *
     * @param schemaVersion 目录版本
     * @param events        事件定义列表
     * @author wenbin
     * @since 2026-09-15
     */
    private record CatalogDocument(int schemaVersion, List<EventDefinition> events) {
    }

    /**
     * 事件定义（仅供 Jackson 绑定）。
     *
     * @param code        事件码
     * @param description 事件说明
     * @param properties  属性定义
     * @author wenbin
     * @since 2026-09-15
     */
    private record EventDefinition(String code, String description, List<PropertyDefinition> properties) {
    }

    /**
     * 属性定义（仅供 Jackson 绑定）。
     *
     * @param name        属性名
     * @param type        属性类型
     * @param maxLength   最大长度；目录只为 string 类型声明，其余类型缺省（故可空）
     * @param description 属性说明
     * @author wenbin
     * @since 2026-09-15
     */
    private record PropertyDefinition(String name, String type, @Nullable Integer maxLength, String description) {
    }
}

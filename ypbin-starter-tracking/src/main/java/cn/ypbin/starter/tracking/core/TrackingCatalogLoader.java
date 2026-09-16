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

import cn.ypbin.starter.tracking.core.TrackingCatalogMerger.MergeResult;
import cn.ypbin.starter.tracking.core.TrackingEventCatalog.EventSchema;
import cn.ypbin.starter.tracking.core.TrackingEventCatalog.PropertySchema;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 事件目录资源加载器：显式读取类路径上<strong>全部</strong> {@code META-INF/ypbin/tracking-events.json}，
 * 区分「starter 基线（base）」与「宿主项目（project）」两层后交给 {@link TrackingCatalogMerger} 合并。
 *
 * <p><strong>为什么不能只取一份</strong>：同名资源同时存在于 starter jar 与宿主 jar 时，
 * {@code ClassPathResource}（内部即 {@code ClassLoader#getResource}）只返回命中顺序里的第一个，
 * 而命中顺序取决于类路径顺序、<strong>不可靠</strong>；因此本类改用
 * {@code classpath*:} 前缀，其语义是「经 {@code ClassLoader#getResources} 取回全部同名资源」。</p>
 *
 * <p><strong>两层的判定方式</strong>：不做「谁先谁后」的猜测，而是比较资源所在归档与
 * {@link TrackingEventCatalog} 自身类文件所在归档是否一致——一致即 base（starter 自己的 jar /
 * {@code target/classes}），不一致即 project（宿主 jar）。</p>
 *
 * <p><strong>不降级</strong>：base 缺失、project 出现多份（无法确定覆盖优先级）、资源无法解析或
 * schemaVersion 不支持，全部直接抛错并带完整堆栈，绝不「空目录照常运行」或忽略宿主文件。</p>
 *
 * <p><strong>已知边界（归档判定成立的前提）</strong>：判定依赖「starter 的目录资源与
 * {@link TrackingEventCatalog} 类文件处于同一归档」这一事实。若宿主用 uber/shade 打包，把 starter 的
 * 目录资源与宿主自己的同名资源合并进<strong>同一个</strong>归档，两层会退化为一份（被判为 base），
 * 此时无从区分——需要分层能力的宿主请勿合并该资源。宿主侧出现两份及以上同名资源则直接启动失败，
 * 避免按不可靠顺序静默择一。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
final class TrackingCatalogLoader {

    private static final Logger log = LoggerFactory.getLogger(TrackingCatalogLoader.class);

    /** Spring 的「取回全部同名类路径资源」前缀；语义见 PathMatchingResourcePatternResolver */
    private static final String CLASSPATH_ALL_PREFIX = "classpath*:";

    /** 目录资源的类路径位置（两层共用同名路径，靠所在归档区分） */
    private static final String CATALOG_LOCATION_PATTERN =
        CLASSPATH_ALL_PREFIX + TrackingEventCatalog.RESOURCE_PATH;

    /** 当前支持的目录 schema 版本 */
    private static final int SUPPORTED_SCHEMA_VERSION = 1;

    /** 本类文件在归档内的条目路径（用于定位 starter 自身归档） */
    private static final String SELF_CLASS_ENTRY =
        "/" + TrackingEventCatalog.class.getName().replace('.', '/') + ".class";

    /** 目录资源在归档内的条目路径 */
    private static final String CATALOG_ENTRY = "/" + TrackingEventCatalog.RESOURCE_PATH;

    private TrackingCatalogLoader() {
    }

    /**
     * 从类路径加载 base（必需）与 project（可选）两层目录并合并。
     *
     * @param objectMapper Jackson 映射器
     * @return 合并结果
     */
    static MergeResult load(ObjectMapper objectMapper) {
        return classifyAndMerge(objectMapper, findAll());
    }

    /**
     * 取回类路径上的全部同名目录资源。
     *
     * @return 全部命中资源（顺序不作保证，也不被本类依赖）
     */
    static List<Resource> findAll() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            return List.of(resolver.getResources(CATALOG_LOCATION_PATTERN));
        } catch (IOException ex) {
            throw new UncheckedIOException(
                "[ypbin-starter] failed to scan tracking event catalog resources: " + CATALOG_LOCATION_PATTERN, ex);
        }
    }

    /**
     * 按「归档归属」把资源分为 base / project 两层，读取后合并。
     *
     * @param objectMapper Jackson 映射器
     * @param catalogs     全部命中资源（可由单测注入）
     * @return 合并结果
     */
    static MergeResult classifyAndMerge(ObjectMapper objectMapper, List<Resource> catalogs) {
        String baseArchive = archiveKey(selfClassLocation(), SELF_CLASS_ENTRY);
        List<Resource> baseCatalogs = new ArrayList<>();
        List<Resource> projectCatalogs = new ArrayList<>();
        for (Resource catalog : catalogs) {
            if (archiveKey(urlOf(catalog), CATALOG_ENTRY).equals(baseArchive)) {
                baseCatalogs.add(catalog);
            } else {
                projectCatalogs.add(catalog);
            }
        }
        if (baseCatalogs.isEmpty()) {
            throw new IllegalStateException(
                "[ypbin-starter] tracking event catalog resource not found: " + TrackingEventCatalog.RESOURCE_PATH
                    + "；请执行 node tools/export-tracking-events.mjs 重新生成后重新构建");
        }
        if (baseCatalogs.size() > 1) {
            // 同一归档内出现多份同名资源属异常，但不影响合并结果（内容同源），故告警而不失败
            log.warn("[ypbin-starter] duplicated tracking event catalog resource in starter archive: {}", baseCatalogs);
        }
        if (projectCatalogs.size() > 1) {
            throw new IllegalStateException("[ypbin-starter] multiple project tracking event catalogs found: "
                + projectCatalogs + "；无法确定覆盖优先级，请只保留一份（宿主项目目录）");
        }
        Map<String, EventSchema> baseSchemas = read(objectMapper, baseCatalogs.get(0));
        Map<String, EventSchema> projectSchemas =
            projectCatalogs.isEmpty() ? Map.of() : read(objectMapper, projectCatalogs.get(0));
        return TrackingCatalogMerger.merge(baseSchemas, projectSchemas, log);
    }

    /**
     * 读取并校验单份目录资源。
     *
     * @param objectMapper Jackson 映射器
     * @param resource     目录资源
     * @return 事件码到 schema 的映射
     */
    private static Map<String, EventSchema> read(ObjectMapper objectMapper, Resource resource) {
        CatalogDocument document;
        try (InputStream inputStream = resource.getInputStream()) {
            document = objectMapper.readValue(inputStream, CatalogDocument.class);
        } catch (JacksonException ex) {
            // Jackson 3 的解析异常是运行时异常，且消息里不含资源位置；不指明是哪一层（base / project）
            // 会让宿主拿到一个无法定位的错误，故在此补上资源描述并保留完整堆栈
            throw new IllegalStateException(
                "[ypbin-starter] failed to parse tracking event catalog: " + resource.getDescription(), ex);
        } catch (IOException ex) {
            throw new UncheckedIOException(
                "[ypbin-starter] failed to read tracking event catalog: " + resource.getDescription(), ex);
        }
        if (document == null || document.schemaVersion() != SUPPORTED_SCHEMA_VERSION) {
            throw new IllegalStateException("[ypbin-starter] unsupported tracking catalog schemaVersion ("
                + resource.getDescription() + "): "
                + (document == null ? "null" : document.schemaVersion()));
        }
        @Nullable List<EventDefinition> definitions = document.events();
        if (definitions == null || definitions.isEmpty()) {
            throw new IllegalStateException(
                "[ypbin-starter] tracking event catalog has no events: " + resource.getDescription());
        }
        Map<String, EventSchema> loaded = new LinkedHashMap<>();
        for (EventDefinition definition : definitions) {
            Map<String, PropertySchema> properties = new LinkedHashMap<>();
            if (definition.properties() != null) {
                for (PropertyDefinition property : definition.properties()) {
                    // 目录只为字符串属性声明 maxLength；非字符串缺省视为不限制（0）
                    int maxLength = property.maxLength() == null ? 0 : property.maxLength();
                    properties.put(property.name(), new PropertySchema(property.type(), maxLength));
                }
            }
            EventSchema previous = loaded.put(definition.code(),
                new EventSchema(definition.description(), Map.copyOf(properties)));
            if (previous != null) {
                throw new IllegalStateException("[ypbin-starter] duplicated tracking event code: " + definition.code()
                    + " (" + resource.getDescription() + ")");
            }
        }
        return Map.copyOf(loaded);
    }

    /**
     * 本类（starter 自身）类文件的 URL。
     *
     * @return 类文件 URL
     */
    private static URL selfClassLocation() {
        URL location = TrackingEventCatalog.class.getResource(SELF_CLASS_ENTRY);
        if (location == null) {
            throw new IllegalStateException("[ypbin-starter] tracking event catalog anchor class not resolvable: "
                + TrackingEventCatalog.class.getName());
        }
        return location;
    }

    /**
     * 资源 URL（读不出 URL 时按不降级原则抛错）。
     *
     * @param resource 类路径资源
     * @return 资源 URL
     */
    private static URL urlOf(Resource resource) {
        try {
            return resource.getURL();
        } catch (IOException ex) {
            throw new UncheckedIOException(
                "[ypbin-starter] failed to resolve tracking event catalog location: " + resource.getDescription(), ex);
        }
    }

    /**
     * 归档标识：把「类文件 URL / 资源 URL」归一化成同一个归档键，使两者可直接比较。
     *
     * <p>去掉归档内条目路径后，再抹平 {@code jar:}/{@code nested:} 前缀与 {@code !/} 分隔符，
     * 于是普通 jar、Spring Boot 可执行 jar 的嵌套 jar、以及 {@code target/classes} 目录三种形态
     * 都能得到稳定可比的键。</p>
     *
     * @param url        类文件或资源 URL
     * @param entry      该 URL 在归档内的条目路径（含前导 {@code /}）
     * @return 归一化归档标识
     */
    private static String archiveKey(URL url, String entry) {
        String value = url.toString();
        if (value.endsWith(entry)) {
            value = value.substring(0, value.length() - entry.length());
        }
        if (value.startsWith("jar:")) {
            value = value.substring("jar:".length());
        }
        while (value.endsWith("/") || value.endsWith("!")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    /**
     * 目录资源文档结构（仅供 Jackson 绑定，不对外暴露）。
     *
     * @param schemaVersion 目录版本
     * @param events        事件定义列表（缺失即为 null，显式报错而不静默跳过）
     * @author wenbin
     * @since 2026-09-16
     */
    private record CatalogDocument(int schemaVersion, @Nullable List<EventDefinition> events) {
    }

    /**
     * 事件定义（仅供 Jackson 绑定）。
     *
     * @param code        事件码
     * @param description 事件说明
     * @param properties  属性定义（缺失即为 null，视为无属性）
     * @author wenbin
     * @since 2026-09-16
     */
    private record EventDefinition(String code, String description,
                                   @Nullable List<PropertyDefinition> properties) {
    }

    /**
     * 属性定义（仅供 Jackson 绑定）。
     *
     * @param name        属性名
     * @param type        属性类型
     * @param maxLength   最大长度；目录只为 string 类型声明，其余类型缺省（故可空）
     * @param description 属性说明
     * @author wenbin
     * @since 2026-09-16
     */
    private record PropertyDefinition(String name, String type, @Nullable Integer maxLength, String description) {
    }
}

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
package cn.ypbin.starter.json.enums;

import cn.ypbin.starter.core.enums.BaseEnum;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.Version;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.deser.Deserializers;
import tools.jackson.databind.introspect.AnnotatedMember;
import tools.jackson.databind.ser.Serializers;

/**
 * 让所有 {@link BaseEnum} 实现按 {@code value} 参与 JSON 序列化/反序列化的 Jackson 模块。
 *
 * <p><b>为什么用自定义 {@link JacksonModule} 而不是 {@code SimpleModule#addSerializer(Class, ...)}</b>：
 * 枚举的序列化器在 Jackson 内部由 {@code findEnumSerializer} 钩子单独解析，
 * {@code SimpleSerializers.addSerializer(Class, ...)} 只对「精确类 / 接口」的 {@code findSerializer}
 * 生效，<b>不会</b>作用于枚举子类。用 {@link JacksonModule} 直接实现 {@link Serializers} /
 * {@link Deserializers} 的枚举钩子，匹配规则完全由我们掌握，也不会与已注册的 {@code SimpleModule}
 * 抢注册顺序。</p>
 *
 * <p><b>三条不覆盖原则</b>（保证「不改变既有枚举行为」）：</p>
 * <ol>
 *   <li>只处理实现了 {@link BaseEnum} 的枚举——其余枚举走 Jackson 原生
 *       {@code name()} / {@code toString()} 逻辑，一个字节都不变；</li>
 *   <li>跳过用 {@code @JsonValue} 标注了取值方法（或类本身）的枚举——那是业务<b>更明确</b>的意图；</li>
 *   <li>跳过 {@code @JsonFormat(shape = POJO/OBJECT)} 的枚举——业务显式要求按对象形态输出。</li>
 * </ol>
 *
 * <p><b>为什么不用 {@code @JsonValue} 就能生效</b>：{@link BaseEnum} 的注释即约定「{@code value}
 * 用于存储/传输」，实现该接口就是选择了「按 value 传输」的语义。若还要强制业务在每个枚举上再写一遍
 * {@code @JsonValue}，那这个接口就毫无价值（#102 指出的「零引用」正是这种重复劳动的后果）。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
public final class BaseEnumModule extends JacksonModule {

    private static final String MODULE_NAME = "ypbin-base-enum";

    @Override
    public String getModuleName() {
        return MODULE_NAME;
    }

    @Override
    public Version version() {
        return Version.unknownVersion();
    }

    @Override
    public void setupModule(SetupContext context) {
        context.addSerializers(new BaseEnumSerializers());
        context.addDeserializers(new BaseEnumDeserializers());
    }

    // ------------------------------------------------------------------ 匹配与索引

    /**
     * 判断一个 JavaType 是否需要本模块接管。
     *
     * <p>三层过滤按「代价从低到高」排列，且 {@code @JsonValue} 判定要访问注解内省，
     * 放在最后以免给所有枚举都付这份代价。</p>
     */
    private static boolean shouldHandle(JavaType type, BeanDescription.@Nullable Supplier description) {
        if (!type.isEnumType()) {
            return false;
        }
        Class<?> raw = type.getRawClass();
        if (!BaseEnum.class.isAssignableFrom(raw)) {
            return false;
        }
        return !hasExplicitJsonShape(raw, description);
    }

    /**
     * 枚举是否用 {@code @JsonValue} / {@code @JsonFormat(shape=POJO|OBJECT)} 显式指定了表示形态。
     *
     * <p>没有 {@link BeanDescription} 时保守返回 {@code false}（按本模块约定处理）——
     * 该情况下 Jackson 后续仍会用业务自定义序列化器覆盖，不会造成错误输出。</p>
     */
    private static boolean hasExplicitJsonShape(Class<?> raw, BeanDescription.@Nullable Supplier description) {
        if (description == null) {
            return false;
        }
        BeanDescription bean = description.get();
        AnnotatedMember jsonValueAccessor = bean.findJsonValueAccessor();
        if (jsonValueAccessor != null) {
            return true;
        }
        // JsonFormat.Value 恒非 null（未标注时返回 EMPTY），无需判空
        JsonFormat.Shape shape = description.findExpectedFormat(raw).getShape();
        if (shape == JsonFormat.Shape.POJO || shape == JsonFormat.Shape.OBJECT) {
            return true;
        }
        // 类上直接标注 @JsonValue（部分写法把注解放在 getter 上，已由 findJsonValueAccessor 覆盖）
        return bean.getClassInfo().hasAnnotation(JsonValue.class);
    }

    /** 按枚举类型缓存索引：同一个枚举被反复序列化时不必每次重建。 */
    private static final class IndexCache {

        private final Map<Class<?>, BaseEnumIndex<?>> cache =
            Collections.synchronizedMap(new IdentityHashMap<>());

        @SuppressWarnings({"unchecked", "rawtypes"})
        BaseEnumIndex<?> get(Class<?> enumType) {
            return cache.computeIfAbsent(enumType, key -> BaseEnumIndex.of((Class) key));
        }
    }

    // ------------------------------------------------------------------ 序列化钩子

    private static final class BaseEnumSerializers implements Serializers {

        private final IndexCache indexCache = new IndexCache();

        @Override
        @Nullable
        public ValueSerializer<?> findEnumSerializer(SerializationConfig config,
                                                     JavaType type,
                                                     BeanDescription.@Nullable Supplier description,
                                                     JsonFormat.@Nullable Value format) {
            if (!shouldHandle(type, description)) {
                return null;
            }
            return newSerializer(indexCache.get(type.getRawClass()));
        }
    }

    /** 构造类型正确的序列化器（索引已按该枚举类型建立，泛型在擦除后按 raw 强转）。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ValueSerializer<?> newSerializer(BaseEnumIndex<?> index) {
        return new BaseEnumSerializer((BaseEnumIndex) index);
    }

    // ------------------------------------------------------------------ 反序列化钩子

    private static final class BaseEnumDeserializers implements Deserializers {

        private final IndexCache indexCache = new IndexCache();

        @Override
        @Nullable
        public ValueDeserializer<?> findEnumDeserializer(JavaType type,
                                                         DeserializationConfig config,
                                                         BeanDescription.@Nullable Supplier description) {
            if (!shouldHandle(type, description)) {
                return null;
            }
            return newDeserializer(indexCache.get(type.getRawClass()));
        }

        @Override
        public boolean hasDeserializerFor(DeserializationConfig config, Class<?> valueType) {
            return valueType.isEnum() && BaseEnum.class.isAssignableFrom(valueType);
        }
    }

    /** 构造类型正确的反序列化器。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ValueDeserializer<?> newDeserializer(BaseEnumIndex<?> index) {
        return new BaseEnumDeserializer((BaseEnumIndex) index);
    }
}

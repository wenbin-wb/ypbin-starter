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
package cn.ypbin.starter.web.enums;

import cn.ypbin.starter.core.enums.BaseEnum;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalConverter;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.util.StringUtils;

/**
 * 让实现 {@link BaseEnum} 的枚举支持按 {@code value} 绑定 URL 参数/表单字段。
 *
 * <p><b>为什么需要它</b>：Spring MVC 对枚举参数的默认转换器 {@code StringToEnumConverterFactory}
 * 只认常量名。于是接口声明 {@code @RequestParam DeviceStatus status} 时，前端按对外契约传
 * {@code value}（{@code ?status=1}）会 400；业务只好把参数改成 {@code String} 自己转，
 * 或者让枚举 {@code name()} 迁就前端——两种结局都破坏了「按 value 传输」的契约。
 * 本工厂让 {@code ?status=1} 直接绑定到枚举，与 JSON / 持久化两侧的 value 语义对齐。</p>
 *
 * <p><b>必须实现 {@link ConditionalConverter}（关键，不是可选优化）</b>：
 * {@code GenericConversionService} 中同类转换器的候选队列是 <b>{@code addFirst}</b> 插入、
 * 按插入逆序匹配——后注册的排在前面。本工厂在 Web 层晚于 Spring 内置工厂注册，
 * 若不带条件判断，它会对<b>所有</b>枚举（含未实现 {@code BaseEnum} 的）先被选中，
 * 从而把 Spring 原生的 {@code name()} 绑定一并破坏。实现 {@code matches} 后，
 * 非 {@code BaseEnum} 目标返回 {@code false}，队列继续向后查找，Spring 内置工厂照旧生效。</p>
 *
 * <p><b>为什么泛型是 {@code ConverterFactory<String, Enum<?>>} 而不是 {@code <String, BaseEnum>}</b>：
 * {@code GenericConversionService} 用 {@code ResolvableType} 从类声明解析出
 * {@code ConvertiblePair(String, Enum)}，再按「目标类型的类层级」查找候选对；
 * 写 {@code BaseEnum} 时配对键会变成 {@code (String, BaseEnum)}，而实际查找键是
 * {@code (String, DeviceStatus)}，能否命中取决于层级探测实现，属脆弱写法。
 * 与 Spring 内置 {@code StringToEnumConverterFactory} 保持一致用 {@code Enum} 最稳妥。</p>
 *
 * <p><b>空值语义</b>：空串与纯空白返回 {@code null}（Spring 对「缺省参数」的常规处理），
 * 不当成「找不到枚举」报错——{@code ?status=} 是前端「清空筛选」的常见写法。</p>
 *
 * <p><b>未匹配必须失败</b>：抛 {@link IllegalArgumentException}，由 Spring 转成 400。
 * 不返回 {@code null}、不静默取第一个常量：那会让「参数写错」变成「静默按第一个枚举处理」，
 * 是典型的静默降级。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
public final class BaseEnumConverterFactory implements ConverterFactory<String, Enum<?>>, ConditionalConverter {

    /** 按枚举类型缓存 value → 常量索引，避免每次请求重扫常量数组。 */
    private final Map<Class<?>, Map<String, ? extends Enum<?>>> indexCache = new ConcurrentHashMap<>();

    /**
     * 只对实现了 {@link BaseEnum} 的枚举目标生效。
     *
     * <p>这是本类「不改变既有枚举绑定行为」的<b>唯一</b>保障：非 {@code BaseEnum} 目标在此返回
     * {@code false}，后续 Spring 内置 {@code StringToEnumConverterFactory} 正常接手。</p>
     *
     * @param sourceType 源类型（String）
     * @param targetType 目标类型
     * @return 目标是 {@code BaseEnum} 枚举时返回 {@code true}
     */
    @Override
    public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
        Class<?> targetClass = targetType.getObjectType();
        return targetClass.isEnum() && BaseEnum.class.isAssignableFrom(targetClass);
    }

    @Override
    public <T extends Enum<?>> Converter<String, T> getConverter(Class<T> targetType) {
        Objects.requireNonNull(targetType, "targetType");
        return new BaseEnumConverter<>(targetType, indexOf(targetType));
    }

    /**
     * 建立（或复用）枚举的 value → 常量索引。
     *
     * @param enumType 枚举类型
     * @return 按 value 文本索引的常量表（不可变）
     * @throws IllegalStateException value 为 null 或重复（枚举定义有误应在首次使用即暴露）
     */
    private Map<String, ? extends Enum<?>> indexOf(Class<?> enumType) {
        return indexCache.computeIfAbsent(enumType, key -> {
            Map<String, Enum<?>> byValue = new LinkedHashMap<>();
            for (Enum<?> constant : key.asSubclass(Enum.class).getEnumConstants()) {
                Object value = ((BaseEnum<?>) constant).getValue();
                if (value == null) {
                    throw new IllegalStateException("枚举 " + key.getName() + " 的常量 "
                        + constant.name() + " 的 value 为 null；BaseEnum#getValue 契约上不允许返回 null");
                }
                String valueText = String.valueOf(value);
                Enum<?> existing = byValue.putIfAbsent(valueText, constant);
                if (existing != null) {
                    throw new IllegalStateException("枚举 " + key.getName() + " 存在重复的 value=" + valueText
                        + "（" + existing.name() + " 与 " + constant.name()
                        + "）；value 必须唯一，否则参数绑定结果不确定");
                }
            }
            return Map.copyOf(byValue);
        });
    }

    /**
     * 具体枚举转换器。
     *
     * @param <T> 枚举类型
     */
    private static final class BaseEnumConverter<T extends Enum<?>> implements Converter<String, T> {

        private final Class<T> targetType;

        private final Map<String, ? extends Enum<?>> index;

        BaseEnumConverter(Class<T> targetType, Map<String, ? extends Enum<?>> index) {
            this.targetType = targetType;
            this.index = index;
        }

        @Override
        @Nullable
        @SuppressWarnings("unchecked")
        public T convert(String source) {
            if (!StringUtils.hasText(source)) {
                return null;
            }
            String valueText = source.trim();
            Enum<?> matched = index.get(valueText);
            if (matched == null) {
                throw new IllegalArgumentException("参数值 '" + valueText + "' 不是 " + targetType.getSimpleName()
                    + " 的合法取值，可选值：" + index.keySet().stream().collect(Collectors.joining(", ")));
            }
            return (T) matched;
        }
    }
}

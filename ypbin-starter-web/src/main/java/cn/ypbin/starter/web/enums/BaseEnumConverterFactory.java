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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalGenericConverter;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.util.StringUtils;

/**
 * 让实现 {@link BaseEnum} 的枚举支持按 {@code value} 绑定 URL 参数/表单字段。
 *
 * <p><b>为什么需要它</b>：Spring MVC 对枚举参数的默认转换器 {@code StringToEnumConverterFactory}
 * 只认常量名。于是接口声明 {@code @RequestParam DeviceStatus status} 时，前端按对外契约传
 * {@code value}（{@code ?status=1}）会 400；业务只好把参数改成 {@code String} 自己转，
 * 或者让枚举 {@code name()} 迁就前端——两种结局都破坏了「按 value 传输」的契约。
 * 本转换器让 {@code ?status=1} 直接绑定到枚举，与 JSON / 持久化两侧的 value 语义对齐。</p>
 *
 * <p><b>为什么实现 {@link GenericConverter} 而不是 {@code ConverterFactory<String, Enum<?>>}</b>：
 * 空字符串在 Spring MVC 中是「参数未提供」的常规形态（{@code ?status=} 表示清空筛选），本类型必须把它
 * 绑定为 {@code null}。但 {@code Converter#convert(S)} 在 Spring 7 中<b>没有</b>标注
 * {@code @Nullable}（实测字节码：{@code Object convert(Object)} 无 return type annotation），
 * 而 {@link GenericConverter#convert(Object, TypeDescriptor, TypeDescriptor)} 的返回类型
 * <b>官方就标注了 {@code org.jspecify.annotations.Nullable}</b>。本模块又是 {@code @NullMarked}
 * （未标注即非空），在 {@code Converter} 上补 {@code @Nullable} 会直接违反 NullAway 的 override 规则
 * （实测报错：「method returns @Nullable, but superclass method ... returns @NonNull」）。
 * 因此选 {@link GenericConverter}：既能让 {@code null} 语义合法表达，又不需要任何
 * {@code @SuppressWarnings} 之类的糊弄手段。<b>Spring 自身的 {@code StringToEnumConverterFactory}
 * 内部对空串同样返回 null，说明这是框架层面的既定行为，不是本类引入的例外。</b></p>
 *
 * <p><b>必须实现 {@link org.springframework.core.convert.converter.ConditionalConverter}（关键）</b>：
 * {@code GenericConversionService$ConvertersForPair} 用 {@code addFirst} 插入、按插入逆序匹配——
 * 后注册的排在前面。本转换器在 Web 层晚于 Spring 内置转换器注册，若不带条件判断，它会先被选中而
 * 对<b>所有</b>枚举（含未实现 {@code BaseEnum} 的）生效，从而把 Spring 原生的 {@code name()} 绑定
 * 一并破坏。{@link #matches} 只在目标「是枚举且实现了 BaseEnum」时返回 {@code true}，
 * 其余目标返回 {@code false}，让队列继续向后查找，Spring 内置转换器照旧生效。</p>
 *
 * <p><b>注册方式</b>：以 {@code (String → Enum)} 的 {@link GenericConverter.ConvertiblePair}
 * 注册到 Spring MVC 的转换服务；具体目标是哪个枚举不在注册时确定，而由每次转换时的
 * {@link TypeDescriptor} 给出，故按类型缓存 value 索引。</p>
 *
 * <p><b>未匹配必须失败</b>：抛 {@link IllegalArgumentException}，由 Spring 转成 400。
 * 不返回 {@code null}、不静默取第一个常量：那会让「参数写错」变成「静默按第一个枚举处理」，
 * 是典型的静默降级。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
public final class BaseEnumConverterFactory implements ConditionalGenericConverter {

    /** 本转换器负责的转换对：String → 任意枚举（具体枚举由运行时目标类型确定）。 */
    private static final Set<GenericConverter.ConvertiblePair> CONVERTIBLE_TYPES =
        Set.of(new GenericConverter.ConvertiblePair(String.class, Enum.class));

    /** 按枚举类型缓存 value → 常量索引，避免每次请求重扫常量数组。 */
    private final Map<Class<?>, Map<String, ? extends Enum<?>>> indexCache = new ConcurrentHashMap<>();

    @Override
    public Set<GenericConverter.ConvertiblePair> getConvertibleTypes() {
        return CONVERTIBLE_TYPES;
    }

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
        return supports(targetClass);
    }

    /**
     * 转换：按 {@code value} 把字符串绑定到目标枚举。
     *
     * <p>空串/纯空白返回 {@code null}（前端「清空筛选」）；{@code null} 源同样返回 {@code null}
     * ——{@code GenericConversionService} 对 null 源走 {@code convertNullSource} 不会进到这里，
     * 这里兼容它是为了防御直接调用方。</p>
     *
     * @param source     源对象（期望 String；{@code null} 或非 String 时返回 {@code null}）
     * @param sourceType 源类型
     * @param targetType 目标类型（必须是实现了 {@code BaseEnum} 的枚举）
     * @return 匹配的枚举常量；空输入返回 {@code null}
     * @throws IllegalArgumentException 值不在枚举的 value 集合中
     */
    @Override
    @Nullable
    public Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
        if (!(source instanceof String sourceText) || !StringUtils.hasText(sourceText)) {
            return null;
        }
        Class<?> enumType = targetType.getObjectType();
        Map<String, ? extends Enum<?>> index = indexOf(enumType);
        String valueText = sourceText.trim();
        Enum<?> matched = index.get(valueText);
        if (matched == null) {
            throw new IllegalArgumentException("参数值 '" + valueText + "' 不是 " + enumType.getSimpleName()
                + " 的合法取值，可选值：" + index.keySet().stream().collect(Collectors.joining(", ")));
        }
        return matched;
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
     * 判断某个类型是否由本转换器接管。
     *
     * @param enumType 候选类型
     * @return 是实现了 {@code BaseEnum} 的枚举时返回 {@code true}
     */
    public static boolean supports(Class<?> enumType) {
        Objects.requireNonNull(enumType, "enumType");
        return enumType.isEnum() && BaseEnum.class.isAssignableFrom(enumType);
    }
}

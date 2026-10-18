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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.ypbin.starter.core.enums.BaseEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.format.support.FormattingConversionService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@link BaseEnumConverterFactory} 单元测试 + {@code ?status=1} 参数绑定端到端验证。
 *
 * <p><b>两层验证的分工</b>：转换器自身的语义（空串、未知值、去重）直接调 {@code matches}/{@code convert}
 * 断言，失败时责任方明确；「{@code ?status=1} 真的能绑定到枚举」必须走 MockMvc 真实参数解析链，
 * 因为最容易出错的恰恰是「注册进了哪个 Registry、条件匹配是否让位给 Spring 内置工厂」。
 * 只做前者会漏掉整条链路没接上的情况。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
class BaseEnumConverterFactoryTest {

    /** 整型 value 枚举（URL 参数按 value 传）。 */
    public enum Status implements BaseEnum<Integer> {
        DISABLED(0, "停用"),
        ENABLED(1, "启用");

        private final Integer value;
        private final String description;

        Status(Integer value, String description) {
            this.value = value;
            this.description = description;
        }

        @Override
        public Integer getValue() {
            return value;
        }

        @Override
        public String getDescription() {
            return description;
        }
    }

    /** 未实现 BaseEnum 的枚举：绑定必须仍按常量名走 Spring 内置转换器（回归保护）。 */
    public enum PlainStatus {
        ON,
        OFF
    }

    /** 测试控制器：把绑定结果原样回显。 */
    @RestController
    static class TestController {

        @GetMapping("/status")
        String status(@RequestParam Status status) {
            return status.name();
        }

        @GetMapping("/optional")
        String optional(@RequestParam(required = false) Status status) {
            return status == null ? "null" : status.name();
        }

        @GetMapping("/plain")
        String plain(@RequestParam PlainStatus status) {
            return status.name();
        }
    }

    /**
     * 与生产环境同构的转换服务：{@link DefaultFormattingConversionService} 含 Spring 内置
     * {@code StringToEnumConverterFactory}（只认常量名），再叠加本工厂——
     * 这正是「不破坏既有枚举绑定」必须验证的顺序。
     *
     * <p>刻意不用裸 {@code FormattingConversionService}：那不含内置格式化器/转换器，
     * 回归用例会因「根本没有内置枚举转换器」而假通过。</p>
     */
    private static FormattingConversionService conversionService() {
        DefaultFormattingConversionService service = new DefaultFormattingConversionService();
        service.addConverter(new BaseEnumConverterFactory());
        return service;
    }

    /** 直接调用转换器（等价于 GenericConversionService 内部的调用形态）。 */
    private static Object convert(BaseEnumConverterFactory factory, Class<?> targetType, String source) {
        return factory.convert(source, TypeDescriptor.valueOf(String.class),
            TypeDescriptor.valueOf(targetType));
    }

    /** 装配了转换器的 standalone MockMvc（standalone 默认不含本转换器，需显式注入）。 */
    private static MockMvc mockMvc() {
        FormattingConversionService service = conversionService();
        return MockMvcBuilders.standaloneSetup(new TestController())
            .setConversionService(service)
            .build();
    }

    // ------------------------------------------------------------------ 端到端绑定

    @Test
    @DisplayName("★ URL 参数按 value 绑定到枚举：?status=1 → Status.ENABLED")
    void requestParamShouldBindByValue() throws Exception {
        mockMvc().perform(get("/status").param("status", "1"))
            .andExpect(status().isOk())
            .andExpect(content().string("ENABLED"));
    }

    @Test
    @DisplayName("★ URL 参数按 value 绑定：?status=0 → Status.DISABLED")
    void requestParamShouldBindZeroValue() throws Exception {
        mockMvc().perform(get("/status").param("status", "0"))
            .andExpect(status().isOk())
            .andExpect(content().string("DISABLED"));
    }

    @Test
    @DisplayName("★ 可选参数缺省时绑定为 null（不报错）")
    void absentOptionalParamShouldBeNull() throws Exception {
        mockMvc().perform(get("/optional"))
            .andExpect(status().isOk())
            .andExpect(content().string("null"));
    }

    @Test
    @DisplayName("★ 空串参数绑定为 null（前端「清空筛选」的常见写法）")
    void emptyOptionalParamShouldBeNull() throws Exception {
        mockMvc().perform(get("/optional").param("status", ""))
            .andExpect(status().isOk())
            .andExpect(content().string("null"));
    }

    @Test
    @DisplayName("★ 未知 value 返回 400，不静默按第一个常量处理")
    void unknownValueShouldBeBadRequest() throws Exception {
        mockMvc().perform(get("/status").param("status", "9"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("★ 回归：未实现 BaseEnum 的枚举仍按常量名绑定（内置转换器不被覆盖）")
    void nonBaseEnumShouldStillBindByName() throws Exception {
        mockMvc().perform(get("/plain").param("status", "ON"))
            .andExpect(status().isOk())
            .andExpect(content().string("ON"));
    }

    @Test
    @DisplayName("★ 回归：未实现 BaseEnum 的枚举传数字不被本转换器接受（仍走内置 name 匹配，故 400）")
    void nonBaseEnumShouldNotAcceptNumericValue() throws Exception {
        mockMvc().perform(get("/plain").param("status", "1"))
            .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ 条件匹配

    @Test
    @DisplayName("matches 只对 BaseEnum 目标为真（非 BaseEnum 枚举让位给内置转换器）")
    void matchesShouldOnlyAcceptBaseEnumTargets() {
        BaseEnumConverterFactory factory = new BaseEnumConverterFactory();
        TypeDescriptor stringType = TypeDescriptor.valueOf(String.class);

        assertThat(factory.matches(stringType, TypeDescriptor.valueOf(Status.class))).isTrue();
        assertThat(factory.matches(stringType, TypeDescriptor.valueOf(PlainStatus.class))).isFalse();
        assertThat(factory.matches(stringType, TypeDescriptor.valueOf(Integer.class))).isFalse();
        // 转换对只声明 String → Enum，具体枚举由运行时目标类型确定
        assertThat(factory.getConvertibleTypes()).hasSize(1).allSatisfy(pair -> {
            assertThat(pair.getSourceType()).isEqualTo(String.class);
            assertThat(pair.getTargetType()).isEqualTo(Enum.class);
        });
    }

    // ------------------------------------------------------------------ 转换语义

    @Test
    @DisplayName("convert 按 value 精确匹配")
    void converterShouldMatchByValue() {
        BaseEnumConverterFactory factory = new BaseEnumConverterFactory();

        assertThat(convert(factory, Status.class, "1")).isEqualTo(Status.ENABLED);
        assertThat(convert(factory, Status.class, "0")).isEqualTo(Status.DISABLED);
    }

    @Test
    @DisplayName("convert 对首尾空白做 trim（URL 中带空格不应 400）")
    void converterShouldTrimWhitespace() {
        BaseEnumConverterFactory factory = new BaseEnumConverterFactory();

        assertThat(convert(factory, Status.class, " 1 ")).isEqualTo(Status.ENABLED);
    }

    @Test
    @DisplayName("convert 对空串/纯空白返回 null")
    void converterShouldReturnNullForBlank() {
        BaseEnumConverterFactory factory = new BaseEnumConverterFactory();

        assertThat(convert(factory, Status.class, "")).isNull();
        assertThat(convert(factory, Status.class, "   ")).isNull();
    }

    @Test
    @DisplayName("convert 对未知 value 抛 IllegalArgumentException 并列出可选值")
    void converterShouldFailOnUnknownValue() {
        BaseEnumConverterFactory factory = new BaseEnumConverterFactory();

        assertThatThrownBy(() -> convert(factory, Status.class, "99"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("99")
            .hasMessageContaining("0")
            .hasMessageContaining("1");
    }

    @Test
    @DisplayName("重复 value 的枚举在首次使用时显式失败")
    void duplicateValueShouldFailFast() {
        BaseEnumConverterFactory factory = new BaseEnumConverterFactory();

        assertThatThrownBy(() -> convert(factory, DuplicateValue.class, "1"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("重复的 value");
    }

    /** value 冲突的非法枚举。 */
    enum DuplicateValue implements BaseEnum<Integer> {
        FIRST(1, "第一个"),
        SECOND(1, "第二个");

        private final Integer value;
        private final String description;

        DuplicateValue(Integer value, String description) {
            this.value = value;
            this.description = description;
        }

        @Override
        public Integer getValue() {
            return value;
        }

        @Override
        public String getDescription() {
            return description;
        }
    }

    @Test
    @DisplayName("supports 对 null 类型显式失败；对非枚举类型返回 false")
    void supportsShouldValidateInput() {
        assertThatThrownBy(() -> BaseEnumConverterFactory.supports(null))
            .isInstanceOf(NullPointerException.class);
        assertThat(BaseEnumConverterFactory.supports(Status.class)).isTrue();
        assertThat(BaseEnumConverterFactory.supports(PlainStatus.class)).isFalse();
        assertThat(BaseEnumConverterFactory.supports(String.class)).isFalse();
    }

    @Test
    @DisplayName("null 源与非 String 源返回 null（防御直接调用方）")
    void nullOrNonStringSourceShouldYieldNull() {
        BaseEnumConverterFactory factory = new BaseEnumConverterFactory();
        TypeDescriptor target = TypeDescriptor.valueOf(Status.class);

        assertThat(factory.convert(null, TypeDescriptor.valueOf(String.class), target)).isNull();
        assertThat(factory.convert(1, TypeDescriptor.valueOf(Integer.class), target)).isNull();
    }

    @Test
    @DisplayName("转换器可复用（索引缓存不改变结果）")
    void converterShouldBeReusable() {
        BaseEnumConverterFactory factory = new BaseEnumConverterFactory();

        assertThat(convert(factory, Status.class, "1")).isEqualTo(Status.ENABLED);
        assertThat(convert(factory, Status.class, "1")).isEqualTo(Status.ENABLED);
        assertThat(convert(factory, Status.class, "0")).isEqualTo(Status.DISABLED);
    }
}

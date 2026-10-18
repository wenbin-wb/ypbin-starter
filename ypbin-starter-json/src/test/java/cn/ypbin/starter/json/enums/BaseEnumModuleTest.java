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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.ypbin.starter.core.enums.BaseEnum;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

/**
 * {@link BaseEnumModule} 单元测试：value 序列化/反序列化 + 不改变既有枚举行为。
 *
 * <p><b>为什么这些「不改变」用例比功能用例更重要</b>：本模块是<b>全局</b>注册的，一旦匹配范围写宽
 * （例如把所有枚举都接管），全仓每一个既有枚举的对外 JSON 都会变——那是破坏性变更，且只在联调时
 * 才被发现。故逐个钉住三条豁免：非 {@code BaseEnum} 枚举、{@code @JsonValue} 枚举、
 * {@code @JsonFormat(shape=OBJECT)} 枚举。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
class BaseEnumModuleTest {

    // ------------------------------------------------------------------ 测试枚举

    /** 整型 value 的枚举（最常见的字典/状态枚举形态）。 */
    enum Status implements BaseEnum<Integer> {
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

    /** 字符串 value 的枚举。 */
    enum Level implements BaseEnum<String> {
        LOW("low", "低"),
        HIGH("high", "高");

        private final String value;
        private final String description;

        Level(String value, String description) {
            this.value = value;
            this.description = description;
        }

        @Override
        public String getValue() {
            return value;
        }

        @Override
        public String getDescription() {
            return description;
        }
    }

    /** 未实现 BaseEnum 的普通枚举：本模块必须完全不碰它（回归保护）。 */
    enum PlainEnum {
        ALPHA,
        BETA
    }

    /** 既有业务枚举写法：用 {@code @JsonValue} 自己声明取值方法，本模块必须让位。 */
    enum LegacyJsonValueEnum {
        ONE(1),
        TWO(2);

        private final int code;

        LegacyJsonValueEnum(int code) {
            this.code = code;
        }

        @JsonValue
        public int getCode() {
            return code;
        }
    }

    /** 显式要求按对象形态输出：本模块必须让位。 */
    @JsonFormat(shape = JsonFormat.Shape.OBJECT)
    enum ObjectShapeEnum implements BaseEnum<Integer> {
        A(1, "甲");

        private final Integer value;
        private final String description;

        ObjectShapeEnum(Integer value, String description) {
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

    /** 数据载体：验证字段级（而非仅根级）序列化。 */
    record Holder(Status status, Level level, PlainEnum plain) {
    }

    /** 建一个「只装了 BaseEnumModule」的 Mapper，避免受宿主其它定制干扰。 */
    private static JsonMapper mapperWithModule() {
        return JsonMapper.builder()
            .addModule(new BaseEnumModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    }

    /** 建一个未装本模块的 Mapper，作为「升级前行为」基线。 */
    private static JsonMapper plainMapper() {
        return JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    }

    // ------------------------------------------------------------------ 序列化

    @Test
    @DisplayName("★ 实现 BaseEnum 的枚举序列化为 value，而不是常量名")
    void baseEnumShouldSerializeAsValue() {
        JsonMapper mapper = mapperWithModule();

        assertThat(mapper.writeValueAsString(Status.ENABLED)).isEqualTo("1");
        assertThat(mapper.writeValueAsString(Level.HIGH)).isEqualTo("\"high\"");
    }

    @Test
    @DisplayName("★ 字段级枚举同样按 value 输出（含 record 组件）")
    void baseEnumFieldShouldSerializeAsValue() {
        JsonMapper mapper = mapperWithModule();

        String json = mapper.writeValueAsString(new Holder(Status.DISABLED, Level.LOW, PlainEnum.ALPHA));

        assertThat(json).isEqualTo("{\"status\":0,\"level\":\"low\",\"plain\":\"ALPHA\"}");
    }

    @Test
    @DisplayName("★ 集合中的 BaseEnum 元素也按 value 输出")
    void baseEnumInCollectionShouldSerializeAsValue() {
        JsonMapper mapper = mapperWithModule();

        assertThat(mapper.writeValueAsString(List.of(Status.ENABLED, Status.DISABLED)))
            .isEqualTo("[1,0]");
    }

    @Test
    @DisplayName("★ 回归：未实现 BaseEnum 的枚举仍按 name 输出，与未装模块时完全一致")
    void nonBaseEnumShouldKeepJacksonDefaultBehaviour() {
        JsonMapper withModule = mapperWithModule();
        JsonMapper baseline = plainMapper();

        assertThat(withModule.writeValueAsString(PlainEnum.BETA))
            .isEqualTo(baseline.writeValueAsString(PlainEnum.BETA))
            .isEqualTo("\"BETA\"");
    }

    @Test
    @DisplayName("★ 回归：显式 @JsonValue 的既有枚举不受影响（业务意图优先）")
    void jsonValueAnnotatedEnumShouldNotBeOverridden() {
        JsonMapper withModule = mapperWithModule();
        JsonMapper baseline = plainMapper();

        assertThat(withModule.writeValueAsString(LegacyJsonValueEnum.TWO))
            .isEqualTo(baseline.writeValueAsString(LegacyJsonValueEnum.TWO))
            .isEqualTo("2");
    }

    @Test
    @DisplayName("★ 回归：@JsonFormat(shape=OBJECT) 的枚举仍按对象输出（不被 value 覆盖）")
    void objectShapeEnumShouldSerializeAsObject() {
        JsonMapper mapper = mapperWithModule();

        String json = mapper.writeValueAsString(ObjectShapeEnum.A);

        assertThat(json).contains("\"value\":1").contains("\"description\":\"甲\"");
        // 若被本模块接管，输出会退化成裸值 1（不含对象括号）
        assertThat(json).startsWith("{").endsWith("}");
    }

    // ------------------------------------------------------------------ 反序列化

    @Test
    @DisplayName("★ 数字 value 反序列化：JSON 数字与数字字符串都接受")
    void numericValueShouldDeserializeFromNumberAndString() {
        JsonMapper mapper = mapperWithModule();

        assertThat(mapper.readValue("1", Status.class)).isEqualTo(Status.ENABLED);
        assertThat(mapper.readValue("\"1\"", Status.class)).isEqualTo(Status.ENABLED);
        assertThat(mapper.readValue("0", Status.class)).isEqualTo(Status.DISABLED);
    }

    @Test
    @DisplayName("★ 字符串 value 反序列化（大小写敏感，按 value 精确匹配）")
    void stringValueShouldDeserialize() {
        JsonMapper mapper = mapperWithModule();

        assertThat(mapper.readValue("\"high\"", Level.class)).isEqualTo(Level.HIGH);
    }

    @Test
    @DisplayName("★ enum→JSON→enum 往返一致")
    void baseEnumShouldRoundTrip() {
        JsonMapper mapper = mapperWithModule();

        for (Status status : Status.values()) {
            String json = mapper.writeValueAsString(status);
            assertThat(mapper.readValue(json, Status.class)).as("状态 %s 往返", status).isEqualTo(status);
        }
        for (Level level : Level.values()) {
            String json = mapper.writeValueAsString(level);
            assertThat(mapper.readValue(json, Level.class)).as("等级 %s 往返", level).isEqualTo(level);
        }
    }

    @Test
    @DisplayName("★ 未知 value 显式失败，不静默返回 null 或第一个常量")
    void unknownValueShouldFail() {
        JsonMapper mapper = mapperWithModule();

        assertThatThrownBy(() -> mapper.readValue("99", Status.class))
            .isInstanceOf(Exception.class)
            .hasMessageContaining("99");
        assertThatThrownBy(() -> mapper.readValue("\"nope\"", Level.class))
            .isInstanceOf(Exception.class)
            .hasMessageContaining("nope");
    }

    @Test
    @DisplayName("未知 value 的报错信息列出全部可选值，便于前端/联调定位")
    void unknownValueErrorShouldListCandidates() {
        JsonMapper mapper = mapperWithModule();

        assertThatThrownBy(() -> mapper.readValue("99", Status.class))
            .hasMessageContaining("0")
            .hasMessageContaining("1");
    }

    @Test
    @DisplayName("对象/数组等非标量输入显式失败")
    void nonScalarInputShouldFail() {
        JsonMapper mapper = mapperWithModule();

        assertThatThrownBy(() -> mapper.readValue("{\"x\":1}", Status.class))
            .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("显式 null 反序列化为 null（引用类型语义）")
    void explicitNullShouldDeserializeToNull() {
        JsonMapper mapper = mapperWithModule();

        assertThat(mapper.readValue("null", Status.class)).isNull();
    }

    @Test
    @DisplayName("★ 回归：未实现 BaseEnum 的枚举仍按 name 反序列化")
    void nonBaseEnumShouldKeepNameBasedDeserialization() {
        JsonMapper mapper = mapperWithModule();

        assertThat(mapper.readValue("\"BETA\"", PlainEnum.class)).isEqualTo(PlainEnum.BETA);
    }

    @Test
    @DisplayName("★ 回归：@JsonValue 枚举仍按自身 code 反序列化")
    void jsonValueAnnotatedEnumShouldKeepOwnDeserialization() {
        JsonMapper mapper = mapperWithModule();

        assertThat(mapper.readValue("2", LegacyJsonValueEnum.class)).isEqualTo(LegacyJsonValueEnum.TWO);
    }

    // ------------------------------------------------------------------ 索引与边界

    @Test
    @DisplayName("重复 value 的枚举在首次使用时显式失败（不让反序列化结果不确定）")
    void duplicateValueShouldFailFast() {
        assertThatThrownBy(() -> BaseEnumIndex.of(DuplicateValue.class))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("重复的 value");
    }

    /** value 冲突的非法枚举（仅用于验证索引拒绝重复值）。 */
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
    @DisplayName("模块可被普通 Mapper 复用，不污染其它模块注册（简单验证可重复 addModule）")
    void moduleShouldBeIdempotentWhenAddedTwice() {
        BaseEnumModule module = new BaseEnumModule();
        JsonMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .addModules(module, new BaseEnumModule())
            .build();

        assertThat(mapper.writeValueAsString(Status.ENABLED)).isEqualTo("1");
    }

    @Test
    @DisplayName("模块与其它 SimpleModule 共存时互不干扰")
    void moduleShouldCoexistWithOtherModules() {
        SimpleModule other = new SimpleModule("test-other");
        JsonMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .addModule(other)
            .addModule(new BaseEnumModule())
            .build();

        assertThat(mapper.writeValueAsString(Status.DISABLED)).isEqualTo("0");
        assertThat(mapper.readValue("1", Status.class)).isEqualTo(Status.ENABLED);
    }

    @Test
    @DisplayName("Map 的 value 为 BaseEnum 时按 value 输出")
    void baseEnumAsMapValueShouldSerializeAsValue() {
        JsonMapper mapper = mapperWithModule();

        assertThat(mapper.writeValueAsString(Map.of("status", Status.ENABLED)))
            .isEqualTo("{\"status\":1}");
    }
}

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
package cn.ypbin.starter.data.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.ypbin.starter.core.enums.BaseEnum;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * {@link BaseEnumTypeHandler} 与注册器单元测试（不依赖真实数据库）。
 *
 * <p><b>用 Mockito 而非内存库的理由</b>：本类的语义核心是「写什么、读什么、认不出时怎么办」，
 * 这三件事都能在 JDBC 接口层面精确断言（{@code setInt(1, 1)} / {@code rs.getString -> "1"}）。
 * 用内存库会把「MyBatis 是否真的调用了这个 handler」与「handler 自身逻辑」两件事混在一条用例里，
 * 失败时难以判断责任方。真实库的端到端往返另见 {@code BaseEnumTypeHandlerH2Test}。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
class BaseEnumTypeHandlerTest {

    /** 整型 value 枚举。 */
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

    /** 字符串 value 枚举。 */
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

    /** 未实现 BaseEnum 的枚举：注册器必须跳过。 */
    enum PlainEnum {
        ALPHA
    }

    // ------------------------------------------------------------------ 写入

    @Test
    @DisplayName("★ 整型 value 写库用 setInt，而不是存常量名")
    void integerValueShouldBeWrittenAsInt() throws SQLException {
        PreparedStatement ps = Mockito.mock(PreparedStatement.class);
        BaseEnumTypeHandler<Status> handler = new BaseEnumTypeHandler<>(Status.class);

        handler.setNonNullParameter(ps, 2, Status.ENABLED, JdbcType.INTEGER);

        Mockito.verify(ps).setInt(2, 1);
    }

    @Test
    @DisplayName("★ 字符串 value 写库用 setString")
    void stringValueShouldBeWrittenAsString() throws SQLException {
        PreparedStatement ps = Mockito.mock(PreparedStatement.class);
        BaseEnumTypeHandler<Level> handler = new BaseEnumTypeHandler<>(Level.class);

        handler.setNonNullParameter(ps, 1, Level.HIGH, JdbcType.VARCHAR);

        Mockito.verify(ps).setString(1, "high");
    }

    // ------------------------------------------------------------------ 读取

    @Test
    @DisplayName("★ 读库按 value 回填枚举（整型列以字符串返回也能识别）")
    void integerValueShouldBeReadBackByValue() throws SQLException {
        ResultSet rs = Mockito.mock(ResultSet.class);
        Mockito.when(rs.getString("status")).thenReturn("1");
        Mockito.when(rs.getString(3)).thenReturn("0");
        BaseEnumTypeHandler<Status> handler = new BaseEnumTypeHandler<>(Status.class);

        assertThat(handler.getNullableResult(rs, "status")).isEqualTo(Status.ENABLED);
        assertThat(handler.getNullableResult(rs, 3)).isEqualTo(Status.DISABLED);
    }

    @Test
    @DisplayName("★ 字符串 value 读库回填")
    void stringValueShouldBeReadBackByValue() throws SQLException {
        ResultSet rs = Mockito.mock(ResultSet.class);
        Mockito.when(rs.getString("level")).thenReturn("high");
        BaseEnumTypeHandler<Level> handler = new BaseEnumTypeHandler<>(Level.class);

        assertThat(handler.getNullableResult(rs, "level")).isEqualTo(Level.HIGH);
    }

    @Test
    @DisplayName("★ CallableStatement 结果同样按 value 回填")
    void callableStatementShouldBeReadBackByValue() throws SQLException {
        CallableStatement cs = Mockito.mock(CallableStatement.class);
        Mockito.when(cs.getString(1)).thenReturn("0");
        BaseEnumTypeHandler<Status> handler = new BaseEnumTypeHandler<>(Status.class);

        assertThat(handler.getNullableResult(cs, 1)).isEqualTo(Status.DISABLED);
    }

    @Test
    @DisplayName("列值为 null 时返回 null（不是第一个常量）")
    void nullColumnShouldYieldNull() throws SQLException {
        ResultSet rs = Mockito.mock(ResultSet.class);
        Mockito.when(rs.getString("status")).thenReturn(null);
        BaseEnumTypeHandler<Status> handler = new BaseEnumTypeHandler<>(Status.class);

        assertThat(handler.getNullableResult(rs, "status")).isNull();
    }

    @Test
    @DisplayName("★ 库中 value 与代码枚举定义不一致时显式失败，不静默返回 null")
    void unknownDatabaseValueShouldFail() throws SQLException {
        ResultSet rs = Mockito.mock(ResultSet.class);
        Mockito.when(rs.getString("status")).thenReturn("9");
        BaseEnumTypeHandler<Status> handler = new BaseEnumTypeHandler<>(Status.class);

        assertThatThrownBy(() -> handler.getNullableResult(rs, "status"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("9")
            .hasMessageContaining("0")
            .hasMessageContaining("1");
    }

    // ------------------------------------------------------------------ 索引

    @Test
    @DisplayName("重复 value 的枚举在构造时显式失败")
    void duplicateValueShouldFailFast() {
        assertThatThrownBy(() -> new BaseEnumTypeHandler<>(DuplicateValue.class))
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
    @DisplayName("构造器拒绝 null 类型")
    void nullTypeShouldBeRejected() {
        assertThatThrownBy(() -> new BaseEnumTypeHandler<>(null))
            .isInstanceOf(NullPointerException.class);
    }

    // ------------------------------------------------------------------ 注册器

    @Test
    @DisplayName("★ 注册器只为 BaseEnum 枚举注册，普通枚举与非枚举类型被跳过")
    void registrarShouldOnlyRegisterBaseEnums() {
        Configuration configuration = new Configuration();
        TypeHandlerRegistry registry = configuration.getTypeHandlerRegistry();
        // MyBatis 的默认枚举处理器为 EnumTypeHandler
        assertThat(registry.getTypeHandler(PlainEnum.class))
            .isNotInstanceOf(BaseEnumTypeHandler.class);

        List<Class<?>> registered = BaseEnumTypeHandlerRegistrar.register(
            configuration, List.of(Status.class, Level.class, PlainEnum.class, String.class));

        assertThat(registered).containsExactly(Status.class, Level.class);
        assertThat(BaseEnumTypeHandlerRegistrar.isRegistered(configuration, Status.class)).isTrue();
        assertThat(BaseEnumTypeHandlerRegistrar.isRegistered(configuration, Level.class)).isTrue();
        assertThat(BaseEnumTypeHandlerRegistrar.isRegistered(configuration, PlainEnum.class)).isFalse();
    }

    @Test
    @DisplayName("★ 注册后普通枚举仍走 MyBatis 默认枚举处理（行为不变）")
    void nonBaseEnumShouldKeepDefaultTypeHandler() {
        Configuration configuration = new Configuration();
        BaseEnumTypeHandlerRegistrar.register(configuration, List.of(Status.class));

        Object plainHandler = configuration.getTypeHandlerRegistry().getTypeHandler(PlainEnum.class);

        assertThat(plainHandler)
            .isNotNull()
            .isNotInstanceOf(BaseEnumTypeHandler.class);
    }

    @Test
    @DisplayName("注册器对 null 元素与空集合安全（不抛异常、返回空列表）")
    void registrarShouldTolerateEmptyAndNullElements() {
        Configuration configuration = new Configuration();

        assertThat(BaseEnumTypeHandlerRegistrar.register(configuration, List.of())).isEmpty();
        assertThat(BaseEnumTypeHandlerRegistrar.register(
            configuration, Arrays.asList(Status.class, null))).containsExactly(Status.class);
    }

    @Test
    @DisplayName("注册器可重复调用（幂等）")
    void registrarShouldBeIdempotent() {
        Configuration configuration = new Configuration();

        BaseEnumTypeHandlerRegistrar.register(configuration, List.of(Status.class));
        BaseEnumTypeHandlerRegistrar.register(configuration, List.of(Status.class));

        assertThat(BaseEnumTypeHandlerRegistrar.isRegistered(configuration, Status.class)).isTrue();
    }

    @Test
    @DisplayName("写入时按 value 的运行时类型选择 setter（Long 走 setLong）")
    void longValueShouldUseSetLong() throws SQLException {
        PreparedStatement ps = Mockito.mock(PreparedStatement.class);
        BaseEnumTypeHandler<LongValue> handler = new BaseEnumTypeHandler<>(LongValue.class);

        handler.setNonNullParameter(ps, 1, LongValue.BIG, JdbcType.BIGINT);

        Mockito.verify(ps).setLong(1, 9007199254740993L);
    }

    /** Long value 枚举。 */
    enum LongValue implements BaseEnum<Long> {
        BIG(9007199254740993L, "大数");

        private final Long value;
        private final String description;

        LongValue(Long value, String description) {
            this.value = value;
            this.description = description;
        }

        @Override
        public Long getValue() {
            return value;
        }

        @Override
        public String getDescription() {
            return description;
        }
    }

    @Test
    @DisplayName("写入时 Boolean value 走 setBoolean")
    void booleanValueShouldUseSetBoolean() throws SQLException {
        PreparedStatement ps = Mockito.mock(PreparedStatement.class);
        BaseEnumTypeHandler<Flag> handler = new BaseEnumTypeHandler<>(Flag.class);

        handler.setNonNullParameter(ps, 1, Flag.YES, JdbcType.BOOLEAN);

        Mockito.verify(ps).setBoolean(1, true);
    }

    /** Boolean value 枚举。 */
    enum Flag implements BaseEnum<Boolean> {
        NO(false, "否"),
        YES(true, "是");

        private final Boolean value;
        private final String description;

        Flag(Boolean value, String description) {
            this.value = value;
            this.description = description;
        }

        @Override
        public Boolean getValue() {
            return value;
        }

        @Override
        public String getDescription() {
            return description;
        }
    }

    @Test
    @DisplayName("Boolean value 读库回填（库中存 true/false 文本）")
    void booleanValueShouldBeReadBack() throws SQLException {
        ResultSet rs = Mockito.mock(ResultSet.class);
        Mockito.when(rs.getString("flag")).thenReturn("true");
        BaseEnumTypeHandler<Flag> handler = new BaseEnumTypeHandler<>(Flag.class);

        assertThat(handler.getNullableResult(rs, "flag")).isEqualTo(Flag.YES);
    }

    @Test
    @DisplayName("其它类型 value 退化为 setObject（不假设 value 一定是整型）")
    void otherValueTypeShouldFallBackToSetObject() throws SQLException {
        PreparedStatement ps = Mockito.mock(PreparedStatement.class);
        BaseEnumTypeHandler<CustomValue> handler = new BaseEnumTypeHandler<>(CustomValue.class);

        handler.setNonNullParameter(ps, 1, CustomValue.A, JdbcType.VARCHAR);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        Mockito.verify(ps).setObject(Mockito.eq(1), captor.capture());
        assertThat(captor.getValue()).isEqualTo('A');
    }

    /** 非标准 value 类型枚举（Character），验证 setObject 兜底。 */
    enum CustomValue implements BaseEnum<Character> {
        A('A', "甲");

        private final Character value;
        private final String description;

        CustomValue(Character value, String description) {
            this.value = value;
            this.description = description;
        }

        @Override
        public Character getValue() {
            return value;
        }

        @Override
        public String getDescription() {
            return description;
        }
    }
}

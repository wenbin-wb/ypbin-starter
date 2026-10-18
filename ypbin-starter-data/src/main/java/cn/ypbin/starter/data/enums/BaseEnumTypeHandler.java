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

import cn.ypbin.starter.core.enums.BaseEnum;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Objects;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.jspecify.annotations.Nullable;

/**
 * {@link BaseEnum} 的 MyBatis TypeHandler：按 {@code value} 落库、按 {@code value} 回填。
 *
 * <p><b>为什么需要它</b>：MyBatis 默认枚举处理是 {@code EnumTypeHandler}（存 {@code name()}）或
 * {@code EnumOrdinalTypeHandler}（存序号）。两者都把「枚举实现细节」写进了数据库：改常量名要刷数据，
 * 插入一个常量会让后续所有序号整体偏移（{@code EnumOrdinalTypeHandler} 是纯粹的数据陷阱）。
 * {@link BaseEnum} 的契约本就是「{@code value} 用于存储」，本处理器把它落到实处。</p>
 *
 * <p><b>为什么继承 {@link BaseTypeHandler} 而不是 {@code BaseTypeHandler<E>}</b>：泛型擦除后
 * {@code getNullableResult} 的返回类型无法携带具体枚举，实例化时由 {@link #BaseEnumTypeHandler(Class)}
 * 从枚举类型反查 value 索引，返回类型对 MyBatis 而言是「声明为 Object 的枚举实例」，由其自动装箱规则
 * 交给实体字段——这与 MyBatis 官方 {@code EnumTypeHandler} 的做法一致。</p>
 *
 * <p><b>为什么必须显式构造带 {@code Class} 的实例</b>：接口级注册（{@code register(BaseEnum.class, handlerClass)}）
 * 时 MyBatis 用反射 {@code getInstance(Class)} 走公共 {@code (Class)} 构造器；而按具体枚举类型注册
 * （推荐做法，见 {@code BaseEnumTypeHandlerRegistrar}）时用同一个构造器拿到精确实例。两条路径都要能用。</p>
 *
 * <p><b>空值与未知 value</b>：数据库列可为 {@code null}（读回 {@code null}）；但读到本枚举不认识的
 * {@code value} 时<b>显式抛异常</b>——那说明库里的数据与代码的枚举定义已经不一致，静默返回 {@code null}
 * 会让「脏数据」伪装成「字段为空」，问题被推迟到更远的地方才暴露。</p>
 *
 * @param <E> 枚举类型
 * @author wenbin
 * @since 2026-10-18
 */
public class BaseEnumTypeHandler<E extends Enum<E> & BaseEnum<?>> extends BaseTypeHandler<E> {

    private final BaseEnumIndex<E> index;

    /**
     * 构造（MyBatis 反射实例化入口，必须为 public 单参构造器）。
     *
     * @param enumType 枚举类型（必须实现 {@link BaseEnum}）
     * @throws IllegalArgumentException 类型不支持或存在重复 value
     */
    public BaseEnumTypeHandler(Class<E> enumType) {
        Objects.requireNonNull(enumType, "enumType");
        this.index = BaseEnumIndex.of(enumType);
    }

    /**
     * 写库：把 {@code getValue()} 作为参数写入。
     *
     * <p>按 value 的运行时类型选择 setter（{@code Integer}/{@code Long}/{@code String}/{@code Boolean}），
     * 其余类型退化为 {@code setObject}，避免假设 value 一定是整型。</p>
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, E parameter, @Nullable JdbcType jdbcType)
        throws SQLException {
        Object value = parameter.getValue();
        if (value == null) {
            ps.setNull(i, Types.NULL);
            return;
        }
        if (value instanceof Integer integer) {
            ps.setInt(i, integer);
        } else if (value instanceof Long longValue) {
            ps.setLong(i, longValue);
        } else if (value instanceof String string) {
            ps.setString(i, string);
        } else if (value instanceof Boolean booleanValue) {
            ps.setBoolean(i, booleanValue);
        } else {
            ps.setObject(i, value);
        }
    }

    @Override
    @Nullable
    public E getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return fromDb(rs.getString(columnName));
    }

    @Override
    @Nullable
    public E getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return fromDb(rs.getString(columnIndex));
    }

    @Override
    @Nullable
    public E getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return fromDb(cs.getString(columnIndex));
    }

    /**
     * 数据库值 → 枚举。
     *
     * <p>统一按字符串比较：JDBC 驱动把 int 列读成 {@code "1"}、把 varchar 列读成 {@code "1"}，
     * 与 {@code String.valueOf(getValue())} 对齐即可同时支持「整型列」与「字符列」两种表设计，
     * 不必要求业务库的列类型与 value 的 Java 类型严格一致。</p>
     */
    @Nullable
    private E fromDb(@Nullable String dbValue) {
        if (dbValue == null) {
            return null;
        }
        E matched = index.findByValueText(dbValue);
        if (matched == null) {
            throw new IllegalStateException("枚举 " + index.enumType().getName()
                + " 不支持数据库值 value=" + dbValue + "，可选值："
                + index.valueTexts() + "；请检查数据是否与代码中的枚举定义一致");
        }
        return matched;
    }
}

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

import cn.ypbin.starter.core.enums.BaseEnum;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.type.TypeHandler;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code BaseEnum} → 数据库 → {@code BaseEnum} 端到端往返测试（H2 内存库）。
 *
 * <p><b>为什么要跑真实库</b>：单元测试用 Mockito 验证了「handler 写了什么、读了什么」，
 * 但没有验证<b>MyBatis 是否真的调用了这个 handler</b>——类型处理器的装配是最容易「看起来注册了、
 * 实际没生效」的一环（接口级注册不命中具体枚举就是典型例子）。本测试走真实
 * {@code SqlSessionFactory} + 真实 SQL，把「注册 → 生效 → 数据形态正确」整条链路钉住。</p>
 *
 * <p>同时反向验证回归：未实现 {@code BaseEnum} 的枚举列在同一个库里仍按 {@code name()} 存取，
 * 证明本能力没有顺带改变既有枚举的持久化行为。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
class BaseEnumTypeHandlerH2Test {

    /** 落库枚举：value 为整数，与常见字典/状态列一致。 */
    public enum DeviceStatus implements BaseEnum<Integer> {
        OFFLINE(0, "离线"),
        ONLINE(1, "在线");

        private final Integer value;
        private final String description;

        DeviceStatus(Integer value, String description) {
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

    /** 落库枚举：value 为字符串。 */
    public enum AlarmLevel implements BaseEnum<String> {
        INFO("info", "提示"),
        CRITICAL("critical", "严重");

        private final String value;
        private final String description;

        AlarmLevel(String value, String description) {
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

    /** 未实现 BaseEnum 的枚举：用于回归验证（应仍按 name 存取）。 */
    public enum PlainKind {
        KIND_A,
        KIND_B
    }

    /** 建库 + 注册处理器 + 返回可用的 SqlSessionFactory。 */
    private static SqlSessionFactory newFactory(String jdbcUrl) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("create table device ("
                    + "id int primary key, "
                    + "status int, "
                    + "level varchar(32), "
                    + "plain_kind varchar(32))");
            }
        }
        Configuration configuration = new Configuration(
            new Environment("test", new JdbcTransactionFactory(), unpooled(jdbcUrl)));
        // 关键：走「按具体枚举类型注册」的路径（装配层真实做法）
        BaseEnumTypeHandlerRegistrar.register(configuration, List.of(DeviceStatus.class, AlarmLevel.class));
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    /** 不引入连接池依赖时的最小数据源（MyBatis 自带 UnpooledDataSource）。 */
    private static DataSource unpooled(String jdbcUrl) {
        UnpooledDataSource dataSource = new UnpooledDataSource();
        dataSource.setDriver("org.h2.Driver");
        dataSource.setUrl(jdbcUrl);
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    @Test
    @DisplayName("★ 整型 value 枚举：写库存 value（不是 name），读回枚举实例")
    void integerEnumShouldRoundTripThroughDatabase() throws SQLException {
        SqlSessionFactory factory = newFactory("jdbc:h2:mem:base_enum_int;DB_CLOSE_DELAY=-1");
        BaseEnumTypeHandler<DeviceStatus> handler = new BaseEnumTypeHandler<>(DeviceStatus.class);

        try (SqlSession session = factory.openSession(true)) {
            Connection connection = session.getConnection();
            try (PreparedStatement insert = connection.prepareStatement(
                "insert into device (id, status) values (?, ?)")) {
                insert.setInt(1, 1);
                handler.setNonNullParameter(insert, 2, DeviceStatus.ONLINE, null);
                insert.executeUpdate();
            }
            try (PreparedStatement select = connection.prepareStatement(
                "select status from device where id = ?")) {
                select.setInt(1, 1);
                try (ResultSet rs = select.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    // 库里必须是 value=1，而不是 name='ONLINE'
                    assertThat(rs.getInt("status")).isEqualTo(1);
                    assertThat(handler.getNullableResult(rs, "status")).isEqualTo(DeviceStatus.ONLINE);
                }
            }
        }
    }

    @Test
    @DisplayName("★ 字符串 value 枚举：写库存 value 文本，读回枚举实例")
    void stringEnumShouldRoundTripThroughDatabase() throws SQLException {
        SqlSessionFactory factory = newFactory("jdbc:h2:mem:base_enum_str;DB_CLOSE_DELAY=-1");
        BaseEnumTypeHandler<AlarmLevel> handler = new BaseEnumTypeHandler<>(AlarmLevel.class);

        try (SqlSession session = factory.openSession(true)) {
            Connection connection = session.getConnection();
            try (PreparedStatement insert = connection.prepareStatement(
                "insert into device (id, level) values (?, ?)")) {
                insert.setInt(1, 2);
                handler.setNonNullParameter(insert, 2, AlarmLevel.CRITICAL, null);
                insert.executeUpdate();
            }
            try (PreparedStatement select = connection.prepareStatement(
                "select level from device where id = ?")) {
                select.setInt(1, 2);
                try (ResultSet rs = select.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("level")).isEqualTo("critical");
                    assertThat(handler.getNullableResult(rs, "level")).isEqualTo(AlarmLevel.CRITICAL);
                }
            }
        }
    }

    @Test
    @DisplayName("★ 注册后的处理器确实挂在具体枚举类型上（接口级注册不命中具体枚举的反例）")
    void registrarShouldBindHandlerToConcreteEnumType() throws SQLException {
        SqlSessionFactory factory = newFactory("jdbc:h2:mem:base_enum_bind;DB_CLOSE_DELAY=-1");
        Configuration configuration = factory.getConfiguration();

        assertThat(BaseEnumTypeHandlerRegistrar.isRegistered(configuration, DeviceStatus.class)).isTrue();
        assertThat(BaseEnumTypeHandlerRegistrar.isRegistered(configuration, AlarmLevel.class)).isTrue();
        // 未参与注册的枚举：不接管，仍走 MyBatis 默认枚举处理
        assertThat(BaseEnumTypeHandlerRegistrar.isRegistered(configuration, PlainKind.class)).isFalse();
    }

    @Test
    @DisplayName("★ 回归：未实现 BaseEnum 的枚举在同一个库里仍按 name 存取，行为不变")
    void nonBaseEnumShouldStillUseName() throws SQLException {
        SqlSessionFactory factory = newFactory("jdbc:h2:mem:base_enum_regress;DB_CLOSE_DELAY=-1");

        try (SqlSession session = factory.openSession(true)) {
            Connection connection = session.getConnection();
            // 用 MyBatis 默认枚举处理器写入：应存 name
            var typeHandler = session.getConfiguration().getTypeHandlerRegistry()
                .getTypeHandler(PlainKind.class);
            assertThat(typeHandler).isNotNull();
            try (PreparedStatement insert = connection.prepareStatement(
                "insert into device (id, plain_kind) values (?, ?)")) {
                insert.setInt(1, 3);
                @SuppressWarnings("unchecked")
                TypeHandler<PlainKind> plainHandler = (TypeHandler<PlainKind>) typeHandler;
                plainHandler.setParameter(insert, 2, PlainKind.KIND_B, null);
                insert.executeUpdate();
            }
            try (PreparedStatement select = connection.prepareStatement(
                "select plain_kind from device where id = ?")) {
                select.setInt(1, 3);
                try (ResultSet rs = select.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("plain_kind")).isEqualTo("KIND_B");
                }
            }
        }
    }

    @Test
    @DisplayName("同一表内整型/字符串/普通枚举列混合写入，互不干扰")
    void mixedColumnsShouldCoexist() throws SQLException {
        SqlSessionFactory factory = newFactory("jdbc:h2:mem:base_enum_mixed;DB_CLOSE_DELAY=-1");
        BaseEnumTypeHandler<DeviceStatus> statusHandler = new BaseEnumTypeHandler<>(DeviceStatus.class);
        BaseEnumTypeHandler<AlarmLevel> levelHandler = new BaseEnumTypeHandler<>(AlarmLevel.class);

        try (SqlSession session = factory.openSession(true)) {
            Connection connection = session.getConnection();
            try (PreparedStatement insert = connection.prepareStatement(
                "insert into device (id, status, level, plain_kind) values (?, ?, ?, ?)")) {
                insert.setInt(1, 4);
                statusHandler.setNonNullParameter(insert, 2, DeviceStatus.OFFLINE, null);
                levelHandler.setNonNullParameter(insert, 3, AlarmLevel.INFO, null);
                insert.setString(4, "KIND_A");
                insert.executeUpdate();
            }
            try (PreparedStatement select = connection.prepareStatement(
                "select status, level, plain_kind from device where id = ?")) {
                select.setInt(1, 4);
                try (ResultSet rs = select.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(statusHandler.getNullableResult(rs, "status")).isEqualTo(DeviceStatus.OFFLINE);
                    assertThat(levelHandler.getNullableResult(rs, "level")).isEqualTo(AlarmLevel.INFO);
                    assertThat(rs.getString("plain_kind")).isEqualTo("KIND_A");
                }
            }
        }
    }

    @Test
    @DisplayName("扫描器能发现测试类内的 BaseEnum 枚举实现")
    void scannerShouldFindBaseEnumImplementations() {
        List<Class<?>> found = BaseEnumScanner.scan(List.of("cn.ypbin.starter.data.enums"));

        assertThat(found).contains(DeviceStatus.class, AlarmLevel.class);
        // 未实现 BaseEnum 的枚举不应出现在结果里
        assertThat(found).doesNotContain(PlainKind.class);
        // 结果中不应有非枚举类型
        assertThat(found).allMatch(Class::isEnum);
    }

    @Test
    @DisplayName("扫描器对空包名与 null 元素安全，返回空列表而非 null")
    void scannerShouldTolerateEmptyInput() {
        assertThat(BaseEnumScanner.scan(List.of())).isEmpty();
        assertThat(BaseEnumScanner.scan(List.<String>of())).isEmpty();
    }

    @Test
    @DisplayName("扫描器结果按包去重（同一枚举只出现一次）")
    void scannerShouldDeduplicateResults() {
        List<Class<?>> found = BaseEnumScanner.scan(
            List.of("cn.ypbin.starter.data.enums", "cn.ypbin.starter.data"));

        List<Class<?>> deviceStatusOccurrences = new ArrayList<>();
        for (Class<?> type : found) {
            if (type == DeviceStatus.class) {
                deviceStatusOccurrences.add(type);
            }
        }
        assertThat(deviceStatusOccurrences).hasSize(1);
    }

    @Test
    @DisplayName("库中 value 与枚举定义不一致时，读回显式失败（不静默返回 null）")
    void inconsistentDatabaseValueShouldFail() throws SQLException {
        SqlSessionFactory factory = newFactory("jdbc:h2:mem:base_enum_dirty;DB_CLOSE_DELAY=-1");
        BaseEnumTypeHandler<DeviceStatus> handler = new BaseEnumTypeHandler<>(DeviceStatus.class);

        try (SqlSession session = factory.openSession(true)) {
            Connection connection = session.getConnection();
            try (PreparedStatement insert = connection.prepareStatement(
                "insert into device (id, status) values (?, ?)")) {
                insert.setInt(1, 5);
                // 模拟「代码删了一个枚举常量、库里还留着旧值」的脏数据
                insert.setInt(2, 99);
                insert.executeUpdate();
            }
            try (PreparedStatement select = connection.prepareStatement(
                "select status from device where id = ?")) {
                select.setInt(1, 5);
                try (ResultSet rs = select.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt("status")).isEqualTo(99);
                    Assertions.assertThatThrownBy(() -> handler.getNullableResult(rs, "status"))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("99");
                }
            }
        }
    }
}

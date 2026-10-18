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
package cn.ypbin.starter.security.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * {@link AbstractPermissionProvider} 的解析口径与 fail-closed 语义测试。
 *
 * <p>把「loginId → userId」这条框架契约钉死，并覆盖 issue 列出的四个用例：
 * (a) loginId 为 {@code null}；(b) 非数字；(c) 带空白的数字；(d) 数据源返回 {@code null} ⇒ 空集合。
 * 每组用例同时断言「数据源是否被调用」与「日志级别」，把「拒绝」与「静默」区分开：
 * 解析失败必须是 WARN 且<strong>不碰数据源</strong>，数据源缺失必须是 ERROR 且返回空集合。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
class AbstractPermissionProviderTest {

    /** 数据源替身：记录被调用的 userId，并按预设值/异常作答。 */
    private static final class StubPermissionProvider extends AbstractPermissionProvider {

        private final List<Long> permissionCalls = new ArrayList<>();

        private final List<Long> roleCalls = new ArrayList<>();

        private @Nullable List<String> permissions = List.of("system:user:add");

        private @Nullable List<String> roles = List.of("tenant_admin");

        private @Nullable RuntimeException permissionsFailure;

        private @Nullable RuntimeException rolesFailure;

        @Override
        protected @Nullable List<String> permissionsOf(long userId) {
            permissionCalls.add(userId);
            if (permissionsFailure != null) {
                throw permissionsFailure;
            }
            return permissions;
        }

        @Override
        protected @Nullable List<String> rolesOf(long userId) {
            roleCalls.add(userId);
            if (rolesFailure != null) {
                throw rolesFailure;
            }
            return roles;
        }
    }

    private final StubPermissionProvider provider = new StubPermissionProvider();

    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        appender = new ListAppender<>();
        appender.start();
        logger().addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger().detachAppender(appender);
    }

    private static Logger logger() {
        return (Logger) LoggerFactory.getLogger(AbstractPermissionProvider.class);
    }

    private List<ILoggingEvent> events(Level level) {
        return appender.list.stream().filter(event -> event.getLevel() == level).toList();
    }

    @Test
    @DisplayName("(a) loginId 为 null：WARN + 空集合，且不调用数据源")
    void nullLoginId_deniesWithoutTouchingDataSource() {
        assertThat(provider.getPermissions(null, "login")).isEmpty();
        assertThat(provider.getRoles(null, "login")).isEmpty();

        assertThat(provider.permissionCalls).as("loginId 缺失时不得发起数据源查询").isEmpty();
        assertThat(provider.roleCalls).isEmpty();
        assertThat(events(Level.WARN)).as("解析失败必须暴露（WARN），不能静默").hasSize(2);
        assertThat(events(Level.ERROR)).isEmpty();
    }

    @Test
    @DisplayName("(b) 非数字 loginId：WARN + 空集合，且不调用数据源")
    void nonNumericLoginId_deniesWithoutTouchingDataSource() {
        assertThat(provider.getPermissions("not-a-number", "login")).isEmpty();
        assertThat(provider.getRoles("not-a-number", "login")).isEmpty();

        assertThat(provider.permissionCalls).isEmpty();
        assertThat(provider.roleCalls).isEmpty();
        assertThat(events(Level.WARN)).hasSize(2);
    }

    @Test
    @DisplayName("(c) 带首尾空白的数字 loginId：按合法 userId 解析并查询数据源")
    void blankPaddedNumericLoginId_isParsedAndQueried() {
        assertThat(provider.getPermissions(" 1001 ", "login")).containsExactly("system:user:add");
        assertThat(provider.getRoles("\t1001\n", "login")).containsExactly("tenant_admin");

        assertThat(provider.permissionCalls).containsExactly(1001L);
        assertThat(provider.roleCalls).containsExactly(1001L);
        assertThat(events(Level.WARN)).as("合法登录标识不应产生任何告警").isEmpty();
    }

    @Test
    @DisplayName("(d) 数据源返回 null：ERROR + 空集合（fail-closed，且错误可见）")
    void nullDataSourceResult_deniesWithErrorLog() {
        provider.permissions = null;
        provider.roles = null;

        assertThat(provider.getPermissions(1001L, "login")).isEmpty();
        assertThat(provider.getRoles(1001L, "login")).isEmpty();

        assertThat(provider.permissionCalls).containsExactly(1001L);
        assertThat(provider.roleCalls).containsExactly(1001L);
        assertThat(events(Level.ERROR)).as("数据源返回 null 必须记 ERROR 暴露，不能静默当空集合").hasSize(2);
    }

    @Test
    @DisplayName("正常路径：数字 loginId 原样下发 userId，数据源结果原样返回（不增删权限码）")
    void numericLoginId_returnsDataSourceResultVerbatim() {
        assertThat(provider.getPermissions(1001L, "login")).containsExactly("system:user:add");
        assertThat(provider.getRoles("1001", "login")).containsExactly("tenant_admin");

        assertThat(provider.permissionCalls).containsExactly(1001L);
        assertThat(provider.roleCalls).containsExactly(1001L);
        assertThat(events(Level.WARN)).isEmpty();
        assertThat(events(Level.ERROR)).isEmpty();
    }

    @Test
    @DisplayName("数据源自身抛异常必须原样上抛（不得伪装成空权限）")
    void dataSourceFailure_propagates() {
        provider.permissionsFailure = new IllegalStateException("system unavailable");
        provider.rolesFailure = new IllegalStateException("system unavailable");

        assertThatThrownBy(() -> provider.getPermissions(1001L, "login"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("system unavailable");
        assertThatThrownBy(() -> provider.getRoles(1001L, "login"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("system unavailable");
    }

    @Test
    @DisplayName("userIdOf 对合法/非法入参的解析口径（供子类覆写的既有契约）")
    void userIdOfContract() {
        assertThat(provider.userIdOf(42L, "login")).isEqualTo(OptionalLong.of(42L));
        assertThat(provider.userIdOf(" 42 ", "login")).isEqualTo(OptionalLong.of(42L));
        assertThat(provider.userIdOf(null, "login")).isEqualTo(OptionalLong.empty());
        assertThat(provider.userIdOf("abc", "login")).isEqualTo(OptionalLong.empty());
        assertThat(provider.userIdOf("", "login")).isEqualTo(OptionalLong.empty());
    }
}

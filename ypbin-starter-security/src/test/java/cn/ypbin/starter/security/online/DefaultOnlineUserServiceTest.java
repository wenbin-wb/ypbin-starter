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
package cn.ypbin.starter.security.online;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

/**
 * {@link DefaultOnlineUserService} 读取失败的可见性测试。
 *
 * <p>会话反序列化异常（如 Sa-Token 会话多态白名单缺项）曾表现为「Redis 数据完好、在线用户字段全为空」且
 * 只在 debug 级留痕。本用例断言：① 该条在线记录仍然返回（不因读失败整条丢失）；② 失败以 WARN 记录且带完整
 * 堆栈（可定位），不再静默。</p>
 *
 * @author wenbin
 * @since 2026-09-17
 */
class DefaultOnlineUserServiceTest {

    private static final String TOKEN_KEY = "Authorization:login:token:token-a";

    private static final String TOKEN = "token-a";

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
        return (Logger) LoggerFactory.getLogger(DefaultOnlineUserService.class);
    }

    @Test
    void shouldKeepRecordAndWarnWithStackWhenTerminalReadFails() {
        StpLogic stpLogic = mock(StpLogic.class);
        when(stpLogic.getTokenActiveTimeoutByToken(TOKEN)).thenReturn(SaTokenDao.NEVER_EXPIRE);
        when(stpLogic.getTerminalInfoByToken(TOKEN)).thenThrow(new IllegalStateException("terminal info broken"));

        try (MockedStatic<StpUtil> stpUtil = Mockito.mockStatic(StpUtil.class)) {
            stpUtil.when(() -> StpUtil.searchTokenValue("", 0, -1, false)).thenReturn(List.of(TOKEN_KEY));
            stpUtil.when(StpUtil::getStpLogic).thenReturn(stpLogic);
            stpUtil.when(() -> StpUtil.getLoginIdByToken(TOKEN)).thenReturn(1L);
            stpUtil.when(() -> StpUtil.getSessionByLoginId(1L, false)).thenReturn(null);
            // 模拟 Token-Session 反序列化失败（白名单缺项时的真实表现）
            stpUtil.when(() -> StpUtil.getTokenSessionByToken(TOKEN))
                .thenThrow(new IllegalStateException("not allowed to deserialize OnlineUserHelper$Terminal"));

            List<OnlineUser> users = new DefaultOnlineUserService().list();

            // 记录不丢：仍然返回该在线用户，只是字段为空
            assertThat(users).hasSize(1);
            assertThat(users.get(0).getIp()).isNull();
            assertThat(users.get(0).getBrowser()).isNull();
            assertThat(users.get(0).getLoginTime()).isNull();
        }

        assertThat(warnEventsWithStack())
            .anySatisfy(message -> assertThat(message).contains("在线用户终端信息读取失败"))
            .anySatisfy(message -> assertThat(message).contains("设备类型"))
            .anySatisfy(message -> assertThat(message).contains("创建时间"));
    }

    /** 只取 WARN 且带堆栈的日志 message：证明失败不是「只打 message」的静默降级。 */
    private List<String> warnEventsWithStack() {
        return appender.list.stream()
            .filter(event -> event.getLevel() == Level.WARN)
            .filter(event -> event.getThrowableProxy() != null)
            .map(ILoggingEvent::getFormattedMessage)
            .toList();
    }
}

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.json.SaJsonTemplateForJackson3;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * {@link OnlineUserHelper} 终端信息读写测试。
 *
 * <p>覆盖三类回归：① 旧形态（{@code LocalDateTime} 序列化出的 ISO 字符串）会话数据仍能读出且登录时间不丢；
 * ② 反序列化退化成 {@code Map} 形态时按「能取多少取多少」还原，不丢整条记录；③ 存储形态为与时间类型配置
 * 无关的「epoch 毫秒 + 文本」。</p>
 *
 * @author wenbin
 * @since 2026-09-17
 */
class OnlineUserHelperTest {

    /**
     * 线上实测的旧形态会话值（Sa-Token 1.46 + Jackson 3 会话 JSON，{@code loginTime} 为 LocalDateTime 的
     * ISO 字符串）。该用例同时是 {@code META-INF/satoken/sa-json-type.list} 的防回归门禁：白名单缺少
     * {@code OnlineUserHelper$Terminal} 时，Sa-Token 的类型校验会直接抛异常使本用例转红。
     */
    private static final String LEGACY_SESSION_JSON = """
        {"@class":"cn.dev33.satoken.session.SaSession","createTime":1758090887602,
         "dataMap":{"@class":"java.util.concurrent.ConcurrentHashMap",
           "ypbin:onlineTerminal":{
             "@class":"cn.ypbin.starter.security.online.OnlineUserHelper$Terminal",
             "browser":"Chrome 153.0.8010.12","ip":"183.179.215.235","location":null,
             "loginTime":"2026-09-17T11:54:47.602450842","os":"Linux"}}}""";

    @Test
    void shouldReadTerminalFromLegacySessionPayload() {
        SaSession session = new SaJsonTemplateForJackson3().jsonToObject(LEGACY_SESSION_JSON, SaSession.class);

        assertThat(session).isNotNull();
        OnlineUserHelper.Terminal terminal =
            OnlineUserHelper.toTerminal(session.get(OnlineUserHelper.KEY_TERMINAL));

        assertThat(terminal).isNotNull();
        assertThat(terminal.getIp()).isEqualTo("183.179.215.235");
        assertThat(terminal.getBrowser()).isEqualTo("Chrome 153.0.8010.12");
        assertThat(terminal.getOs()).isEqualTo("Linux");
        assertThat(terminal.resolveLoginTime()).isEqualTo(LocalDateTime.parse("2026-09-17T11:54:47.602450842"));
    }

    /** 真写→真读往返：证明新存储形态（String/Long）经真实 Sa-Token 会话序列化器仍能原样还原。 */
    @Test
    void shouldRoundTripNewStorageShapeThroughSatokenSessionJson() {
        SaJsonTemplateForJackson3 json = new SaJsonTemplateForJackson3();
        OnlineUserHelper.Terminal terminal = new OnlineUserHelper.Terminal();
        terminal.setIp("10.0.0.8");
        terminal.setBrowser("Chrome 120.0.0.0");
        terminal.setOs("Linux");
        terminal.fillLoginTimeIfAbsent();
        SaSession session = new SaSession("token-session:round-trip");
        session.set(OnlineUserHelper.KEY_TERMINAL, terminal);

        String payload = json.objectToJson(session);
        SaSession restored = json.jsonToObject(payload, SaSession.class);
        OnlineUserHelper.Terminal back = OnlineUserHelper.toTerminal(restored.get(OnlineUserHelper.KEY_TERMINAL));

        assertThat(back).isNotNull();
        assertThat(back.getLoginTimeMillis()).isEqualTo(terminal.getLoginTimeMillis());
        assertThat(back.getLoginTimeText()).isEqualTo(terminal.getLoginTimeText());
        assertThat(back.resolveLoginTime()).isEqualTo(terminal.resolveLoginTime());
        // 写出的就是与时间类型配置无关的两种形态（毫秒 + 文本）
        assertThat(payload).contains(OnlineUserHelper.FIELD_LOGIN_TIME_MILLIS)
            .contains(OnlineUserHelper.FIELD_LOGIN_TIME_TEXT);
    }

    @Test
    void shouldExtractFieldsFromDegradedMapShapeWithoutLosingRecord() {        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ip", "10.0.0.8");
        map.put("browser", "Firefox 130.0");
        map.put("os", "Windows 10");
        map.put("location", null);
        map.put(OnlineUserHelper.FIELD_LOGIN_TIME, "2026-09-17 08:00:01");

        OnlineUserHelper.Terminal terminal = OnlineUserHelper.toTerminal(map);

        assertThat(terminal).isNotNull();
        assertThat(terminal.getIp()).isEqualTo("10.0.0.8");
        assertThat(terminal.getBrowser()).isEqualTo("Firefox 130.0");
        assertThat(terminal.getOs()).isEqualTo("Windows 10");
        assertThat(terminal.getLocation()).isNull();
        assertThat(terminal.resolveLoginTime()).isEqualTo(LocalDateTime.of(2026, 9, 17, 8, 0, 1));
    }

    @Test
    void shouldNotThrowForUnrecognizedShape() {
        assertThat(OnlineUserHelper.toTerminal(null)).isNull();
        assertThat(OnlineUserHelper.toTerminal("not-a-terminal")).isNull();
    }

    @Test
    void shouldParseAllKnownLoginTimeShapes() {
        assertThat(OnlineUserHelper.parseLoginTimeText("2026-09-17T11:54:47.602450842"))
            .isEqualTo(LocalDateTime.parse("2026-09-17T11:54:47.602450842"));
        assertThat(OnlineUserHelper.parseLoginTimeText("2026-09-17 11:54:47"))
            .isEqualTo(LocalDateTime.of(2026, 9, 17, 11, 54, 47));
        assertThat(OnlineUserHelper.parseLoginTimeText("2026-09-17 11:54:47.602"))
            .isEqualTo(LocalDateTime.of(2026, 9, 17, 11, 54, 47, 602_000_000));
        assertThat(OnlineUserHelper.parseLoginTimeText("2026-09-17T11:54:47+08:00"))
            .isEqualTo(LocalDateTime.of(2026, 9, 17, 11, 54, 47));
        assertThat(OnlineUserHelper.parseLoginTimeText("1758090887602"))
            .isEqualTo(Instant.ofEpochMilli(1758090887602L).atZone(ZoneId.systemDefault()).toLocalDateTime());
        assertThat(OnlineUserHelper.parseLoginTimeText("  ")).isNull();
        assertThat(OnlineUserHelper.parseLoginTimeText(null)).isNull();
        assertThat(OnlineUserHelper.parseLoginTimeText("not-a-time")).isNull();
    }

    @Test
    void shouldRecordJacksonIndependentLoginTimeShapes() {
        SaSession tokenSession = mock(SaSession.class);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        try (MockedStatic<StpUtil> stpUtil = Mockito.mockStatic(StpUtil.class)) {
            when(StpUtil.getTokenSession()).thenReturn(tokenSession);
            OnlineUserHelper.record("10.0.0.8", "Chrome 120.0.0.0", "Linux");
        }
        verify(tokenSession).set(eq(OnlineUserHelper.KEY_TERMINAL), captor.capture());
        OnlineUserHelper.Terminal terminal = (OnlineUserHelper.Terminal) captor.getValue();

        assertThat(terminal.getIp()).isEqualTo("10.0.0.8");
        assertThat(terminal.getBrowser()).isEqualTo("Chrome 120.0.0.0");
        assertThat(terminal.getOs()).isEqualTo("Linux");
        assertThat(terminal.getLoginTimeMillis()).isNotNull();
        assertThat(terminal.getLoginTimeText()).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
        // 历史形态字段仍写出：未升级的读取方（期望 LocalDateTime 形态）也能读到登录时间
        assertThat(terminal.getLoginTime()).isNotNull();
        assertThat(LocalDateTime.parse(terminal.getLoginTime())).isNotNull();
        assertThat(terminal.resolveLoginTime()).isNotNull();
    }
}

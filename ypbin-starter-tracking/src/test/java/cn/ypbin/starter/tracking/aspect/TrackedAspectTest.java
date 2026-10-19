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
package cn.ypbin.starter.tracking.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.ypbin.starter.tracking.annotation.Tracked;
import cn.ypbin.starter.tracking.core.TrackEvent;
import cn.ypbin.starter.tracking.core.TrackIdentityProvider;
import cn.ypbin.starter.tracking.core.TrackRecorder;
import cn.ypbin.starter.tracking.core.TrackingEventCatalog;
import cn.ypbin.starter.tracking.core.TrackingEventCodes;
import cn.ypbin.starter.tracking.support.BoundedEventQueue;
import cn.ypbin.starter.tracking.support.TrackCounters;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code @Tracked} 切面测试：成功与异常两条路径都必须产出事件，且异常原样透传。
 *
 * @author wenbin
 * @since 2026-09-15
 */
class TrackedAspectTest {

    private BoundedEventQueue queue;

    private TrackCounters counters;

    private TrackedAspect aspect;

    @BeforeEach
    void setUp() {
        queue = new BoundedEventQueue(8);
        counters = new TrackCounters();
        TrackRecorder recorder = new TrackRecorder(queue, counters, new TrackingEventCatalog(new ObjectMapper()));
        aspect = new TrackedAspect(recorder, "ypbin-admin");
    }

    @Test
    void shouldRecordSuccessfulInvocation() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Tracked tracked = mock(Tracked.class);
        when(tracked.value()).thenReturn(TrackingEventCodes.AUTH_USER_LOGIN);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.around(joinPoint, tracked);

        assertThat(result).isEqualTo("ok");
        TrackEvent event = queue.poll(10L);
        assertThat(event).isNotNull();
        assertThat(event.eventCode()).isEqualTo(TrackingEventCodes.AUTH_USER_LOGIN);
        assertThat(event.appId()).isEqualTo("ypbin-admin");
        assertThat(event.success()).isTrue();
        assertThat(event.durationMs()).isNotNull();
        assertThat(event.payload()).isEmpty();
    }

    @Test
    void shouldRecordFailedInvocationAndRethrow() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Tracked tracked = mock(Tracked.class);
        when(tracked.value()).thenReturn(TrackingEventCodes.AUTH_USER_LOGIN);
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("业务失败（测试注入）"));

        assertThatThrownBy(() -> aspect.around(joinPoint, tracked))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("业务失败");

        TrackEvent event = queue.poll(10L);
        assertThat(event).isNotNull();
        assertThat(event.success()).isFalse();
    }

    @Test
    void shouldNotEnqueueUnregisteredCodeButKeepBusinessRunning() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Tracked tracked = mock(Tracked.class);
        when(tracked.value()).thenReturn("auth.user.typo");
        when(joinPoint.proceed()).thenReturn("ok");

        assertThat(aspect.around(joinPoint, tracked)).isEqualTo("ok");

        assertThat(queue.isEmpty()).isTrue();
        assertThat(counters.snapshot().rejectedByReason()).containsEntry("unregistered", 1L);
    }

    /**
     * <b>身份维度</b>：注入 {@code TrackIdentityProvider} 后，{@code @Tracked} 事件必须带上
     * {@code userId}/{@code tenantId}。
     *
     * <p><b>为什么这是关键缺口</b>（2026-10-19 反哺）：切面没有 HTTP 请求上下文，
     * 此前 {@code TrackRequestContext} 传 {@code EMPTY} ⇒ 事件 {@code userId} 恒为空。
     * 后果是「登录事件没有 userId 就无法按人做漏斗，等于白采」，于是每个宿主都手写一遍
     * {@code LoginEventTracker} 绕过注解。本用例钉住修复后的契约。</p>
     */
    @Test
    void shouldCarryUserIdAndTenantIdWhenIdentityProviderPresent() throws Throwable {
        TrackRecorder recorder = new TrackRecorder(queue, counters,
            new TrackingEventCatalog(new ObjectMapper()));
        TrackedAspect withIdentity = new TrackedAspect(recorder, "ypbin-admin",
            new TrackIdentityProvider() {
                @Override
                public Long userId() {
                    return 42L;
                }

                @Override
                public Long tenantId() {
                    return 7L;
                }
            });

        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Tracked tracked = mock(Tracked.class);
        when(tracked.value()).thenReturn(TrackingEventCodes.AUTH_USER_LOGIN);
        when(joinPoint.proceed()).thenReturn("ok");

        withIdentity.around(joinPoint, tracked);

        TrackEvent event = queue.poll(10L);
        assertThat(event).isNotNull();
        assertThat(event.context()).isNotNull();
        assertThat(event.context().userId()).as("userId 必须被采集，否则按人漏斗无法分析").isEqualTo(42L);
        assertThat(event.context().tenantId()).isEqualTo(7L);
    }

    /**
     * 身份提供者抛异常：事件仍然记录，只是缺该维度（埋点是旁路能力，不能因身份故障丢事件）。
     */
    @Test
    void shouldStillRecordEventWhenIdentityProviderFails() throws Throwable {
        TrackRecorder recorder = new TrackRecorder(queue, counters,
            new TrackingEventCatalog(new ObjectMapper()));
        TrackedAspect withBrokenIdentity = new TrackedAspect(recorder, "ypbin-admin",
            new TrackIdentityProvider() {
                @Override
                public Long userId() {
                    throw new IllegalStateException("identity unavailable");
                }

                @Override
                public Long tenantId() {
                    return 7L;
                }
            });

        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Tracked tracked = mock(Tracked.class);
        when(tracked.value()).thenReturn(TrackingEventCodes.AUTH_USER_LOGIN);
        when(joinPoint.proceed()).thenReturn("ok");

        assertThat(withBrokenIdentity.around(joinPoint, tracked)).isEqualTo("ok");

        TrackEvent event = queue.poll(10L);
        assertThat(event).as("身份取值失败不得导致事件丢失").isNotNull();
        assertThat(event.context()).isNotNull();
        assertThat(event.context().userId()).isNull();
        assertThat(event.context().tenantId()).as("另一维度不受影响").isEqualTo(7L);
    }

    /**
     * 未注入身份提供者（旧构造器）：行为与改动前逐字一致（context 各项为 null）。
     */
    @Test
    void shouldKeepLegacyBehaviourWithoutIdentityProvider() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Tracked tracked = mock(Tracked.class);
        when(tracked.value()).thenReturn(TrackingEventCodes.AUTH_USER_LOGIN);
        when(joinPoint.proceed()).thenReturn("ok");

        aspect.around(joinPoint, tracked);

        TrackEvent event = queue.poll(10L);
        assertThat(event).isNotNull();
        assertThat(event.context()).isNotNull();
        assertThat(event.context().userId()).isNull();
        assertThat(event.context().tenantId()).isNull();
    }
}

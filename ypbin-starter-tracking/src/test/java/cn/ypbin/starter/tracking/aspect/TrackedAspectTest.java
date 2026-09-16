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
}

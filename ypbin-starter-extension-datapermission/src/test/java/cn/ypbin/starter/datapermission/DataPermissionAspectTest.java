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
package cn.ypbin.starter.datapermission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.ypbin.starter.datapermission.annotation.DataPermission;
import cn.ypbin.starter.datapermission.aspect.DataPermissionAspect;
import cn.ypbin.starter.datapermission.core.DataPermissionContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;

/**
 * 数据权限切面与上下文测试。
 *
 * @author wenbin
 * @since 2026-08-31
 */
class DataPermissionAspectTest {

    static class DemoService {
        @DataPermission
        public String guarded() {
            return "guarded";
        }

        @DataPermission(ignore = true)
        public String ignored() {
            return "ignored";
        }
    }

    @Test
    void aroundShouldEnterContextForGuardedMethod() throws Throwable {
        DataPermissionAspect aspect = new DataPermissionAspect();
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(point.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(
            DemoService.class.getMethod("guarded"));
        when(point.getTarget()).thenReturn(new DemoService());
        when(point.proceed()).thenReturn("result");

        Object result = aspect.around(point);

        assertThat(result).isEqualTo("result");
        assertThat(DataPermissionContext.isActive()).isFalse();
    }

    @Test
    void aroundShouldSkipWhenIgnore() throws Throwable {
        DataPermissionAspect aspect = new DataPermissionAspect();
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(point.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(
            DemoService.class.getMethod("ignored"));
        when(point.getTarget()).thenReturn(new DemoService());
        when(point.proceed()).thenReturn("ignored-result");

        Object result = aspect.around(point);

        assertThat(result).isEqualTo("ignored-result");
        verify(point).proceed();
    }

    @Test
    void contextShouldTrackScope() {
        assertThat(DataPermissionContext.isActive()).isFalse();
        DataPermissionContext.enter();
        assertThat(DataPermissionContext.isActive()).isTrue();
        DataPermissionContext.exit();
        assertThat(DataPermissionContext.isActive()).isFalse();
    }
}

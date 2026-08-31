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
package cn.ypbin.starter.tenant.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 租户上下文传播器与线程本地访问器测试。
 *
 * @author wenbin
 * @since 2026-08-31
 */
class TenantContextPropagatorTest {

    @Test
    void propagatorShouldCaptureAndRestore() {
        TenantContext.runWithTenant(7L, () -> {
            TenantContextPropagator propagator = new TenantContextPropagator();
            TenantContext.ContextSnapshot snapshot = propagator.capture();
            assertThat(snapshot.tenantId()).isEqualTo(7L);

            TenantContext.runWithTenant(9L, () -> {
                propagator.restore(snapshot);
                assertThat(TenantContext.getTenantId()).contains(7L);
            });
        });
    }

    @Test
    void threadLocalAccessorShouldExposeKeyAndValue() {
        TenantContext.runWithTenant(3L, () -> {
            TenantThreadLocalAccessor accessor = new TenantThreadLocalAccessor();
            assertThat(accessor.key()).isEqualTo(TenantThreadLocalAccessor.KEY);
            assertThat(accessor.getValue()).isNotNull();
        });
    }

    @Test
    void tenantBaseEntityShouldCarryTenantId() {
        TenantEntity entity = new TenantEntity();
        entity.setTenantId(5L);
        assertThat(entity.getTenantId()).isEqualTo(5L);
    }

    static class TenantEntity extends TenantBaseEntity {
    }
}

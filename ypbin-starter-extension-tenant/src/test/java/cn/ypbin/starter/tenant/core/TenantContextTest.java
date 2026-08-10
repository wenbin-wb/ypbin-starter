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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * {@link TenantContext} 显式租户与上下文快照测试。
 *
 * @author wenbin
 * @since 2026-08-09
 */
class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void explicitTenantIsVisibleAndClearedAfterScope() {
        Long tenantId = TenantContext.executeWithTenant(10L,
            () -> TenantContext.getTenantId().orElseThrow());

        assertThat(tenantId).isEqualTo(10L);
        assertThat(TenantContext.getTenantId()).isEmpty();
    }

    @Test
    void nestedTenantRestoresOuterTenant() {
        TenantContext.runWithTenant(10L, () -> {
            assertThat(TenantContext.getTenantId()).contains(10L);
            TenantContext.runWithTenant(20L,
                () -> assertThat(TenantContext.getTenantId()).contains(20L));
            assertThat(TenantContext.getTenantId()).contains(10L);
        });

        assertThat(TenantContext.getTenantId()).isEmpty();
    }

    @Test
    void exceptionRestoresPreviousTenant() {
        TenantContext.runWithTenant(10L, () -> {
            assertThatThrownBy(() -> TenantContext.runWithTenant(20L, () -> {
                throw new IllegalStateException("boom");
            })).isInstanceOf(IllegalStateException.class);
            assertThat(TenantContext.getTenantId()).contains(10L);
        });
    }

    @Test
    void snapshotRestoresTenantAndIgnoreDepth() {
        TenantContext.ContextSnapshot snapshot = TenantContext.executeWithTenant(10L,
            () -> TenantContext.executeIgnore(TenantContext::snapshot));

        TenantContext.restore(snapshot);
        assertThat(TenantContext.getTenantId()).contains(10L);
        assertThat(TenantContext.isIgnored()).isTrue();

        TenantContext.clear();
        assertThat(TenantContext.getTenantId()).isEmpty();
        assertThat(TenantContext.isIgnored()).isFalse();
    }

    @Test
    void nullSnapshotClearsContext() {
        TenantContext.runWithTenant(10L, () -> {
            TenantContext.enterIgnore();
            TenantContext.restore(null);
            assertThat(TenantContext.getTenantId()).isEmpty();
            assertThat(TenantContext.isIgnored()).isFalse();
        });
    }
}

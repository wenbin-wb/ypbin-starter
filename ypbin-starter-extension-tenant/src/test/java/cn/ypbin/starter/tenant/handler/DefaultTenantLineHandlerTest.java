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
package cn.ypbin.starter.tenant.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.tenant.autoconfigure.TenantProperties;
import cn.ypbin.starter.tenant.core.TenantContext;
import java.util.Optional;
import net.sf.jsqlparser.expression.LongValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * {@link DefaultTenantLineHandler} 租户来源优先级测试。
 *
 * @author wenbin
 * @since 2026-08-09
 */
class DefaultTenantLineHandlerTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void explicitTenantTakesPriorityOverProvider() {
        DefaultTenantLineHandler handler = new DefaultTenantLineHandler(
            () -> Optional.of(10L), new TenantProperties());

        TenantContext.runWithTenant(20L, () -> {
            assertThat(handler.getTenantId()).isInstanceOf(LongValue.class);
            assertThat(((LongValue) handler.getTenantId()).getValue()).isEqualTo(20L);
        });
    }

    @Test
    void providerIsUsedWithoutExplicitTenant() {
        DefaultTenantLineHandler handler = new DefaultTenantLineHandler(
            () -> Optional.of(10L), new TenantProperties());

        assertThat(handler.getTenantId()).isInstanceOf(LongValue.class);
        assertThat(((LongValue) handler.getTenantId()).getValue()).isEqualTo(10L);
    }

    @Test
    void missingTenantShouldBeRejectedByDefault() {
        DefaultTenantLineHandler handler = new DefaultTenantLineHandler(
            Optional::empty, new TenantProperties());

        // 默认 fail-closed：缺租户上下文直接拒绝，避免跨全租户查询
        assertThatThrownBy(handler::getTenantId)
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("缺少租户上下文");
    }

    @Test
    void missingTenantShouldReturnJavaNullWhenFailClosedDisabled() {
        TenantProperties properties = new TenantProperties();
        properties.setFailOnMissingTenant(false);
        DefaultTenantLineHandler handler = new DefaultTenantLineHandler(Optional::empty, properties);

        // 仅当显式关闭 fail-closed 时才跳过租户条件；必须是 Java null（返回 NullValue 会拼出 tenant_id = NULL）
        assertThat(handler.getTenantId()).isNull();
    }

    @Test
    void ignoredTableBypassesTenantResolution() {
        DefaultTenantLineHandler handler = new DefaultTenantLineHandler(
            Optional::empty, new TenantProperties());

        TenantContext.runIgnore(() -> assertThat(handler.ignoreTable("sys_user")).isTrue());
    }
}

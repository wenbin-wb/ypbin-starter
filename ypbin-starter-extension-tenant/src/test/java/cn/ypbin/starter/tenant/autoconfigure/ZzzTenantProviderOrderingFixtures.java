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
package cn.ypbin.starter.tenant.autoconfigure;

import cn.ypbin.starter.tenant.core.TenantProvider;
import java.util.Optional;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * {@code @AutoConfigureBefore} 有效性对照夹具（仅测试用，不在 AutoConfiguration.imports 中）。
 *
 * <p><strong>为何需要它</strong>：生产实现 {@link IdentityTenantAutoConfiguration} 的类名以 {@code I} 开头，
 * 字典序本就在 {@link TenantAutoConfiguration}（{@code T}）之前，因此「默认空实现退让」这一现象无法区分
 * 「顺序注解真的生效」与「字典序恰好帮了忙」。本夹具把外层类名换成字典序排在 {@code T} 之后的 {@code Zzz}
 * 前缀，用一对只差顺序注解的孪生自动配置做正反对照：</p>
 *
 * <ul>
 *   <li>{@link Ordered} 带 {@code @AutoConfigureBefore(TenantAutoConfiguration.class)} ⇒ 必须先注册，
 *       默认空实现退让；</li>
 *   <li>{@link Unordered} 无该注解（且类名字典序在后）⇒ 默认空实现抢先注册，孪生夹具退让。</li>
 * </ul>
 *
 * <p>注意：Spring Boot 的自动配置排序按<b>全限定类名</b>比较，故夹具必须是与
 * {@link TenantAutoConfiguration} 同级的顶层类（{@code Zzz...$Ordered}），
 * 若写成测试类的嵌套类，外层测试类名会替它决定排序位置，对照即失效。</p>
 *
 * @author wenbin
 * @since 2026-10-17
 */
final class ZzzTenantProviderOrderingFixtures {

    /** 哨兵租户 ID：用于确认「生效的是夹具」而非默认空实现。 */
    static final long MARKER_TENANT_ID = 4242L;

    private ZzzTenantProviderOrderingFixtures() {
    }

    /** 正向对照：带顺序注解，即便字典序不利（Zzz &gt; Tenant）也必须先于默认配置注册。 */
    @AutoConfiguration
    @AutoConfigureBefore(TenantAutoConfiguration.class)
    static class Ordered {

        @Bean
        @ConditionalOnMissingBean(TenantProvider.class)
        TenantProvider zzzOrderedTenantProvider() {
            return new MarkerTenantProvider();
        }
    }

    /** 反向对照：与 {@link Ordered} 唯一的差别是没有顺序注解。 */
    @AutoConfiguration
    static class Unordered {

        @Bean
        @ConditionalOnMissingBean(TenantProvider.class)
        TenantProvider zzzUnorderedTenantProvider() {
            return new MarkerTenantProvider();
        }
    }

    /** 哨兵实现：用于区分「夹具生效」与「默认空实现生效」。 */
    static class MarkerTenantProvider implements TenantProvider {

        @Override
        public Optional<Long> getCurrentTenantId() {
            return Optional.of(MARKER_TENANT_ID);
        }
    }
}

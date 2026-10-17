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
package cn.ypbin.starter.license.diagnostic;

import cn.ypbin.starter.license.core.LicenseContent;
import cn.ypbin.starter.license.core.LicenseManager;
import cn.ypbin.starter.license.core.LicenseStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * License 健康指示器：把授权状态与<strong>证书剩余有效天数</strong>暴露给 Actuator，
 * 供 Prometheus / Grafana 做续期预警。
 *
 * <p><strong>状态映射</strong>：{@link LicenseStatus#ILLEGAL} ⇒ {@code DOWN}，其余（合法 / 宽限期）⇒
 * {@code UP}。宽限期虽然可用，但已进入风险窗口，故在 details 里显式给出 {@code status=GRACE} 与
 * {@code graceRemainingDays}，接入方可按细节做分级告警，而不是让整个应用被标记为 DOWN。</p>
 *
 * <p><strong>剩余天数口径</strong>（均按自然日差计算，避免"不足 24 小时算 0 天"的错觉）：</p>
 * <ul>
 *     <li>{@code remainingDays}：距 {@code expireAt} 的自然日数（已过期为负）；</li>
 *     <li>{@code graceRemainingDays}：距 {@code expireAt + graceDays} 的自然日数（无宽限期时等于
 *     {@code remainingDays}）。</li>
 * </ul>
 *
 * <p><strong>不泄漏敏感信息</strong>：只暴露授权标识（licenseId）、被授权方（subject）、有效期与模块
 * 清单，不暴露任何密钥、机器指纹或授权串原文。</p>
 *
 * <p>仅在 classpath 存在 Actuator 时装配（见 {@code LicenseAutoConfiguration}，actuator 为可选依赖）。</p>
 *
 * @author wenbin
 * @since 2026-10-17
 */
public class LicenseHealthIndicator implements HealthIndicator {

    private final LicenseManager licenseManager;

    public LicenseHealthIndicator(LicenseManager licenseManager) {
        this.licenseManager = licenseManager;
    }

    @Override
    public Health health() {
        LicenseStatus status = licenseManager.getStatus();
        Health.Builder builder = status == LicenseStatus.ILLEGAL ? Health.down() : Health.up();
        builder.withDetail("status", status.name())
            .withDetail("description", status.getDescription());
        String reason = licenseManager.getReason();
        if (reason != null && !reason.isBlank()) {
            builder.withDetail("reason", reason);
        }
        LicenseContent content = licenseManager.getContent();
        if (content == null) {
            // 未加载到授权内容（无授权启动）：明确给出标记，避免"没有 details"被误读为"没接入"
            builder.withDetail("licenseLoaded", false);
            return builder.build();
        }
        LocalDate today = LocalDate.now();
        builder.withDetail("licenseLoaded", true)
            .withDetail("graceDays", content.graceDays())
            .withDetail("modules", content.modules());
        // 可能为空的字段一律不写空值键：Actuator 的 details 契约不接受 null 值
        builder = put(builder, "licenseId", content.licenseId());
        builder = put(builder, "subject", content.subject());
        builder = put(builder, "effectiveAt", text(content.effectiveAt()));
        builder = put(builder, "expireAt", text(content.expireAt()));
        builder = put(builder, "remainingDays", remainingDays(today, content.expireAt()));
        builder = put(builder, "graceRemainingDays",
            graceRemainingDays(today, content.expireAt(), content.graceDays()));
        return builder.build();
    }

    /**
     * 仅在值非空时写入 detail（Actuator 的 details 契约不接受 {@code null} 值）。
     *
     * @param builder 目标 builder
     * @param key     detail 键
     * @param value   detail 值，可为 {@code null}
     * @return builder（便于链式赋值）
     */
    private static Health.Builder put(Health.Builder builder, String key, @Nullable Object value) {
        return value == null ? builder : builder.withDetail(key, value);
    }

    /**
     * 距到期日的自然日数。
     *
     * @param today    今天
     * @param expireAt 到期时间（可空）
     * @return 剩余自然日数；到期时间缺失时返回 {@code null}（details 中表现为不出现该键值）
     */
    @Nullable
    private static Long remainingDays(LocalDate today, @Nullable LocalDateTime expireAt) {
        return expireAt == null ? null : ChronoUnit.DAYS.between(today, expireAt.toLocalDate());
    }

    /**
     * 距宽限期结束的自然日数；无宽限期（{@code graceDays <= 0}）时与 {@link #remainingDays} 一致。
     *
     * @param today    今天
     * @param expireAt 到期时间（可空）
     * @param graceDays 宽限天数
     * @return 剩余自然日数；到期时间缺失时返回 {@code null}
     */
    @Nullable
    private static Long graceRemainingDays(LocalDate today, @Nullable LocalDateTime expireAt, int graceDays) {
        if (expireAt == null) {
            return null;
        }
        long extra = Math.max(graceDays, 0);
        return ChronoUnit.DAYS.between(today, expireAt.toLocalDate().plusDays(extra));
    }

    @Nullable
    private static String text(@Nullable LocalDateTime value) {
        return value == null ? null : value.toString();
    }
}

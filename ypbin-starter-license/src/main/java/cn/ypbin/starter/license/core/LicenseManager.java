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
package cn.ypbin.starter.license.core;

import cn.ypbin.starter.license.exception.LicenseErrorCode;
import cn.ypbin.starter.license.exception.LicenseException;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * License 授权状态机。
 *
 * <p>持有当前运行环境的授权状态，是离线校验的核心。加载授权串后依次执行：SM4 解密 + SM2 验签 →
 * 机器指纹匹配 → 时钟回拨检测 → 生效/到期时间判定 → 宽限期判定，最终落定 {@link LicenseStatus}
 * 三态之一。合法与宽限期内可用，过期或校验失败即不可用（自动锁定）。</p>
 *
 * <p>状态可被反复重算（运行期定期任务会触发），因此时间跨入过期后无需重启即自动切换为不可用。
 * 时钟回拨检测基于进程内单调递增的「最近校验时刻」，一旦发现系统时间早于该时刻超过容差，即判定被篡改。
 * 跨重启的持久化留待联机阶段的存储扩展点补齐，此处不做静默兜底。</p>
 *
 * <p>除定期任务外，{@link #assertUsable()} 断言路径还带一层<strong>轻量过期快查</strong>（内部
 * {@code evaluateIfNeeded()}）：按本地时钟判定授权是否已过宽限期并直接切换状态，避免依赖业务手工调度
 * 定期任务而出现"授权已过期但仍可用"的窗口；快查有 5s 缓存且不做完整签名重验。边界说明：时钟回拨检测等
 * 全量重算仍走 {@link #evaluate()} 与定时任务，本快查不替代。</p>
 *
 * @author wenbin
 * @since 2026-08-05
 */
public class LicenseManager {

    private static final Logger log = LoggerFactory.getLogger(LicenseManager.class);

    /** 时钟回拨容差（秒）：小于此幅度的回退视为正常抖动 */
    private static final long CLOCK_TOLERANCE_SECONDS = 300L;

    /** 断言路径过期快查的最小间隔（毫秒）：5s 内不重复重算，避免每个请求都做状态判定 */
    private static final long EXPIRY_QUICK_CHECK_INTERVAL_MILLIS = 5000L;

    private final String sm2PublicKey;
    private final String sm4Key;
    private final boolean fingerprintEnabled;

    private volatile LicenseContent content;
    private volatile LicenseStatus status = LicenseStatus.ILLEGAL;
    private volatile String reason = "尚未加载授权";
    private volatile LocalDateTime lastSeenTime;

    /** 最近一次过期快查的时刻（毫秒时间戳，用于 5s 内不重算的节流） */
    private volatile long lastQuickCheckMillis;

    /**
     * @param sm2PublicKey       Base64 SM2 公钥（验签）
     * @param sm4Key             Base64 SM4 密钥（解密）
     * @param fingerprintEnabled 是否启用机器指纹绑定校验
     */
    public LicenseManager(String sm2PublicKey, String sm4Key, boolean fingerprintEnabled) {
        this.sm2PublicKey = sm2PublicKey;
        this.sm4Key = sm4Key;
        this.fingerprintEnabled = fingerprintEnabled;
    }

    /**
     * 加载并校验授权串，落定当前状态。
     *
     * @param authCode Base64 授权串
     * @return 校验后的授权内容
     * @throws LicenseException 解密、验签或指纹校验失败时抛出（并将状态置为不可用）
     */
    public synchronized LicenseContent load(String authCode) {
        LicenseContent parsed;
        try {
            parsed = LicenseSigner.verify(authCode, sm2PublicKey, sm4Key);
        } catch (LicenseException e) {
            markIllegal(e.getMessage());
            throw e;
        }
        verifyFingerprint(parsed);
        this.content = parsed;
        evaluate();
        log.info("[ypbin-starter] 授权加载完成：licenseId={}，subject={}，状态={}",
            parsed.licenseId(), parsed.subject(), status.getDescription());
        return parsed;
    }

    /**
     * 纯函数：给定授权内容与参照时刻，判定授权应处的状态。
     *
     * <p>只做「未生效 / 生效期内 / 宽限期内 / 已过期」的时间窗判定，不含时钟回拨检测、不改状态、不抛异常、
     * 不打日志，因此可安全地被无状态调用方（如签发端的授权状态可视化）复用。实例的 {@link #evaluate()}
     * 亦以此为决策核心，保证运行端与展示端对同一份授权得出一致结论。</p>
     *
     * @param content 授权内容（为 {@code null} 视为不可用）
     * @param now     参照时刻
     * @return 授权状态三态之一（未生效归入 {@link LicenseStatus#ILLEGAL}）
     */
    public static LicenseStatus evaluateAt(LicenseContent content, LocalDateTime now) {
        if (content == null) {
            return LicenseStatus.ILLEGAL;
        }
        if (content.effectiveAt() != null && now.isBefore(content.effectiveAt())) {
            return LicenseStatus.ILLEGAL;
        }
        LocalDateTime expireAt = content.expireAt();
        if (expireAt == null) {
            return LicenseStatus.LEGAL;
        }
        if (!now.isAfter(expireAt)) {
            return LicenseStatus.LEGAL;
        }
        LocalDateTime graceEnd = expireAt.plusDays(Math.max(0, content.graceDays()));
        if (content.graceDays() > 0 && !now.isAfter(graceEnd)) {
            return LicenseStatus.GRACE;
        }
        return LicenseStatus.ILLEGAL;
    }

    /**
     * 依据当前时间重新评估授权状态（供定期任务调用，实现过期自动锁定）。
     *
     * <p>时间窗决策复用 {@link #evaluateAt(LicenseContent, LocalDateTime)}，并在此基础上补充实例特有的
     * 副作用：时钟回拨检测、状态迁移日志、以及「尚未生效」显式抛出，交由调用方感知而非静默。</p>
     */
    public synchronized void evaluate() {
        if (content == null) {
            markIllegal("尚未加载授权");
            return;
        }
        LocalDateTime now = now();
        if (content.effectiveAt() != null && now.isBefore(content.effectiveAt())) {
            transit(LicenseStatus.ILLEGAL, "授权尚未到生效时间：" + content.effectiveAt());
            throw new LicenseException(LicenseErrorCode.LICENSE_NOT_YET_VALID);
        }
        LocalDateTime expireAt = content.expireAt();
        switch (evaluateAt(content, now)) {
            case LEGAL -> transit(LicenseStatus.LEGAL,
                expireAt == null ? "永久授权" : "授权有效，到期时间：" + expireAt);
            case GRACE -> transit(LicenseStatus.GRACE,
                "授权已过期，宽限期至：" + expireAt.plusDays(Math.max(0, content.graceDays())));
            case ILLEGAL -> transit(LicenseStatus.ILLEGAL, "授权已过期：" + expireAt);
        }
    }

    /**
     * 断言当前授权可用（合法或宽限期内），否则抛出。
     *
     * <p>断言前先做基于本地时钟的轻量过期快查（见 {@link #evaluateIfNeeded()}），使授权跨入过期后
     * 即使定期任务尚未触发也能及时切换为不可用。</p>
     *
     * @throws LicenseException 授权不可用时抛出
     */
    public void assertUsable() {
        // 过期快查：仅按本地时钟判定是否已过宽限期，5s 内不重算；完整重算（含时钟回拨检测）仍走 evaluate
        evaluateIfNeeded();
        if (!status.isUsable()) {
            throw new LicenseException(LicenseErrorCode.LICENSE_EXPIRED, reason);
        }
    }

    /**
     * 断言指定功能模块已授权。
     *
     * @param module 模块标识
     * @throws LicenseException 未授权该模块时抛出
     */
    public void assertModule(String module) {
        assertUsable();
        if (content == null || !content.hasModule(module)) {
            throw new LicenseException(LicenseErrorCode.LICENSE_MODULE_UNLICENSED,
                "功能模块未授权：" + module);
        }
    }

    /**
     * 断言某业务额度未超限。
     *
     * @param key     业务参数名
     * @param current 当前使用量
     * @throws LicenseException 超出授权额度时抛出
     */
    public void assertQuota(String key, long current) {
        assertUsable();
        Long limit = content == null ? null : content.quota(key);
        if (limit != null && current > limit) {
            throw new LicenseException(LicenseErrorCode.LICENSE_QUOTA_EXCEEDED,
                "已达授权额度上限[" + key + "]：" + current + "/" + limit);
        }
    }

    /**
     * 轻量过期快查（断言路径使用）。
     *
     * <p>只按本地时钟判定「授权是否已过宽限期」，是则直接把状态切换为不可用，使过期即时生效、
     * 不依赖业务手工调度定期任务。5s 节流避免每请求重算，且不做完整签名重验（成本考虑）。
     * 边界：本快查是纯本地时钟的过期判定，不含时钟回拨检测——时钟异常场景的全量重算仍由
     * {@link #evaluate()} / 定时任务负责。未加载授权或已处于不可用时不切换，恢复路径由
     * {@link #load(String)} / {@link #evaluate()} 主导。</p>
     */
    private void evaluateIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastQuickCheckMillis < EXPIRY_QUICK_CHECK_INTERVAL_MILLIS) {
            return;
        }
        synchronized (this) {
            // 双重检查：并发请求只允许一个线程进入重算，其余请求直接读取已缓存结论
            long recheck = System.currentTimeMillis();
            if (recheck - lastQuickCheckMillis < EXPIRY_QUICK_CHECK_INTERVAL_MILLIS) {
                return;
            }
            lastQuickCheckMillis = recheck;
            LicenseContent current = content;
            if (current == null || !status.isUsable()) {
                return;
            }
            LocalDateTime expireAt = current.expireAt();
            if (expireAt == null) {
                return; // 永久授权，无过期判定
            }
            LocalDateTime wallNow = LocalDateTime.now();
            LocalDateTime graceEnd = expireAt.plusDays(Math.max(0, current.graceDays()));
            if (wallNow.isAfter(graceEnd)) {
                // 已过宽限期：直接切不可用（合法期与宽限期内均可用，无需切换）
                transit(LicenseStatus.ILLEGAL, "授权已过期：" + expireAt);
            }
        }
    }

    /**
     * 校验机器指纹绑定。
     *
     * @param parsed 授权内容
     * @throws LicenseException 指纹不匹配时抛出（并锁定状态）
     */
    private void verifyFingerprint(LicenseContent parsed) {
        if (!fingerprintEnabled || !parsed.isMachineBound()) {
            return;
        }
        String current = MachineFingerprint.current();
        if (!parsed.matchesFingerprint(current)) {
            markIllegal("机器指纹不匹配，当前=" + current);
            throw new LicenseException(LicenseErrorCode.LICENSE_FINGERPRINT_MISMATCH);
        }
    }

    /**
     * 获取当前时间并执行时钟回拨检测。
     *
     * @return 当前时间
     * @throws LicenseException 检测到系统时间被回拨篡改时抛出
     */
    private LocalDateTime now() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime seen = lastSeenTime;
        if (seen != null && now.isBefore(seen.minusSeconds(CLOCK_TOLERANCE_SECONDS))) {
            markIllegal("检测到系统时间回拨，last=" + seen + "，now=" + now);
            throw new LicenseException(LicenseErrorCode.LICENSE_CLOCK_TAMPERED);
        }
        if (seen == null || now.isAfter(seen)) {
            lastSeenTime = now;
        }
        return now;
    }

    /**
     * 迁移到目标状态并记录原因。
     *
     * @param target 目标状态
     * @param why    状态原因
     */
    private void transit(LicenseStatus target, String why) {
        if (this.status != target) {
            log.info("[ypbin-starter] 授权状态变更：{} -> {}（{}）", status.getDescription(),
                target.getDescription(), why);
        }
        this.status = target;
        this.reason = why;
    }

    /**
     * 直接锁定为不可用。
     *
     * @param why 锁定原因
     */
    private void markIllegal(String why) {
        transit(LicenseStatus.ILLEGAL, why);
    }

    public LicenseContent getContent() {
        return content;
    }

    public LicenseStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }
}

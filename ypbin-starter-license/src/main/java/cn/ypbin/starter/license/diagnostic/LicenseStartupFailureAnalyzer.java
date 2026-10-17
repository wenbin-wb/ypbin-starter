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

import cn.ypbin.starter.license.exception.LicenseErrorCode;
import cn.ypbin.starter.license.exception.LicenseException;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * License 启动失败诊断器：把启动期的 {@link LicenseException} 转成 Spring Boot 官方风格的
 * <strong>Description + Action</strong> 提示框。
 *
 * <p><strong>解决什么问题</strong>：{@code ypbin-starter-license} 在启动阶段就会校验授权（密钥缺失、
 * 授权文件缺失/损坏、指纹不匹配等都会直接抛 {@link LicenseException}）。没有诊断器时，接入方看到的
 * 是数百行的 {@code BeanCreationException} 堆栈与一句内部错误码文案，新人难以判断"该改哪个配置项"。
 * 本类把错误码映射为<strong>可执行的修复动作</strong>。</p>
 *
 * <p>注册方式：{@code META-INF/spring.factories} 中的
 * {@code org.springframework.boot.diagnostics.FailureAnalyzer}（Boot 4 仍以此加载诊断器，
 * 见 {@code FailureAnalyzers} 构造器对 {@code SpringFactoriesLoader.forDefaultResourceLocation()} 的调用）。</p>
 *
 * <p>只做<strong>提示</strong>，不改变任何启动/拒绝行为：授权校验的 fail-fast 语义保持原样。</p>
 *
 * @author wenbin
 * @since 2026-10-17
 */
public class LicenseStartupFailureAnalyzer extends AbstractFailureAnalyzer<LicenseException> {

    /** 配置前缀（与 {@code LicenseProperties#PREFIX} 同值；此处为提示文案单一来源） */
    private static final String PREFIX = "ypbin.license";

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, LicenseException cause) {
        return new FailureAnalysis(description(cause), action(cause), cause);
    }

    private static String description(LicenseException cause) {
        LicenseErrorCode errorCode = errorCodeOf(cause);
        String label = errorCode == null ? "未知授权错误" : errorCode.name();
        String detail = cause.getMessage();
        String reason = (detail == null || detail.isBlank()) ? "（无附加说明）" : detail;
        return "[ypbin-starter] License 授权启动校验失败（" + label + "，code=" + cause.getCode() + "）：" + reason;
    }

    private static String action(LicenseException cause) {
        int code = cause.getCode();
        if (code == LicenseErrorCode.LICENSE_MISSING.getCode()) {
            return "放置授权文件到 " + PREFIX + ".location 指向的路径后重启；"
                + "如确需无授权启动，显式设置 " + PREFIX + ".allow-startup-without-license=true"
                + "（此时受保护能力会被拦截而不是放行）。";
        }
        if (code == LicenseErrorCode.LICENSE_CORRUPTED.getCode()) {
            return "核对 " + PREFIX + ".location 指向的授权文件是否完整；"
                + "若提示「缺少授权校验密钥」，请补齐 " + PREFIX + ".public-key 与 " + PREFIX + ".secret-key。";
        }
        if (code == LicenseErrorCode.LICENSE_REMOTE_REJECTED.getCode()) {
            return "核对 " + PREFIX + ".online.*（base-url / access-key / secret-key）"
                + "与签发端「开放应用」的启用状态；确认网络可直达签发端。";
        }
        if (code == LicenseErrorCode.LICENSE_FINGERPRINT_MISMATCH.getCode()) {
            return "授权绑定了其它机器的指纹：请向供应方重新签发与本机匹配的授权，或调整 "
                + PREFIX + ".fingerprint-enabled 后重新签发。";
        }
        if (code == LicenseErrorCode.LICENSE_EXPIRED.getCode()
            || code == LicenseErrorCode.LICENSE_NOT_YET_VALID.getCode()
            || code == LicenseErrorCode.LICENSE_CLOCK_TAMPERED.getCode()) {
            return "核对授权有效期（effectiveAt / expireAt）与服务器系统时间是否准确；"
                + "过期请向供应方续期。";
        }
        return "检查 " + PREFIX + ".* 配置与授权文件内容；"
            + "确需降级启动可设置 " + PREFIX + ".allow-startup-without-license=true（受保护能力将被拦截）。";
    }

    @Nullable
    private static LicenseErrorCode errorCodeOf(LicenseException cause) {
        for (LicenseErrorCode errorCode : LicenseErrorCode.values()) {
            if (errorCode.getCode() == cause.getCode()) {
                return errorCode;
            }
        }
        return null;
    }
}

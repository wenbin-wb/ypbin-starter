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
package cn.ypbin.starter.license.exception;

import cn.ypbin.starter.core.exception.ErrorCode;

/**
 * License 授权错误码。
 *
 * <p>使用 7000 区段与框架内置的三位 HTTP 语义码区分。授权失败一律经全局异常处理器转为统一响应体，
 * 由调用方按码值区分「未授权」「已过期」「指纹不匹配」等具体拦截原因。</p>
 *
 * @author wenbin
 * @since 2026-08-05
 */
public enum LicenseErrorCode implements ErrorCode {

    /** 未加载到授权文件 */
    LICENSE_MISSING(7001, "未检测到有效授权，请联系供应方获取授权文件"),

    /** 授权文件解析失败（解密或格式错误） */
    LICENSE_CORRUPTED(7002, "授权文件已损坏或无法解析"),

    /** 授权签名验证失败 */
    LICENSE_SIGNATURE_INVALID(7003, "授权签名验证失败，文件可能被篡改"),

    /** 机器指纹不匹配 */
    LICENSE_FINGERPRINT_MISMATCH(7004, "授权与当前运行环境不匹配，禁止跨机器使用"),

    /** 授权尚未生效 */
    LICENSE_NOT_YET_VALID(7005, "授权尚未到生效时间"),

    /** 授权已过期（含宽限期已耗尽） */
    LICENSE_EXPIRED(7006, "授权已过期，请续期后继续使用"),

    /** 系统时间疑似被回拨篡改 */
    LICENSE_CLOCK_TAMPERED(7007, "检测到系统时间异常，授权校验被拒绝"),

    /** 联机校验失败（被远程吊销或校验端拒绝） */
    LICENSE_REMOTE_REJECTED(7008, "联机授权校验未通过，授权可能已被吊销"),

    /** 受保护的功能模块未授权 */
    LICENSE_MODULE_UNLICENSED(7009, "当前功能模块未包含在授权范围内"),

    /** 业务参数超出授权额度（设备数/用户数等） */
    LICENSE_QUOTA_EXCEEDED(7010, "已达到授权额度上限");

    private final int code;
    private final String message;

    LicenseErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}

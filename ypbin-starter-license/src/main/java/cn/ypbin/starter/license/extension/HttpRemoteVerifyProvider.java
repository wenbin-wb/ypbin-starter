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
package cn.ypbin.starter.license.extension;

import cn.ypbin.starter.license.core.LicenseContent;
import cn.ypbin.starter.license.exception.LicenseErrorCode;
import cn.ypbin.starter.license.exception.LicenseException;
import cn.ypbin.starter.sign.core.SignAlgorithm;
import cn.ypbin.starter.sign.core.SignClient;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 基于 HTTP 的联机校验参考实现。
 *
 * <p>对接供应方提供的联机校验服务（约定 {@code GET {base}/open/license/verify}，请求经接口签名鉴权：
 * 携带 {@code accessKey/timestamp/nonce/sign} 四件套，响应 {@code {data:{valid, reason}}}），把当前授权
 * 编号与机器指纹上报校验，感知远程吊销。配置 {@code ypbin.license.online.*} 即自动装配；业务侧仍可自定义
 * {@link RemoteVerifyProvider} 实现覆盖。</p>
 *
 * <p>鉴权密钥来自签发端「开放应用管理」签发的 AK/SK：accessKey 公开标识、secretKey 参与签名，应用级独立
 * 可吊销，替代原先的共享令牌——某个应用密钥泄露只影响该应用，可在管理端禁用/重置。</p>
 *
 * <p><strong>三桶裁决</strong>：服务端响应 {@code data.valid} 为明确布尔值时才算「明确裁决」——
 * {@code true} 放行并进入长缓存窗口（{@link #cacheMillis}）；{@code false} 抛
 * {@link LicenseException} 阻断，不缓存。其余一切（连接失败/超时/非 200/响应体无法解析/
 * {@code valid} 字段缺失或非布尔）都是「放行但不明确」，绝不当作明确有效处理，只进入更短的放行窗口
 * （{@link #failOpenMillis}），避免网络抖动把正常业务锁死，也避免吊销感知被误当作长缓存掩盖。</p>
 *
 * <p><strong>防打爆</strong>：放行结果只缓存 {@link #failOpenMillis}，连续放行次数达到
 * {@link #failOpenThreshold} 后升级为更长的退避窗口 {@link #failOpenBackoffMillis}（仍是放行，不是
 * 拒绝）；服务端任意一次明确响应（有效或无效）都会重置连续放行计数。缓存命中判断与实际联机校验之间用
 * {@link #verifyLock} 做单飞（single-flight）：并发请求在缓存未命中时只会有一个真正发起 HTTP 调用，
 * 其余等待其结果，避免联机服务不可用/高并发下被同时打出大量重复请求。</p>
 *
 * @author wenbin
 * @since 2026-08-06
 */
public class HttpRemoteVerifyProvider implements RemoteVerifyProvider {

    private static final Logger log = LoggerFactory.getLogger(HttpRemoteVerifyProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String VERIFY_PATH = "/open/license/verify";

    private final String baseUrl;
    private final String accessKey;
    private final String secretKey;
    private final Duration timeout;
    private final long cacheMillis;
    private final long failOpenMillis;
    private final int failOpenThreshold;
    private final long failOpenBackoffMillis;
    private final RemoteFailurePolicy failurePolicy;
    private final HttpClient client;
    private final Object verifyLock = new Object();

    /** 最近一次服务端明确返回有效的时间戳（毫秒）；0 表示从未明确校验通过。volatile 保证多线程可见 */
    private volatile long lastValidAt;

    /** 放行窗口的到期时间戳（毫秒）；窗口内跳过联机，直接放行。volatile 保证多线程可见 */
    private volatile long failOpenUntil;

    /** 连续放行（未明确裁决）次数，仅在 {@link #verifyLock} 内读写 */
    private int consecutiveFailOpenCount;

    public HttpRemoteVerifyProvider(String baseUrl, String accessKey, String secretKey, Duration timeout,
        long cacheSeconds, long failOpenCacheSeconds, int failOpenThreshold, long failOpenBackoffSeconds,
        RemoteFailurePolicy failurePolicy) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.timeout = timeout;
        this.cacheMillis = Math.max(0, cacheSeconds) * 1000L;
        this.failOpenMillis = Math.max(0, failOpenCacheSeconds) * 1000L;
        this.failOpenThreshold = Math.max(1, failOpenThreshold);
        this.failOpenBackoffMillis = Math.max(0, failOpenBackoffSeconds) * 1000L;
        this.failurePolicy = failurePolicy;
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public void verify(LicenseContent content, String fingerprint) {
        if (content == null || content.licenseId() == null) {
            return;
        }
        if (isCacheHit()) {
            return;
        }
        synchronized (verifyLock) {
            // 双检：可能在等锁期间已被其他线程完成联机校验并写入缓存/放行窗口
            if (isCacheHit()) {
                return;
            }
            doVerify(content, fingerprint);
        }
    }

    /**
     * 判断是否命中缓存（长缓存窗口内的明确有效，或放行窗口内的不明确裁决），命中则本次直接放行。
     *
     * @return 是否命中缓存
     */
    private boolean isCacheHit() {
        long now = System.currentTimeMillis();
        long validSince = lastValidAt;
        if (validSince != 0 && now - validSince < cacheMillis) {
            return true;
        }
        return now < failOpenUntil;
    }

    /**
     * 实际发起联机校验并按三桶裁决更新缓存/放行窗口状态；调用方须持有 {@link #verifyLock}。
     *
     * @param content 授权内容
     * @param fingerprint 机器指纹
     */
    private void doVerify(LicenseContent content, String fingerprint) {
        String licenseId = content.licenseId();
        // 用开放应用 AK/SK 对业务参数签名，生成四件套（accessKey/timestamp/nonce/sign）随请求上报
        Map<String, String> signed = SignClient.sign(
            Map.of("licenseId", licenseId, "fingerprint", fingerprint == null ? "" : fingerprint),
            accessKey, secretKey, SignAlgorithm.HMAC_SHA256);
        URI uri = URI.create(baseUrl + VERIFY_PATH + "?" + buildQuery(signed));
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(timeout)
            .GET()
            .build();
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[ypbin-starter] 联机校验被中断(licenseId={}):{}", licenseId, e.getMessage());
            handleIndeterminate(licenseId);
            return;
        } catch (IOException e) {
            log.warn("[ypbin-starter] 联机校验服务不可达(licenseId={}):{}", licenseId, e.getMessage());
            handleIndeterminate(licenseId);
            return;
        }
        if (response.statusCode() != 200) {
            log.warn("[ypbin-starter] 联机校验服务异常(HTTP {}，licenseId={})",
                response.statusCode(), licenseId);
            handleIndeterminate(licenseId);
            return;
        }
        JsonNode data;
        try {
            data = MAPPER.readTree(response.body()).path("data");
        } catch (Exception e) {
            log.warn("[ypbin-starter] 联机校验响应解析失败(licenseId={}):{}", licenseId, e.getMessage());
            handleIndeterminate(licenseId);
            return;
        }
        JsonNode validNode = data.path("valid");
        if (!validNode.isBoolean()) {
            log.warn("[ypbin-starter] 联机校验响应缺少有效的 valid 字段(licenseId={})", licenseId);
            handleIndeterminate(licenseId);
            return;
        }
        if (validNode.booleanValue()) {
            markValid();
            return;
        }
        // 明确拒绝：重置放行计数（服务端可达且给出明确答复），不缓存，直接阻断
        consecutiveFailOpenCount = 0;
        failOpenUntil = 0;
        String reason = data.path("reason").asText("");
        throw new LicenseException(LicenseErrorCode.LICENSE_REMOTE_REJECTED,
            "联机授权校验未通过：" + (reason.isBlank() ? "授权可能已被吊销" : reason));
    }

    /** 无明确裁决时按配置选择阻断或告警放行 */
    private void handleIndeterminate(String licenseId) {
        if (failurePolicy == RemoteFailurePolicy.FAIL_CLOSED) {
            consecutiveFailOpenCount = 0;
            failOpenUntil = 0;
            log.warn("[ypbin-starter] 联机授权校验未获得明确结果，按 FAIL_CLOSED 拒绝(licenseId={})", licenseId);
            throw new LicenseException(LicenseErrorCode.LICENSE_REMOTE_REJECTED,
                "联机授权校验未获得明确结果：" + licenseId);
        }
        log.warn("[ypbin-starter] 联机授权校验未获得明确结果，按 FAIL_OPEN_WITH_WARNING 放行(licenseId={})",
            licenseId);
        markFailOpen();
    }

    /** 服务端明确返回有效：进入长缓存窗口，重置放行计数 */
    private void markValid() {
        lastValidAt = System.currentTimeMillis();
        failOpenUntil = 0;
        consecutiveFailOpenCount = 0;
    }

    /** 放行但不明确有效：进入放行窗口，连续放行达阈值后升级为退避窗口 */
    private void markFailOpen() {
        consecutiveFailOpenCount++;
        long window = consecutiveFailOpenCount >= failOpenThreshold ? failOpenBackoffMillis : failOpenMillis;
        failOpenUntil = System.currentTimeMillis() + window;
    }

    /**
     * 去除根地址末尾斜杠，避免与路径拼接出双斜杠。
     *
     * @param baseUrl 根地址
     * @return 去掉尾部 {@code /} 的地址
     */
    private static String stripTrailingSlash(String baseUrl) {
        return baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    }

    /**
     * 把签名参数集拼成查询串（空值跳过，与服务端 {@code SignChecker} 的参与参数集合一致）。
     *
     * @param params 签名参数集（含四件套与业务参数）
     * @return 查询串（不含 {@code ?}）
     */
    private static String buildQuery(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String value = entry.getValue();
            if (value == null || value.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(encode(entry.getKey())).append('=').append(encode(value));
        }
        return sb.toString();
    }

    /**
     * URL 编码查询参数（空格转 {@code %20}，避免 {@code +} 歧义）。
     *
     * @param value 参数值
     * @return 编码后文本
     */
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
            .replace("+", "%20");
    }
}

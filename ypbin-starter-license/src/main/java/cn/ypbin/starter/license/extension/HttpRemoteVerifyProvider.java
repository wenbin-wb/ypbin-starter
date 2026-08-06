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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * <p>网络容忍采用「放行+告警」：连接失败、超时、非 200 或响应解析失败均视为瞬时异常，放行本次校验并
 * 输出告警日志；只有服务端明确返回 {@code valid=false} 才抛 {@link LicenseException} 阻断——避免网络
 * 抖动把正常业务锁死，同时绝不静默掩盖明确的吊销判定。</p>
 *
 * <p><strong>缓存窗口</strong>：为避免 {@code @LicenseCheck(online=true)} 每次方法调用都发 HTTP，最近一次
 * 服务端<strong>明确返回有效</strong>后，窗口内（{@code ypbin.license.online.cache-seconds}，默认 1 小时）
 * 直接放行不再重复联机。网络异常/非 200 等「放行但不明确有效」<strong>不进入缓存窗口</strong>，下次调用
 * 仍会重试，避免服务恢复后吊销感知被窗口掩盖。吊销感知延迟 ≤ 缓存窗口。</p>
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
    private final HttpClient client;

    /** 最近一次服务端明确返回有效的时间戳（毫秒）；0 表示从未明确校验通过。volatile 保证多线程可见 */
    private volatile long lastValidAt;

    public HttpRemoteVerifyProvider(String baseUrl, String accessKey, String secretKey, Duration timeout,
        long cacheSeconds) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.timeout = timeout;
        this.cacheMillis = Math.max(0, cacheSeconds) * 1000L;
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public void verify(LicenseContent content, String fingerprint) {
        if (content == null || content.licenseId() == null) {
            return;
        }
        // 缓存窗口内直接放行：最近一次明确有效距今未超窗口，无需重复联机
        long cached = lastValidAt;
        if (cached != 0 && System.currentTimeMillis() - cached < cacheMillis) {
            return;
        }
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
            log.warn("[ypbin-starter] 联机校验被中断，本次放行(licenseId={}):{}", licenseId, e.getMessage());
            return;
        } catch (IOException e) {
            log.warn("[ypbin-starter] 联机校验服务不可达，本次放行(licenseId={}):{}", licenseId, e.getMessage());
            return;
        }
        if (response.statusCode() != 200) {
            log.warn("[ypbin-starter] 联机校验服务异常(HTTP {})，本次放行(licenseId={})",
                response.statusCode(), licenseId);
            return;
        }
        try {
            JsonNode data = MAPPER.readTree(response.body()).path("data");
            if (data.path("valid").asBoolean(true)) {
                // 仅「服务端明确有效」进入缓存窗口；网络放行路径不更新，下次调用仍会重试
                lastValidAt = System.currentTimeMillis();
                return;
            }
            String reason = data.path("reason").asText("");
            throw new LicenseException(LicenseErrorCode.LICENSE_REMOTE_REJECTED,
                "联机授权校验未通过：" + (reason.isBlank() ? "授权可能已被吊销" : reason));
        } catch (LicenseException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[ypbin-starter] 联机校验响应解析失败，本次放行(licenseId={}):{}", licenseId, e.getMessage());
        }
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

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 HTTP 的联机校验参考实现。
 *
 * <p>对接供应方提供的联机校验服务（约定 {@code GET {base}/open/license/verify}，请求头
 * {@code X-License-Token} 携带共享令牌，响应 {@code {data:{valid, reason}}}），把当前授权编号与机器
 * 指纹上报校验，感知远程吊销。配置 {@code ypbin.license.online.*} 即自动装配；业务侧仍可自定义
 * {@link RemoteVerifyProvider} 实现覆盖。</p>
 *
 * <p>网络容忍采用「放行+告警」：连接失败、超时、非 200 或响应解析失败均视为瞬时异常，放行本次校验并
 * 输出告警日志；只有服务端明确返回 {@code valid=false} 才抛 {@link LicenseException} 阻断——避免网络
 * 抖动把正常业务锁死，同时绝不静默掩盖明确的吊销判定。</p>
 *
 * @author wenbin
 * @since 2026-08-06
 */
public class HttpRemoteVerifyProvider implements RemoteVerifyProvider {

    private static final Logger log = LoggerFactory.getLogger(HttpRemoteVerifyProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TOKEN_HEADER = "X-License-Token";
    private static final String VERIFY_PATH = "/open/license/verify";

    private final String baseUrl;
    private final String token;
    private final Duration timeout;
    private final HttpClient client;

    public HttpRemoteVerifyProvider(String baseUrl, String token, Duration timeout) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.token = token;
        this.timeout = timeout;
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public void verify(LicenseContent content, String fingerprint) {
        if (content == null || content.licenseId() == null) {
            return;
        }
        String licenseId = content.licenseId();
        URI uri = URI.create(baseUrl + VERIFY_PATH
            + "?licenseId=" + encode(licenseId)
            + "&fingerprint=" + encode(fingerprint));
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(timeout)
            .header(TOKEN_HEADER, token == null ? "" : token)
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
     * URL 编码查询参数（空格转 {@code %20}，避免 {@code +} 歧义）。
     *
     * @param value 参数值
     * @return 编码后文本
     */
    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8)
            .replace("+", "%20");
    }
}

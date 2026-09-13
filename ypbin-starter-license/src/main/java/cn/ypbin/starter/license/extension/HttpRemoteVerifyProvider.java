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
import cn.ypbin.starter.license.extension.client.LicenseVerifyApi;
import cn.ypbin.starter.license.extension.client.LicenseVerifyResponse;
import cn.ypbin.starter.sign.core.SignAlgorithm;
import cn.ypbin.starter.sign.core.SignClient;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

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

    /** 联机服务基础地址，仅用于日志 */
    private final String baseUrl;
    private final String accessKey;
    private final String secretKey;
    private final Duration timeout;
    private final long cacheMillis;
    private final long failOpenMillis;
    private final int failOpenThreshold;
    private final long failOpenBackoffMillis;
    private final RemoteFailurePolicy failurePolicy;

    /** 外部 API 的声明式客户端（Spring 生成代理）：查询串、序列化、超时与错误转换均由框架负责 */
    private final LicenseVerifyApi verifyApi;

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
        this.verifyApi = buildApiClient(baseUrl, timeout);
    }

    /**
     * 构建声明式外部 API 客户端。
     *
     * <p>传输层沿用 JDK {@link HttpClient} 并显式设置连接与读取超时（与迁移前一致）；
     * 不设置超时会退化为 JDK 默认的无限等待，违反「远程调用必须显式超时」铁律。</p>
     *
     * @param baseUrl 服务基础地址
     * @param timeout 连接/读取超时
     * @return 声明式客户端
     */
    private static LicenseVerifyApi buildApiClient(String baseUrl, Duration timeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        // 显式注册 Jackson 3 转换器：本类自行构建 RestClient（非 Boot 自动配置），
        // 默认转换器不含 JSON 支持，缺它会导致响应无法反序列化并被当成「未明确裁决」。
        // 放宽 supported media type 到 */*：部分开放平台对 JSON 响应未正确设置
        // Content-Type（或返回 text/plain），若严格要求 application/json 会一律判为
        // 「未明确裁决」，在 FAIL_OPEN 策略下等于永久静默放行、吊销永远感知不到——
        // 对吊销校验而言宽容更安全（迁移前用 ofString() 解析，同样不依赖 Content-Type）。
        // 注意：此处不能用 withJsonConverter()——它要求转换器精确声明 application/json
        // （MediaType#equalsTypeAndSubtype 校验），会把 */* 直接判为非法参数。
        JacksonJsonHttpMessageConverter jsonConverter = new JacksonJsonHttpMessageConverter();
        jsonConverter.setSupportedMediaTypes(List.of(MediaType.ALL));
        RestClient restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .configureMessageConverters(converters -> converters
                .registerDefaults()
                .configureMessageConvertersList(list -> {
                    list.removeIf(converter -> converter instanceof JacksonJsonHttpMessageConverter);
                    list.add(jsonConverter);
                }))
            .build();
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
            .build()
            .createClient(LicenseVerifyApi.class);
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
        LicenseVerifyResponse response;
        try {
            // fingerprint 为空时传 null，框架会省略该查询参数——服务端按实际收到的参数重算签名，
            // 多送一个空参数会导致验签失败
            response = verifyApi.verify(signed.get("accessKey"), signed.get("timestamp"),
                signed.get("nonce"), signed.get("sign"), signed.get("licenseId"),
                fingerprint == null || fingerprint.isEmpty() ? null : fingerprint);
        } catch (RestClientException e) {
            // 网络不可达/超时/非 2xx/响应体无法转换，统一按「未明确裁决」处理：
            // 绝不当作明确有效（否则网络抖动会掩盖吊销），也不直接拒绝（由 failurePolicy 决定）
            if (Thread.currentThread().isInterrupted()) {
                log.warn("[ypbin-starter] 联机校验被中断(licenseId={}):{}", licenseId, e.getMessage());
            } else {
                log.warn("[ypbin-starter] 联机校验未获得明确结果(licenseId={}):{}", licenseId, e.getMessage());
            }
            handleIndeterminate(licenseId);
            return;
        }
        LicenseVerifyResponse.VerifyData data = response == null ? null : response.data();
        Boolean valid = data == null ? null : data.valid();
        if (valid == null) {
            log.warn("[ypbin-starter] 联机校验响应缺少有效的 valid 字段(licenseId={})", licenseId);
            handleIndeterminate(licenseId);
            return;
        }
        if (valid) {
            markValid();
            return;
        }
        // 明确拒绝：重置放行计数（服务端可达且给出明确答复），不缓存，直接阻断
        consecutiveFailOpenCount = 0;
        failOpenUntil = 0;
        String reason = data.reason() == null ? "" : data.reason();
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
}

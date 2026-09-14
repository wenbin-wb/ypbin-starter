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
package cn.ypbin.starter.license.extension.client;

import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * 联机授权校验服务契约（外部 API，Spring 声明式 HTTP 客户端）。
 *
 * <p>对接供应方开放平台的 {@code GET /open/license/verify}：请求经接口签名鉴权
 * （{@code accessKey/timestamp/nonce/sign} 四件套 + 业务参数 {@code licenseId/fingerprint}），
 * 响应体形如 {@code {"data":{"valid":true,"reason":"ok"}}}。</p>
 *
 * <p><strong>为何用声明式 HTTP 接口而不是 Feign</strong>：这是<b>外部第三方 API</b>，
 * 没有服务发现、负载均衡与本地降级需求，Feign 的 Spring Cloud 全家桶属于过度依赖；
 * 手写 {@code HttpClient} 则要自己拼查询串、序列化与判状态码。
 * 声明式接口由 Spring 生成代理实现，签名、序列化、超时与错误转换全部交给框架，
 * 业务代码只表达「调什么、传什么、拿什么」。</p>
 *
 * <p><strong>空值必须省略</strong>：服务端按「实际收到的参数集合」重算签名，若把空参数也发出去
 * （如 {@code fingerprint=}），会与服务端计算不一致导致验签失败。因此本方法把
 * {@code fingerprint} 声明为可空，由调用方传 {@code null} 表示该参数不参与请求
 * （见 {@code HttpRemoteVerifyProvider}）。</p>
 *
 * @author wenbin
 * @since 2026-09-13
 */
@HttpExchange(url = "/open/license/verify")
public interface LicenseVerifyApi {

    /**
     * 上报授权编号与机器指纹，校验是否被远程吊销。
     *
     * @param accessKey   开放应用标识（公开）
     * @param timestamp   时间戳（秒）
     * @param nonce       随机串（防重放）
     * @param sign        签名值
     * @param licenseId   授权编号
     * @param fingerprint 机器指纹；{@code null} 表示不携带该参数
     * @return 校验响应
     */
    @GetExchange
    LicenseVerifyResponse verify(
        @RequestParam("accessKey") String accessKey,
        @RequestParam("timestamp") String timestamp,
        @RequestParam("nonce") String nonce,
        @RequestParam("sign") String sign,
        @RequestParam("licenseId") String licenseId,
        @RequestParam(value = "fingerprint", required = false) @Nullable String fingerprint);
}

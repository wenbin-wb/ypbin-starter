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
package cn.ypbin.starter.tracking.web;

import cn.ypbin.starter.core.util.RequestIdUtils;
import cn.ypbin.starter.tools.support.RequestUtils;
import cn.ypbin.starter.tracking.core.TrackIdentityProvider;
import cn.ypbin.starter.tracking.core.TrackRequestContext;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 从当前 HTTP 请求解析采集上下文（IP / User-Agent / 链路 ID / 登录身份）。
 *
 * <p><strong>为什么单独成类</strong>：Controller 严禁私有方法（架构约束），因此取值逻辑必须外置；
 * 单独成类也让它可以被单独测试，宿主还可以直接覆盖这个 Bean 来定制取值。</p>
 *
 * <p>取值全部来自<strong>服务端</strong>：UA 取请求头、IP 取对端地址（是否信任转发头可配）、
 * 链路 ID 沿用网关注入的 {@code X-Request-Id}（缺失时生成一个）、身份取宿主提供的
 * {@link TrackIdentityProvider}。</p>
 *
 * <p>本类依赖当前请求上下文（{@code RequestContextHolder}），因此**只能在请求线程上调用**；
 * 非请求线程（后端切面、IoT 适配器）应使用 {@link TrackRequestContext#EMPTY}。</p>
 *
 * @author wenbin
 * @since 2026-09-15
 */
public class TrackRequestContextResolver {

    private static final Logger log = LoggerFactory.getLogger(TrackRequestContextResolver.class);

    /** 网关注入的链路 ID 请求头 */
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    /** {@code RequestUtils} 在取不到值时的占位串 */
    private static final String UNKNOWN = "unknown";

    private final boolean trustForwarded;

    private final TrackIdentityProvider identityProvider;

    /**
     * 创建解析器。
     *
     * @param trustForwarded   是否信任反向代理注入的转发头。部署在网关之后时为 {@code true}，
     *                         否则取到的永远是网关自身的地址
     * @param identityProvider 登录身份提供者（宿主实现；缺省不提供身份维度）
     */
    public TrackRequestContextResolver(boolean trustForwarded, TrackIdentityProvider identityProvider) {
        this.trustForwarded = trustForwarded;
        this.identityProvider = identityProvider;
    }

    /**
     * 解析当前请求的采集上下文。
     *
     * @return 上下文；无请求上下文时各项为空
     */
    public TrackRequestContext resolve() {
        return new TrackRequestContext(
            normalize(RequestUtils.getClientIp(trustForwarded)),
            normalize(RequestUtils.getUserAgent()),
            RequestIdUtils.sanitizeOrGenerate(RequestUtils.getHeader(REQUEST_ID_HEADER)),
            resolveUserId(),
            resolveTenantId());
    }

    /**
     * 身份取值失败时不抛异常、只记 null：埋点是旁路能力，身份实现的故障不该让整批上报失败。
     *
     * <p>这不是「静默降级」——失败会打一条带堆栈的告警，事件缺少用户/租户维度是可见的。</p>
     */
    private @Nullable Long resolveUserId() {
        try {
            return identityProvider.userId();
        } catch (RuntimeException ex) {
            log.warn("[ypbin-starter] tracking identity provider failed to resolve userId, "
                + "event will be stored without user dimension.", ex);
            return null;
        }
    }

    private @Nullable Long resolveTenantId() {
        try {
            return identityProvider.tenantId();
        } catch (RuntimeException ex) {
            log.warn("[ypbin-starter] tracking identity provider failed to resolve tenantId, "
                + "event will be stored without tenant dimension.", ex);
            return null;
        }
    }

    private @Nullable String normalize(@Nullable String value) {
        // RequestUtils 取不到值时会返回 "unknown" 占位串，这里归一为 null，
        // 避免把 "unknown" 当成真实 IP/UA 存进库里
        return value == null || value.isBlank() || UNKNOWN.equalsIgnoreCase(value) ? null : value;
    }
}

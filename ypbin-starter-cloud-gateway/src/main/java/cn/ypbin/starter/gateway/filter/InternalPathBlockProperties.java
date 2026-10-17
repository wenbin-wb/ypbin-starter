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
package cn.ypbin.starter.gateway.filter;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 内部端点路径阻断配置项（{@code ypbin.gateway.internal-path-block.*}）。
 *
 * <p><strong>解决的问题</strong>：服务侧的 {@code /internal/**} 端点（跨服务查询、密钥下发等）
 * 只应由服务间直连调用。<b>不经网关</b>访问是这类端点的隐含前提，但网关默认对任何已注册路由
 * 一视同仁——只要路由前缀把 {@code /internal/**} 暴露出去（如 {@code /iot/**} 路由 + 下游
 * {@code /internal/**}），外部调用方就能凭一个普通登录态把它打通。服务侧守卫
 * （{@code ypbin.security.internal-token.guard-enabled}）虽能拦住无凭证请求，但那是<b>最后一道</b>
 * 防线；网关层应尽早阻断，避免内部端点路径对外可见（暴露内部 API 面）。</p>
 *
 * <p><strong>默认开启（安全默认）</strong>：与 {@link cn.ypbin.starter.gateway.ratelimit.RateLimitProperties}
 * 「默认关闭」的取舍相反——限流是额外能力，业务方不知情时不应被限流；而内部端点<b>不该</b>经网关
 * 暴露给外网，不拦才是需要解释的例外，故默认 {@code enabled=true}，并留显式关闭开关。</p>
 *
 * <p><strong>路径模式语义（以 Spring Framework 7.0.9 实测为准，非 Ant 假定）</strong>：</p>
 * <ul>
 *     <li>普通模式（如 {@code /internal/**}）用 WebFlux 的
 *     {@link org.springframework.web.util.pattern.PathPattern} 语义匹配（与 Gateway 路由谓词同口径）；</li>
 *     <li>但 {@code PathPattern} <b>不允许出现两个 {@code **}</b>（实测：把默认模式
 *     {@link #DEFAULT_PATH_PATTERN} 直接交给 {@code PathPatternParser} 会抛
 *     {@code PatternParseException: Multiple {*...} or ** pattern elements are not allowed}），
 *     而「任意层级前缀 + {@code /internal/**}」恰恰需要两个 {@code **}。故本配置约定：
 *     <b>以「{@code /**} 后紧跟一个斜杠」开头的模式按「任意层级前缀」解释</b>——去掉该前缀后用
 *     {@code PathPattern} 编译剩余部分，再对请求路径的每个分段边界做后缀匹配
 *     （{@code /a/b/internal/x} 的后缀 {@code /internal/x} 命中 {@code /internal/**}），
 *     语义等价于 Ant 的「任意层级前缀」写法，且<b>同样覆盖顶层</b>
 *     {@code /internal/x}（实测后缀枚举命中）；</li>
 *     <li>匹配在 {@link org.springframework.http.server.PathContainer} 上做，故与路由匹配一致地
 *     处理百分号编码（{@code /%69nternal/x} 同样被拦，实测）与矩阵参数
 *     （{@code /internal/x;jsessionid=1} 同样被拦）；</li>
 *     <li>其余含多个 {@code **} 的写法（如 {@code /a/**}{@code /internal/**}）无法编译，
 *     <b>启动即失败</b>而不是静默跳过某条模式——安全过滤器的模式静默失效是本仓明令禁止的形态。</li>
 * </ul>
 *
 * <p>本类不用 Lombok（与 {@code GatewayProperties} 同口径）：显式 accessor。</p>
 *
 * @author wenbin
 * @since 2026-10-17
 */
@ConfigurationProperties(prefix = InternalPathBlockProperties.PREFIX)
public class InternalPathBlockProperties {

    /** 配置前缀（单一来源：{@code @ConfigurationProperties} 与 {@code @ConditionalOnProperty} 均引用本常量）。 */
    public static final String PREFIX = "ypbin.gateway.internal-path-block";

    /**
     * 默认路径模式：任意层级前缀下的 {@code /internal/**}（含顶层 {@code /internal/**}）。
     *
     * <p>该写法即上文「任意层级前缀」约定，实测覆盖 {@code /internal/x} 与 {@code /iot/internal/x}。</p>
     */
    public static final String DEFAULT_PATH_PATTERN = "/**/internal/**";

    /**
     * 默认未命中状态码 404（同 {@code HttpStatus.NOT_FOUND}）。
     *
     * <p>用 404 而非 403：403 等于告诉调用方「这个端点存在但你不能访问」，反而暴露内部 API 面。
     * 这里用具名常量而非裸数字；之所以不写成 {@code HttpStatus.NOT_FOUND.value()}，是因为方法调用
     * 不是编译期常量、配置元数据导出器解析不出默认值（实测导出的 {@code defaultValue} 会缺失）。</p>
     */
    public static final int DEFAULT_NOT_FOUND_STATUS = 404;

    /** 是否启用内部端点路径阻断，默认 true（安全默认：内部端点不应经网关对外暴露）。 */
    private boolean enabled = true;

    /** 生效路径模式清单，默认任意层级前缀下的 /internal/**（含顶层）。 */
    private List<String> pathPatterns = new ArrayList<>(List.of(DEFAULT_PATH_PATTERN));

    /** 命中时写回的 HTTP 状态码，默认 404（用 404 而非 403，避免暴露内部端点存在性）。 */
    private int notFoundStatus = DEFAULT_NOT_FOUND_STATUS;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getPathPatterns() {
        return pathPatterns;
    }

    /**
     * 设置生效路径模式；{@code null} 归一化为空清单（等于不拦任何路径），避免装配期 NPE。
     *
     * @param pathPatterns 路径模式清单，可为 {@code null}
     */
    public void setPathPatterns(@Nullable List<String> pathPatterns) {
        this.pathPatterns = pathPatterns == null ? new ArrayList<>() : pathPatterns;
    }

    public int getNotFoundStatus() {
        return notFoundStatus;
    }

    public void setNotFoundStatus(int notFoundStatus) {
        this.notFoundStatus = notFoundStatus;
    }
}

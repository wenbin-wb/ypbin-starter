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
package cn.ypbin.starter.security.internal;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 内部调用凭证守卫配置项（{@code ypbin.security.internal-token.*}）。
 *
 * <p><strong>解决的问题</strong>：服务间 Feign 直连（不经网关）访问 {@code /internal/**} 内部端点时，
 * 这些端点对"任意登录用户经网关转发"即可访问——网关只校验登录态，{@code /internal/**} 不在其权限
 * 收口范围内（跨租户检索、密钥明文出网与口令爆破风险）。本守卫把"登录可访问"收口为"必须持有内部
 * 凭证"，且两种拒绝语义都保留（见下）。</p>
 *
 * <p><strong>默认关闭</strong>：{@code guard-enabled} 默认为 {@code false}，宿主显式开启。
 * 与 {@link cn.ypbin.starter.security.management.ManagementEndpointProperties} 同一约定——
 * 不装配即完全不改动既有宿主行为。</p>
 *
 * <p><strong>两种拒绝语义必须并存（不是二选一）</strong>：真实宿主两种语义同时存在，因此本配置按
 * 「生效路径」而非「单一全局开关」区分语义，两种语义各有独立的路径清单：</p>
 * <ul>
 *     <li>{@link #envelopePathPatterns}（默认 {@code /internal/**}）→ <b>ENVELOPE</b>：抛业务异常，
 *     由全局异常处理器转 HTTP 200 + {@code R.code=401}，是浏览器/网关接口的既有统一约定；</li>
 *     <li>{@link #raw401PathPatterns}（默认空，即不启用）→ <b>RAW_401</b>：直接写原始 {@code 401}，
 *     供 EMQX HTTP 动作这类<b>只看状态码</b>的对端使用。EMQX 把 HTTP 200 判为「投递成功」，凭证错误
 *     的报文既不重试也不计入 {@code dropped.*} ⇒ 双端都绿而数据不存在（静默丢数据）。因此这条语义
 *     <b>不得</b>为了"统一"改成信封。</li>
 * </ul>
 *
 * <p>同一路径可同时出现在两份清单中：此时 RAW_401 过滤器先执行（进程内更早），失败即写 401；
 * 两者都通过才最终放行（纵深防御，与既有业务实现一致）。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
@ConfigurationProperties(prefix = InternalTokenProperties.PREFIX)
public class InternalTokenProperties {

    public static final String PREFIX = "ypbin.security.internal-token";

    /** 默认的 ENVELOPE 生效路径模式：{@code /internal/**}（与既有业务实现一致）。 */
    public static final String DEFAULT_ENVELOPE_PATH_PATTERN = "/internal/**";

    /**
     * 默认凭证请求头名：{@code X-Internal-Token}。
     *
     * <p>与凭证携带侧（Feign 请求拦截器）必须逐字一致，否则"携带了但守卫读不到"⇒ 全量拒绝；
     * 该默认值即两侧共享的历史约定，非必要不要改。</p>
     */
    public static final String DEFAULT_HEADER_NAME = "X-Internal-Token";

    /** 是否启用内部调用凭证守卫，默认关闭（显式开启，避免破坏未配置宿主） */
    private boolean guardEnabled = false;

    /**
     * 内部调用凭证（密钥，各服务共享同一值，部署时经环境变量注入，不落库）。
     *
     * <p><strong>fail-closed</strong>：开启守卫但未配置本项时，受保护路径<b>一律拒绝</b>
     * （未配置即无信任边界，严禁静默放行）。</p>
     */
    private String token = "";

    /** 凭证请求头名，默认 {@value #DEFAULT_HEADER_NAME}（改动需与携带侧同步） */
    private String headerName = DEFAULT_HEADER_NAME;

    /**
     * ENVELOPE 语义的生效路径（Ant 风格），默认 {@code /internal/**}；显式置空即不注册该语义。
     */
    private List<String> envelopePathPatterns = new ArrayList<>(List.of(DEFAULT_ENVELOPE_PATH_PATTERN));

    /**
     * RAW_401 语义的生效路径（Servlet 风格，如 {@code /internal/mqtt/*}），默认空即不注册该语义。
     *
     * <p>只在确属「对端仅看 HTTP 状态码」的入站端点（EMQX HTTP 动作等）上启用，不要泛化。</p>
     */
    private List<String> raw401PathPatterns = new ArrayList<>();

    public boolean isGuardEnabled() {
        return guardEnabled;
    }

    public void setGuardEnabled(boolean guardEnabled) {
        this.guardEnabled = guardEnabled;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public List<String> getEnvelopePathPatterns() {
        return envelopePathPatterns;
    }

    /**
     * 设置 ENVELOPE 生效路径；{@code null} 归一化为空清单（不注册该语义），避免装配期 NPE。
     *
     * @param envelopePathPatterns 路径模式清单，可为 {@code null}
     */
    public void setEnvelopePathPatterns(@Nullable List<String> envelopePathPatterns) {
        this.envelopePathPatterns =
            envelopePathPatterns == null ? new ArrayList<>() : envelopePathPatterns;
    }

    public List<String> getRaw401PathPatterns() {
        return raw401PathPatterns;
    }

    /**
     * 设置 RAW_401 生效路径；{@code null} 归一化为空清单（不注册该语义），避免装配期 NPE。
     *
     * @param raw401PathPatterns 路径模式清单，可为 {@code null}
     */
    public void setRaw401PathPatterns(@Nullable List<String> raw401PathPatterns) {
        this.raw401PathPatterns = raw401PathPatterns == null ? new ArrayList<>() : raw401PathPatterns;
    }
}

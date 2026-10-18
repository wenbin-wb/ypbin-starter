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
package cn.ypbin.starter.cloud.autoconfigure;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * 微服务「下游服务」默认属性注入器。
 *
 * <p><b>要解决的问题</b>：微服务架构下，下游服务（被网关/上游 Feign 调用的业务服务）的身份来自
 * 网关注入的身份头，本身没有 Sa-Token 会话。于是每个下游服务都得在 {@code application.yml} 里
 * 重复抄同一段配置——漏抄的后果不显眼但都很严重。本注入器把其中<b>方向安全</b>的那部分下沉到
 * starter，业务显式配置仍然优先。</p>
 *
 * <p><b>安全红线：默认绝不把安全开关改宽松</b>。本类默认注入的每一项都必须是
 * 「更严格 / 更 fail-closed」的方向，否则默认值本身就是一次静默的安全面收窄。
 * 这条红线直接决定了下面两项的取舍。</p>
 *
 * <h2>一、{@code ypbin.tenant.fail-on-missing-tenant=true}：默认注入</h2>
 *
 * <p>它把行为<b>收紧</b>到 fail-closed（无租户上下文时宁可调用失败，也不执行可能跨租户的查询），
 * 方向安全，故默认注入。当前该值与 tenant 模块自身默认一致，在此显式声明是为了让「下游语义」在
 * 配置层面可见，且不被其它实现改动悄悄放松。</p>
 *
 * <h2>二、{@code ypbin.security.interceptor=false}：显式 opt-in，默认不注入</h2>
 *
 * <p><b>为什么不能默认注入</b>：这一项是<b>把安全校验关掉</b>（关闭全局登录拦截，
 * 即不再执行 {@code StpUtil.checkLogin()}）。默认注入等于默认放宽安全面，违反 fail-closed。
 * 更关键的是<b>「下游服务」没有可靠的自动判据</b>：{@code ypbin-auth} 这类既有服务依赖
 * {@code cloud-core}，但它<b>有自己的用户会话</b>，且其 {@code deploy/nacos} 配置里
 * <b>并没有</b>显式声明 {@code ypbin.security.interceptor}——若本类默认注入 {@code false}，
 * 升级后 auth 的 {@code /user/info}、{@code /codes}、{@code /menu/all}、{@code /check}、
 * {@code /social/*} 等端点（均未标注 {@code @SaCheckLogin}）会<b>静默失去</b>全局登录校验。
 * 那是升级即引入的越权风险，不可接受。既然无法可靠区分「无会话的下游服务」与「有会话的 auth」，
 * 就只能由宿主显式声明——它自己最清楚自己有没有 Sa-Token 会话。</p>
 *
 * <p><b>如何启用</b>：下游服务在配置中声明
 * {@code ypbin.downstream.security-interceptor-disabled=true}，本类才会注入
 * {@code ypbin.security.interceptor=false}。注意即便启用，注解鉴权
 * （{@code ypbin.security.annotation-check}）依然默认开启，权限校验不被削弱。</p>
 *
 * <p><b>为什么用 {@link EnvironmentPostProcessor} 而不用自动配置</b>：这些配置项由其它 starter
 * 模块的 {@code @ConditionalOnProperty} / 配置绑定在<b>自动配置阶段之前</b>就要参与判断，
 * 属性必须在 Environment 就绪后、容器刷新前完成注入。{@code EnvironmentPostProcessor} 是 Boot
 * 官方提供的唯一这一时机扩展点。本仓约定「禁止用 {@code spring.factories} 注册自动配置」，
 * <b>但 {@code EnvironmentPostProcessor} 没有其它注册途径</b>（它不是自动配置，无法放入
 * {@code AutoConfiguration.imports}），因此在 {@code spring.factories} 中注册是本类的唯一选择，
 * 该例外已在 CHANGELOG 说明。</p>
 *
 * <p><b>为什么是「最低优先级属性源 + 逐项 {@code containsProperty} 判断」</b>：
 * {@code addLast} 保证业务 {@code application.yml}/{@code application-*.yml}/Nacos/命令行参数
 * 中的同名项全部优先；而逐项判断让「业务只覆盖其中一项」时另一项仍能用默认值
 * （若整源存在即跳过，就会因为业务配了一项而丢失另一项默认）。</p>
 *
 * <p><b>关于 {@code ypbin.cloud.feign.trusted-source-token}：本类刻意不给它任何默认值。</b>
 * 那是<b>真实凭据</b>（下游用它校验请求来自可信上游），默认值意味着所有宿主共享同一把密钥、
 * 且密钥进入源码仓库——等同于没有校验。凭据只能由部署环境通过配置中心/环境变量注入。</p>
 *
 * <p><b>总开关</b>：{@code ypbin.downstream.defaults-enabled}（默认 {@code true}）。
 * 单体应用或不需要这些默认值的宿主可显式关闭。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
public class DownstreamDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "ypbinDownstreamDefaults";

    /** 总开关：默认开启。 */
    public static final String ENABLED_PROPERTY = "ypbin.downstream.defaults-enabled";

    /**
     * 安全拦截器的显式 opt-in 开关：默认 {@code false}。
     *
     * <p>只有宿主声明本项为 {@code true}，才会注入 {@code ypbin.security.interceptor=false}。
     * 默认 false 是刻意的：把安全开关<b>默认改宽</b>违反 fail-closed，而「下游服务」这个身份
     * 没有可靠自动判据（{@code ypbin-auth} 也依赖 cloud-core，却有真实用户会话）。
     * 详见类注释。</p>
     */
    public static final String SECURITY_INTERCEPTOR_DISABLED_OPT_IN =
        "ypbin.downstream.security-interceptor-disabled";

    /** 安全模块的全局登录拦截开关（注入目标；本类只会在显式 opt-in 时写入）。 */
    public static final String SECURITY_INTERCEPTOR_PROPERTY = "ypbin.security.interceptor";

    /** 租户模块的「缺租户上下文即失败」开关（fail-closed 方向，默认注入）。 */
    public static final String TENANT_FAIL_ON_MISSING_PROPERTY = "ypbin.tenant.fail-on-missing-tenant";

    /**
     * 默认注入项：只有方向安全（fail-closed / 更严格）的配置才允许放在这里。
     *
     * <p>{@code ypbin.tenant.fail-on-missing-tenant=true} 让「无租户上下文」从「可能放行查询」
     * 变成「明确失败」，避免跨租户数据泄露，方向安全故默认注入。</p>
     */
    private static final Map<String, Object> SAFE_DEFAULTS = safeDefaults();

    /** 仅在显式 opt-in 后注入项（会放宽安全校验，绝不默认启用）。 */
    private static final Map<String, Object> OPT_IN_DEFAULTS = optInDefaults();

    private static Map<String, Object> safeDefaults() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put(TENANT_FAIL_ON_MISSING_PROPERTY, "true");
        return Map.copyOf(defaults);
    }

    private static Map<String, Object> optInDefaults() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put(SECURITY_INTERCEPTOR_PROPERTY, "false");
        return Map.copyOf(defaults);
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }
        if (!environment.getProperty(ENABLED_PROPERTY, Boolean.class, true)) {
            return;
        }
        Map<String, Object> defaults = new LinkedHashMap<>();
        collectMissing(environment, SAFE_DEFAULTS, defaults);
        // 安全开关只在宿主显式声明 opt-in 后才注入：默认放宽安全面违反 fail-closed
        if (environment.getProperty(SECURITY_INTERCEPTOR_DISABLED_OPT_IN, Boolean.class, false)) {
            collectMissing(environment, OPT_IN_DEFAULTS, defaults);
        }
        if (defaults.isEmpty()) {
            return;
        }
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
    }

    /**
     * 把「业务尚未显式配置」的项收集到 {@code target}。
     *
     * <p>逐项判断而不是「整源存在即跳过」：业务只覆盖其中一项时，另一项仍应拿到默认值。</p>
     *
     * @param environment 环境
     * @param candidates  候选默认值
     * @param target      收集目标
     */
    private static void collectMissing(ConfigurableEnvironment environment,
                                       Map<String, Object> candidates,
                                       Map<String, Object> target) {
        for (Map.Entry<String, Object> entry : candidates.entrySet()) {
            if (!environment.containsProperty(entry.getKey())) {
                target.put(entry.getKey(), entry.getValue());
            }
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}

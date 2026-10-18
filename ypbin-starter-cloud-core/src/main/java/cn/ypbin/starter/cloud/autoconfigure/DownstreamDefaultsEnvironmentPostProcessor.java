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
 * 网关注入的身份头，本身没有 Sa-Token 会话，也不需要租户上下文缺失时放行。于是每个下游服务都得在
 * {@code application.yml} 里重复抄同一段配置——漏抄的后果分别是「所有接口 401」和「越权访问其他租户
 * 数据」，都是安全相关且不显眼的错误。本注入器把这两项<b>下游恒成立</b>的默认值下沉到 starter，
 * 业务显式配置仍然优先。</p>
 *
 * <p><b>为什么用 {@link EnvironmentPostProcessor} 而不用自动配置</b>：这两项配置（
 * {@code ypbin.security.interceptor} / {@code ypbin.tenant.fail-on-missing-tenant}）由其它 starter
 * 模块的 {@code @ConditionalOnProperty} / 配置绑定在<b>自动配置阶段之前</b>就要参与判断，
 * 属性必须在 Environment 就绪后、容器刷新前完成注入。{@code EnvironmentPostProcessor} 是 Boot 官方
 * 提供的唯一这一时机扩展点。本仓约定「禁止用 {@code spring.factories} 注册自动配置」，
 * <b>但 {@code EnvironmentPostProcessor} 没有其它注册途径</b>（它不是自动配置，无法放入
 * {@code AutoConfiguration.imports}），因此在 {@code spring.factories} 中注册是本类的唯一选择，
 * 该例外已在 CHANGELOG 说明。</p>
 *
 * <p><b>为什么是「最低优先级属性源 + 逐项 {@code containsKey} 判断」</b>：
 * {@code addLast} 保证业务 {@code application.yml}/{@code application-*.yml}/Nacos/命令行参数
 * 中的同名项全部优先；而逐项判断让「业务只覆盖其中一项」时另一项仍能用默认值
 * （若整源存在即跳过，就会因为业务配了一项而丢失另一项默认）。</p>
 *
 * <p><b>关于 {@code ypbin.cloud.feign.trusted-source-token}：本类刻意不给它任何默认值。</b>
 * 那是<b>真实凭据</b>（下游用它校验请求来自可信上游），默认值意味着所有宿主共享同一把密钥、
 * 且密钥进入源码仓库——等同于没有校验。凭据只能由部署环境通过配置中心/环境变量注入。</p>
 *
 * <p><b>开关</b>：{@code ypbin.downstream.defaults-enabled}（默认 {@code true}）。
 * 单体应用或不需要这些默认值的宿主可显式关闭。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
public class DownstreamDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "ypbinDownstreamDefaults";

    /** 总开关：默认开启（下游服务是最常见部署形态）。 */
    public static final String ENABLED_PROPERTY = "ypbin.downstream.defaults-enabled";

    /**
     * 下游服务默认值。
     *
     * <p>逐项说明为何「下游恒成立」：</p>
     * <ul>
     *   <li>{@code ypbin.security.interceptor=false}：下游没有 Sa-Token 会话，登录态校验
     *       （{@code StpUtil.checkLogin()}）必然失败；身份由网关校验并注入身份头，
     *       注解鉴权（{@code ypbin.security.annotation-check}）仍默认开启，权限校验不被削弱；</li>
     *   <li>{@code ypbin.tenant.fail-on-missing-tenant=true}：宁可调用失败也不在无租户上下文时
     *       执行查询（fail-closed），避免跨租户数据泄露。该值当前与 tenant 模块默认一致，
     *       在此显式声明是为了让「下游语义」在配置层面可见，且不受其它实现改动影响。</li>
     * </ul>
     */
    private static final Map<String, Object> DOWNSTREAM_DEFAULTS = downstreamDefaults();

    private static Map<String, Object> downstreamDefaults() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("ypbin.security.interceptor", "false");
        defaults.put("ypbin.tenant.fail-on-missing-tenant", "true");
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
        for (Map.Entry<String, Object> entry : DOWNSTREAM_DEFAULTS.entrySet()) {
            if (!environment.containsProperty(entry.getKey())) {
                defaults.put(entry.getKey(), entry.getValue());
            }
        }
        if (defaults.isEmpty()) {
            return;
        }
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}

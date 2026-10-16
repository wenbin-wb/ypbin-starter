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
package cn.ypbin.starter.core.autoconfigure;

import cn.ypbin.starter.core.context.ContextAwareTaskDecorator;
import cn.ypbin.starter.core.context.ContextPropagator;
import cn.ypbin.starter.core.diagnostic.StarterDiagnosticEndpoint;
import cn.ypbin.starter.core.diagnostic.StarterDiagnosticsProperties;
import cn.ypbin.starter.core.diagnostic.StarterStartupSummary;
import cn.ypbin.starter.core.util.SpringUtils;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.core.task.TaskDecorator;

/**
 * 核心模块自动配置。
 *
 * <p>注册跨模块共用的基础设施 Bean：{@link SpringUtils} 与上下文透传装饰器
 * {@link ContextAwareTaskDecorator}。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@AutoConfiguration
@EnableConfigurationProperties(StarterDiagnosticsProperties.class)
public class CoreAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CoreAutoConfiguration.class);

    /**
     * Spring 上下文静态持有工具。
     *
     * <p>标记为基础设施角色，避免被误认为业务 Bean 参与自动装配报告。</p>
     */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public SpringUtils springUtils() {
        return new SpringUtils();
    }

    /**
     * 上下文透传任务装饰器。
     *
     * <p>收集容器内所有 {@link ContextPropagator}，让异步线程能还原主线程的租户/用户/MDC 等上下文。
     * 业务方将其设置到自定义线程池的 {@code setTaskDecorator} 即可生效；未自定义线程池时，
     * 也可复用本 Bean。</p>
     */
    @Bean
    @ConditionalOnMissingBean(TaskDecorator.class)
    public TaskDecorator contextAwareTaskDecorator(ObjectProvider<ContextPropagator<?>> propagators) {
        List<ContextPropagator<?>> list = propagators.orderedStream().toList();
        log.debug("[ypbin-starter] context-aware task decorator initialized with {} propagator(s).", list.size());
        return new ContextAwareTaskDecorator(list);
    }

    public CoreAutoConfiguration() {
        log.debug("[ypbin-starter] core auto-configuration initialized.");
    }

    /**
     * 启动自省清单：应用就绪时打印一行「已激活的 ypbin starter 模块」。
     *
     * <p>开关为 {@code ypbin.core.diagnostics.startup-summary-enabled}（默认开启；显式 false 时本 Bean
     * 不注册，日志完全安静）。实现与取舍见 {@link StarterStartupSummary}。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = StarterDiagnosticsProperties.PREFIX, name = "startup-summary-enabled",
        havingValue = "true", matchIfMissing = true)
    public StarterStartupSummary starterStartupSummary() {
        return new StarterStartupSummary();
    }

    /**
     * Actuator 自诊断端点配置。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(Endpoint.class)
    static class ActuatorDiagnosticConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public StarterDiagnosticEndpoint starterDiagnosticEndpoint(ApplicationContext applicationContext) {
            return new StarterDiagnosticEndpoint(applicationContext);
        }
    }
}

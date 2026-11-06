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
package cn.ypbin.starter.security.platform;

import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * 平台访问控制自动配置。
 *
 * <p>默认装配 fail-closed 的 {@link PlatformUserChecker}（判定恒为否，业务方实现后自动覆盖），
 * 并装配 {@link PlatformAccessAspect} 切面与其 {@link PlatformAccessPointcut} Advisor。
 * 可通过 {@code ypbin.security.platform.enabled} 关闭（默认开启）。</p>
 *
 * <p><strong>为何显式注册 Advisor 而不依赖 {@code @Aspect} 自动织入</strong>：{@code @within}
 * 表达式在 JDK 动态代理下只认目标类，注解挂在接口上时会静默不织入（平台资源放开）。
 * 自定义切点按「实现方法 → 目标类 → 接口方法 → 接口类」解析注解，两种代理模式一致。</p>
 *
 * @author wenbin
 * @since 2026-09-01
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = PlatformAccessAutoConfiguration.PREFIX, name = "enabled", havingValue = "true",
    matchIfMissing = true)
// Servlet 专属（平台访问切面拦截 Controller）：WebFlux 应用（网关）不装配
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class PlatformAccessAutoConfiguration {

    /**
     * 配置前缀。
     *
     * <p>本能力只有装配开关、没有绑定字段，故前缀常量就近定义在本自动配置类内。
     */
    public static final String PREFIX = "ypbin.security.platform";

    @Bean
    @ConditionalOnMissingBean
    public PlatformUserChecker platformUserChecker() {
        return new PlatformUserChecker() {
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public PlatformAccessAspect platformAccessAspect(PlatformUserChecker checker) {
        return new PlatformAccessAspect(checker);
    }

    /**
     * 把平台访问切面挂到容器上。按 Bean 名做条件：宿主要替换切面语义时应自行提供同名 Advisor，
     * 而不是被「任意一个 Advisor」挤掉。
     *
     * @param aspect 平台访问切面
     * @return 切点 Advisor
     */
    @Bean
    @ConditionalOnMissingBean(name = "platformAccessAdvisor")
    public Advisor platformAccessAdvisor(PlatformAccessAspect aspect) {
        return new DefaultPointcutAdvisor(new PlatformAccessPointcut(), aspect);
    }
}

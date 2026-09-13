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
package cn.ypbin.starter.arch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.core.io.support.SpringFactoriesLoader;

/**
 * 装配注册的「运行时可见性」验证。
 *
 * <p>源码扫描只能证明注册键写对了，不能证明 Spring Boot 真的能找到这些实现。本测试直接用 Boot 使用的
 * {@link SpringFactoriesLoader} 加载 classpath 上全部 {@code META-INF/spring.factories}，断言各模块的
 * {@link EnvironmentPostProcessor} 实现确实被发现——若注册键仍在废弃的
 * {@code org.springframework.boot.env.EnvironmentPostProcessor} 下，Boot 4.1 虽然还能兜底加载，
 * 但一旦移除就静默失效（默认配置项不再注入），本测试会立刻失败。</p>
 *
 * @author wenbin
 * @since 2026-09-13
 */
class RegistrationDiscoveryTest {

    /** 各模块必须被 Boot 发现的 EnvironmentPostProcessor 实现 */
    private static final List<String> EXPECTED_ENVIRONMENT_POST_PROCESSORS = List.of(
        "cn.ypbin.starter.apidoc.autoconfigure.ApiDocDefaultsEnvironmentPostProcessor",
        "cn.ypbin.starter.web.autoconfigure.WebDefaultsEnvironmentPostProcessor",
        "cn.ypbin.starter.data.autoconfigure.DataDefaultsEnvironmentPostProcessor",
        "cn.ypbin.starter.messaging.autoconfigure.SmsDefaultsEnvironmentPostProcessor",
        "cn.ypbin.starter.cloud.autoconfigure.FeignDefaultsEnvironmentPostProcessor",
        "cn.ypbin.starter.loadbalancer.autoconfigure.LoadBalancerEnvironmentPostProcessor",
        "cn.ypbin.starter.nacos.autoconfigure.NacosEnvironmentPostProcessor");

    @Test
    @DisplayName("EnvironmentPostProcessor 必须能被 SpringFactoriesLoader 发现")
    void environmentPostProcessorsShouldBeDiscoverable() {
        // 宽松失败处理：classpath 上还有框架与第三方注册的 EP（部分依赖 DeferredLogFactory 等构造参数，
        // 本测试不提供 ArgumentResolver），它们的实例化失败不应影响对 ypbin 自身注册结果的判断。
        List<String> failures = new ArrayList<>();
        List<String> discovered = SpringFactoriesLoader.forDefaultResourceLocation()
            .load(EnvironmentPostProcessor.class,
                SpringFactoriesLoader.ArgumentResolver.none(),
                (factoryType, factoryName, failure) -> failures.add(factoryName))
            .stream()
            .map(processor -> processor.getClass().getName())
            .toList();

        assertThat(discovered)
            .as("注册键必须是 org.springframework.boot.EnvironmentPostProcessor，且类名与实现一致")
            .containsAll(EXPECTED_ENVIRONMENT_POST_PROCESSORS);
        assertThat(failures)
            .as("ypbin 的 EnvironmentPostProcessor 必须能无参实例化（否则注册了也装配不上）")
            .noneMatch(name -> name.startsWith("cn.ypbin."));
    }
}

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
package cn.ypbin.starter.loadbalancer.autoconfigure;

import cn.ypbin.starter.loadbalancer.core.VersionRequestContextResolver;
import cn.ypbin.starter.loadbalancer.core.VersionServiceInstanceChooser;
import cn.ypbin.starter.loadbalancer.core.VersionServiceInstanceLoadBalancer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClientConfiguration;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClientSpecification;
import org.springframework.cloud.loadbalancer.core.ReactorLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * 版本灰度负载均衡自动配置。
 *
 * @author wenbin
 * @since 2026-07-31
 */
@AutoConfiguration
@AutoConfigureBefore(LoadBalancerClientConfiguration.class)
@ConditionalOnClass(ReactorLoadBalancer.class)
@ConditionalOnProperty(prefix = LoadBalancerProperties.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(LoadBalancerProperties.class)
public class LoadBalancerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public VersionRequestContextResolver versionRequestContextResolver(LoadBalancerProperties properties) {
        return new VersionRequestContextResolver(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public VersionServiceInstanceChooser versionServiceInstanceChooser(LoadBalancerProperties properties) {
        return new VersionServiceInstanceChooser(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ReactorLoadBalancer<ServiceInstance> reactorServiceInstanceLoadBalancer(
        Environment environment,
        LoadBalancerClientFactory loadBalancerClientFactory,
        VersionRequestContextResolver requestContextResolver,
        VersionServiceInstanceChooser chooser) {
        String name = environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME);
        ObjectProvider<ServiceInstanceListSupplier> supplierProvider =
            loadBalancerClientFactory.getLazyProvider(name, ServiceInstanceListSupplier.class);
        return new VersionServiceInstanceLoadBalancer(supplierProvider, requestContextResolver, chooser);
    }

    @Bean
    @ConditionalOnMissingBean(name = "ypbinLoadBalancerClientSpecification")
    public LoadBalancerClientSpecification ypbinLoadBalancerClientSpecification() {
        return new LoadBalancerClientSpecification(
            "default.ypbinLoadBalancerConfiguration", new Class[] {LoadBalancerAutoConfiguration.class});
    }
}

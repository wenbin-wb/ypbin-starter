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
package cn.ypbin.starter.loadbalancer.core;

import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.DefaultResponse;
import org.springframework.cloud.client.loadbalancer.EmptyResponse;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.loadbalancer.core.NoopServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import reactor.core.publisher.Mono;

/**
 * 版本灰度负载均衡器。
 *
 * @author wenbin
 * @since 2026-07-31
 */
public class VersionServiceInstanceLoadBalancer implements ReactorServiceInstanceLoadBalancer {

    private final ObjectProvider<ServiceInstanceListSupplier> supplierProvider;

    private final VersionRequestContextResolver requestContextResolver;

    private final VersionServiceInstanceChooser chooser;

    public VersionServiceInstanceLoadBalancer(
        ObjectProvider<ServiceInstanceListSupplier> supplierProvider,
        VersionRequestContextResolver requestContextResolver,
        VersionServiceInstanceChooser chooser) {
        this.supplierProvider = supplierProvider;
        this.requestContextResolver = requestContextResolver;
        this.chooser = chooser;
    }

    @Override
    public Mono<Response<ServiceInstance>> choose(Request request) {
        ServiceInstanceListSupplier supplier = supplierProvider.getIfAvailable(NoopServiceInstanceListSupplier::new);
        return supplier.get(request).next()
            .map(instances -> response(instances, request));
    }

    private Response<ServiceInstance> response(List<ServiceInstance> instances, Request request) {
        ServiceInstance instance = chooser.choose(instances, requestContextResolver.resolve(request));
        return instance == null ? new EmptyResponse() : new DefaultResponse(instance);
    }
}

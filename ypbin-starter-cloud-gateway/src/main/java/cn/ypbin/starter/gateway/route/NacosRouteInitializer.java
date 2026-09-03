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
package cn.ypbin.starter.gateway.route;

import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import java.util.List;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Nacos 动态路由初始化器。
 *
 * <p>应用启动时从 Nacos 配置中心加载 Gateway 路由定义（JSON 数组格式），并注册配置变更监听器。
 * 当 Nacos 配置变更时自动清理旧路由、写入新路由并发布 {@link RefreshRoutesEvent}。
 * Json 解析失败时保留当前路由不回退任何路由，避免因配置错误打挂网关。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
public class NacosRouteInitializer implements ApplicationRunner, ApplicationEventPublisherAware {

    private static final Logger log = LoggerFactory.getLogger(NacosRouteInitializer.class);

    private static final TypeReference<List<RouteDefinition>> ROUTE_LIST_TYPE = new TypeReference<>() {};

    private final ConfigService configService;

    private final NacosRouteProperties properties;

    private final ObjectMapper objectMapper;

    private final RouteDefinitionLocator routeDefinitionLocator;

    private final RouteDefinitionWriter routeDefinitionWriter;

    private ApplicationEventPublisher eventPublisher;

    private volatile String lastValidConfig;

    public NacosRouteInitializer(
        ConfigService configService,
        NacosRouteProperties properties,
        ObjectMapper objectMapper,
        RouteDefinitionLocator routeDefinitionLocator,
        RouteDefinitionWriter routeDefinitionWriter) {
        this.configService = configService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.routeDefinitionLocator = routeDefinitionLocator;
        this.routeDefinitionWriter = routeDefinitionWriter;
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.eventPublisher = applicationEventPublisher;
    }

    @Override
    public void run(ApplicationArguments args) {
        String dataId = properties.getDataId();
        String group = properties.getGroup();
        long timeoutMs = properties.getTimeoutMs();
        try {
            String config = configService.getConfig(dataId, group, timeoutMs);
            if (config != null && !config.isBlank()) {
                applyRoutes(config);
                lastValidConfig = config;
                log.info("[ypbin-starter] Nacos dynamic routes loaded from {} (group={}), {} routes applied.",
                    dataId, group, parseRoutes(config).size());
            } else {
                log.info("[ypbin-starter] Nacos config {} (group={}) is empty, keeping default routes.", dataId, group);
            }
        } catch (Exception e) {
            log.warn("[ypbin-starter] Failed to load routes from Nacos {} (group={}), keeping default routes.",
                dataId, group, e);
        }
        registerListener(dataId, group);
    }

    private void registerListener(String dataId, String group) {
        try {
            configService.addListener(dataId, group, new Listener() {
                @Override
                public void receiveConfigInfo(String configInfo) {
                    if (configInfo == null || configInfo.isBlank()) {
                        log.warn("[ypbin-starter] Nacos route config {} cleared, keeping current routes.", dataId);
                        return;
                    }
                    applyRoutes(configInfo);
                    lastValidConfig = configInfo;
                }

                @Override
                public Executor getExecutor() {
                    return null;
                }
            });
            log.debug("[ypbin-starter] Nacos config listener registered for {}.", dataId);
        } catch (Exception e) {
            log.warn("[ypbin-starter] Failed to register Nacos listener for {}.", dataId, e);
        }
    }

    void applyRoutes(String config) {
        List<RouteDefinition> newRoutes = parseRoutes(config);
        if (newRoutes == null) {
            log.error("[ypbin-starter] Nacos route config JSON parse failed, keeping current routes.");
            return;
        }
        // 先删旧路由再写入新路由，最后 publish RefreshRoutesEvent
        routeDefinitionLocator.getRouteDefinitions()
            .flatMap(rd -> routeDefinitionWriter.delete(Mono.just(rd.getId())))
            .collectList()
            .flatMapMany(unused -> Flux.fromIterable(newRoutes))
            .flatMap(rd -> routeDefinitionWriter.save(Mono.just(rd)))
            .collectList()
            .subscribe(
                result -> {
                    eventPublisher.publishEvent(new RefreshRoutesEvent(this));
                    log.info("[ypbin-starter] Nacos dynamic routes refreshed: {} routes.", newRoutes.size());
                },
                error -> log.error("[ypbin-starter] Failed to apply Nacos routes, keeping current routes.", error)
            );
    }

    private List<RouteDefinition> parseRoutes(String config) {
        try {
            return objectMapper.readValue(config, ROUTE_LIST_TYPE);
        } catch (JacksonException e) {
            return null;
        }
    }

    String currentValidConfig() {
        return lastValidConfig;
    }
}

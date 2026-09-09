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
import java.time.Duration;
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
 * Json 解析失败、配置为空或为合法空列表 {@code []} 时均保留当前路由（仅告警），避免因配置错误或
 * 误配把网关路由全量打空；多来源并发应用由内部串行化保证不交错。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
public class NacosRouteInitializer implements ApplicationRunner, ApplicationEventPublisherAware {

    private static final Logger log = LoggerFactory.getLogger(NacosRouteInitializer.class);

    private static final TypeReference<List<RouteDefinition>> ROUTE_LIST_TYPE = new TypeReference<>() {};

    /** 单批路由应用（delete-all→save-all）的超时上限（秒），防止路由存储挂起时无限阻塞 */
    private static final long ROUTE_APPLY_TIMEOUT_SECONDS = 10L;

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

    /**
     * 应用一批 Nacos 路由：先全量删除旧路由，再写入新路由，最后发布 {@link RefreshRoutesEvent}。
     *
     * <p><strong>串行化取舍</strong>：启动 {@link #run(ApplicationArguments)} 与 Nacos 监听回调可能并发触发，
     * 若两批 delete-all→save-all 异步交错会互相覆盖导致路由状态不一致。此处用 {@code synchronized} 包住
     * 整条流水线并阻塞等待其完成（路由写库为本地内存/Redis 操作，耗时可控），使每次应用严格串行、最终
     * 状态收敛到最后一次进入的配置；比「版本号丢弃过期批次」更简单——版本号方案仍须解决异步链间串行，
     * 复杂度不降反升。</p>
     *
     * <p>合法空列表（如 {@code []}）视为"清空意图不明确"，与 blank/解析失败策略一致：保留当前路由并告警，
     * 避免运维误配或中间态空配置导致网关路由全量丢失。需要显式清空全部路由的场景另行提供专门的清空入口。</p>
     *
     * @param config Nacos 配置内容（JSON 路由数组）
     */
    private synchronized void applyRoutes(String config) {
        List<RouteDefinition> newRoutes = parseRoutes(config);
        if (newRoutes == null) {
            log.error("[ypbin-starter] Nacos route config JSON parse failed, keeping current routes.");
            return;
        }
        if (newRoutes.isEmpty()) {
            // 空列表不清空：与 blank/解析失败一致保留当前路由，仅告警（防误清全量路由）
            log.warn("[ypbin-starter] Nacos route config is an empty list, keeping current routes.");
            return;
        }
        try {
            // block(Duration) 等整条 delete-all→save-all 流水线完成后再返回，保证与下一批应用互斥
            // 且不交错；带超时避免路由存储实现挂起时无限阻塞启动/监听线程
            routeDefinitionLocator.getRouteDefinitions()
                .flatMap(rd -> routeDefinitionWriter.delete(Mono.just(rd.getId())))
                .collectList()
                .flatMapMany(unused -> Flux.fromIterable(newRoutes))
                .flatMap(rd -> routeDefinitionWriter.save(Mono.just(rd)))
                .collectList()
                .block(Duration.ofSeconds(ROUTE_APPLY_TIMEOUT_SECONDS));
            eventPublisher.publishEvent(new RefreshRoutesEvent(this));
            log.info("[ypbin-starter] Nacos dynamic routes refreshed: {} routes.", newRoutes.size());
        } catch (Exception e) {
            // delete-all→save-all 非原子：中途失败时旧路由可能已被部分/全部删除，日志如实描述，
            // 提示检查路由存储状态，等待下一次配置推送或人工恢复，不做"仍在生效"的误导性声明
            log.error("[ypbin-starter] Failed to apply Nacos routes (may be partially applied/cleared). "
                + "Check route storage and Nacos config. routes={}", newRoutes.size(), e);
        }
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

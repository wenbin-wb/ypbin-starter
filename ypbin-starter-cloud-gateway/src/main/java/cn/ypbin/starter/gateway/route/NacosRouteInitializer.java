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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
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
import org.springframework.util.ObjectUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Nacos 动态路由初始化器。
 *
 * <p>应用启动时从 Nacos 配置中心加载 Gateway 路由定义（JSON 数组格式），并注册配置变更监听器。
 * 当 Nacos 配置变更时按「先写入新路由（同 id 覆盖）→ 再删除『旧有新无』的 id → 最后发布刷新事件」的
 * 原子增量方式生效，任何时刻网关内存中都保留一套完整可用路由。
 * Json 解析失败、配置为空或为合法空列表 {@code []} 时均保留当前路由（仅告警），避免因配置错误或
 * 误配把网关路由全量打空；多来源并发应用由内部串行化保证不交错。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
public class NacosRouteInitializer implements ApplicationRunner, ApplicationEventPublisherAware {

    private static final Logger log = LoggerFactory.getLogger(NacosRouteInitializer.class);

    private static final TypeReference<List<RouteDefinition>> ROUTE_LIST_TYPE = new TypeReference<>() {};

    /** 单批路由应用（增量 save→delete→发布事件）的超时上限（秒），防止路由存储挂起时无限阻塞 */
    private static final long ROUTE_APPLY_TIMEOUT_SECONDS = 10L;

    private final ConfigService configService;

    private final NacosRouteProperties properties;

    private final ObjectMapper objectMapper;

    private final RouteDefinitionLocator routeDefinitionLocator;

    private final RouteDefinitionWriter routeDefinitionWriter;

    @Nullable
    private ApplicationEventPublisher eventPublisher;

    @Nullable
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
                    dataId, group, parseRoutes(config).map(List::size).orElse(0));
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
                @Nullable
                public Executor getExecutor() {
                    // 返回 null 表示使用 Nacos 客户端默认线程池执行回调
                    return null;
                }
            });
            log.debug("[ypbin-starter] Nacos config listener registered for {}.", dataId);
        } catch (Exception e) {
            log.warn("[ypbin-starter] Failed to register Nacos listener for {}.", dataId, e);
        }
    }

    /**
     * 应用一批 Nacos 路由（原子增量）：先 {@code save} 全部新路由（同 id 覆盖），全部成功后
     * 再 {@code delete}「旧有新无」的 id，最后发布 {@link RefreshRoutesEvent}。
     *
     * <p><strong>为什么不是 delete-all→save-all</strong>：先清空再写入会留出「内存中一条路由都没有」的
     * 窗口（在途请求 404），且新路由任一条 {@code save} 失败时旧路由已全部删除 ⇒ 全站路由丢失。
     * 改为增量后，任何时刻内存中都保留一套完整可用路由：{@code save} 阶段只是覆盖/新增，旧路由仍在；
     * 只有全部 {@code save} 成功才会删除确实被下线的 id；{@code save} 失败则一条旧路由都不会被删除。</p>
     *
     * <p><strong>串行化取舍</strong>：启动 {@link #run(ApplicationArguments)} 与 Nacos 监听回调可能并发触发，
     * 若两批增量相互交错会互相覆盖导致路由状态不一致。此处用 {@code synchronized} 包住整条流水线并阻塞
     * 等待其完成（路由写库为本地内存/Redis 操作，耗时可控），使每次应用严格串行、最终状态收敛到最后一次
     * 进入的配置；比「版本号丢弃过期批次」更简单——版本号方案仍须解决异步链间串行，复杂度不降反升。</p>
     *
     * <p>合法空列表（如 {@code []}）视为"清空意图不明确"，与 blank/解析失败策略一致：保留当前路由并告警，
     * 避免运维误配或中间态空配置导致网关路由全量丢失。需要显式清空全部路由的场景另行提供专门的清空入口。</p>
     *
     * <p><strong>空 id 条目的处理</strong>：进入 {@code save} 流水线前先丢弃 id 为空的
     * {@link RouteDefinition} 并计数告警。真实 {@code InMemoryRouteDefinitionRepository.save} 对
     * {@code ObjectUtils.isEmpty(id)} 会直接以 {@code IllegalArgumentException("id may not be empty")}
     * 失败，一条坏条目会让整批新路由全部不生效（虽然 fail-safe 保证旧路由不会被删）。过滤后若一条有效路由
     * 都不剩，则按上面的「空列表」语义处理：保留当前路由并告警、不发刷新事件。{@code newRouteIds} 只由
     * 过滤后的有效路由构成，故「旧有新无」的删除判定不受被丢弃条目的影响。</p>
     *
     * @param config Nacos 配置内容（JSON 路由数组）
     */
    private synchronized void applyRoutes(String config) {
        Optional<List<RouteDefinition>> parsed = parseRoutes(config);
        if (parsed.isEmpty()) {
            log.error("[ypbin-starter] Nacos route config JSON parse failed, keeping current routes.");
            return;
        }
        List<RouteDefinition> parsedRoutes = parsed.get();
        if (parsedRoutes.isEmpty()) {
            // 空列表不清空：与 blank/解析失败一致保留当前路由，仅告警（防误清全量路由）
            log.warn("[ypbin-starter] Nacos route config is an empty list, keeping current routes.");
            return;
        }
        // id 为空的 RouteDefinition 必须在这里拦下：真实 InMemoryRouteDefinitionRepository.save 对
        // ObjectUtils.isEmpty(id) 会 Mono.error(IllegalArgumentException("id may not be empty"))，
        // 整批 save 因此中止（fail-safe 下旧路由不会删，但该批新路由也全部不生效）。旧路由列表早就用
        // Mono.justOrEmpty 滤掉 null id，新配置此前漏了同一道闸。显式丢弃并计数告警，绝不静默吞掉。
        List<RouteDefinition> newRoutes = parsedRoutes.stream()
            .filter(route -> !ObjectUtils.isEmpty(route.getId()))
            .toList();
        int droppedCount = parsedRoutes.size() - newRoutes.size();
        if (droppedCount > 0) {
            log.warn("[ypbin-starter] Nacos route config 中有 {} 条路由的 id 为空，已丢弃这些条目（真实路由存储"
                + "会以 IllegalArgumentException(\"id may not be empty\") 拒绝它们并中止整批）；剩余有效路由 {} 条，"
                + "继续应用，请检查配置中的 id 字段。", droppedCount, newRoutes.size());
        }
        if (newRoutes.isEmpty()) {
            // 全部条目的 id 都为空 ⇒ 过滤后没有任何有效新路由，按「空列表」既有语义处理：
            // 保留当前路由并告警，不发刷新事件（防止一份全空的配置把已有路由删空）
            log.warn("[ypbin-starter] Nacos route config 过滤空 id 后没有有效路由，保持当前路由不变。");
            return;
        }
        Set<String> newRouteIds = newRoutes.stream()
            .map(RouteDefinition::getId)
            .collect(Collectors.toSet());
        try {
            // 原子增量：先 save 全部新路由（同 id 覆盖），全部成功后才 delete「旧有新无」的 id，
            // 最后才发布刷新事件。没有「先清空」的窗口，save 失败时旧路由一条都不会被删除。
            // block(Duration) 等整条流水线完成后再返回，保证与下一批应用互斥且不交错；
            // 带超时避免路由存储实现挂起时无限阻塞启动/监听线程。
            routeDefinitionLocator.getRouteDefinitions()
                // 用 flatMap + justOrEmpty 而非 map：reactor 的 map 不允许映射出 null，
                // 而无 id 的 RouteDefinition 会让 map 直接抛 NPE 并中断整批应用
                .flatMap(rd -> Mono.justOrEmpty(rd.getId()))
                .collectList()
                .flatMapMany(oldRouteIds -> Flux.fromIterable(newRoutes)
                    .flatMap(rd -> routeDefinitionWriter.save(Mono.just(rd)))
                    .thenMany(Flux.fromIterable(oldRouteIds)
                        .filter(id -> !newRouteIds.contains(id))
                        .flatMap(id -> routeDefinitionWriter.delete(Mono.just(id)))))
                .then()
                .block(Duration.ofSeconds(ROUTE_APPLY_TIMEOUT_SECONDS));
            ApplicationEventPublisher publisher = eventPublisher;
            if (publisher == null) {
                // 不静默跳过：没有事件发布器意味着新路由不会生效，属装配错误，必须显式暴露
                throw new IllegalStateException("ApplicationEventPublisher 未注入，无法广播路由刷新事件");
            }
            publisher.publishEvent(new RefreshRoutesEvent(this));
            log.info("[ypbin-starter] Nacos dynamic routes refreshed: {} routes.", newRoutes.size());
        } catch (Exception e) {
            // 增量应用的失败状态可如实描述：delete 只发生在全部 save 成功之后，故旧路由一条都不会被删除、
            // 内存中仍保留一套可用路由；新路由可能已部分写入（同 id 已覆盖）。不做"完全未生效"或
            // "已被清空"的误导性声明。
            log.error("[ypbin-starter] Failed to apply Nacos routes incrementally: stopped before deleting stale "
                + "routes, so old routes are kept (some new routes may already be saved/overwritten). "
                + "Check route storage and Nacos config. routes={}", newRoutes.size(), e);
        }
    }

    /**
     * 解析路由 JSON。
     *
     * <p>解析失败（含 JSON 字面量 {@code null}）返回 {@link Optional#empty()} 而非 {@code null}：
     * 集合返回类型不得用 {@code null} 表达「无结果」（铁律），而这里的语义恰恰是「没解析出来 → 保留现有路由」，
     * 用 {@code Optional} 表达既满足规范又不动安全性语义。</p>
     *
     * @param config Nacos 配置内容（JSON 路由数组）
     * @return 解析出的路由；解析失败为空
     */
    private Optional<List<RouteDefinition>> parseRoutes(String config) {
        try {
            return Optional.ofNullable(objectMapper.readValue(config, ROUTE_LIST_TYPE));
        } catch (JacksonException e) {
            // 解析失败即「本次 Nacos 路由配置被整体忽略、继续沿用现有路由」：属可观测性关键失败，
            // 必须带完整堆栈（曾出现推送了非法 JSON 但日志里什么都看不到的情况）
            log.warn("[ypbin-starter] Nacos 路由配置解析失败，本次配置被忽略并继续沿用现有路由（请检查推送的 "
                + "JSON 是否为合法路由数组）", e);
            return Optional.empty();
        }
    }

    @Nullable
    String currentValidConfig() {
        return lastValidConfig;
    }
}

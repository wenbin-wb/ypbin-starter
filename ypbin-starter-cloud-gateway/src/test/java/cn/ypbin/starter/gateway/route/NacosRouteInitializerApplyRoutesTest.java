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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.util.ObjectUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link NacosRouteInitializer} 路由应用的<b>原子增量</b>回归测试。
 *
 * <p><b>背景（issue #83）</b>：原实现是 delete-all→save-all——清空与写入之间网关内存路由瞬间为空
 * （在途请求 404），且新路由任一条 {@code save} 失败时旧路由已全删 ⇒ 全站路由丢失。本用例锁定重构后的
 * 契约：先 save 全部新路由（同 id 覆盖），全部成功后再 delete「旧有新无」的 id，最后发布
 * {@link RefreshRoutesEvent}；任何时刻内存中都保留一套完整可用路由，save 失败时旧路由一条都不删。</p>
 *
 * <p><b>为什么用内存存储替身而不是 Mockito 验证调用</b>：本用例要断言的是<b>路由存储的状态演化</b>
 * （不出现空窗、失败后旧定义完好），而不只是「调用过哪个方法」。替身同时实现
 * {@link RouteDefinitionLocator} 与 {@link RouteDefinitionWriter}（与真实的
 * {@code InMemoryRouteDefinitionRepository} 同构），记录每次 save/delete 的先后顺序并跟踪是否出现过
 * 「一条路由都没有」的瞬间。</p>
 *
 * <p><b>空 id 契约（2026-10-16 补）</b>：真实 {@code InMemoryRouteDefinitionRepository.save} 对
 * {@code ObjectUtils.isEmpty(id)} 会 {@code Mono.error(IllegalArgumentException("id may not be empty"))}，
 * 一条空 id 条目即可中止整批新路由。故新配置中的空 id 必须在进入 save 流水线前被丢弃并告警；替身
 * {@code RecordingRouteStore#save} 按同一契约拒绝空 id，使缺了过滤的实现在本用例中必然失败（回归护栏）。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
class NacosRouteInitializerApplyRoutesTest {

    private static final String DATA_ID = "gateway-routes.json";

    private static final String GROUP = "DEFAULT_GROUP";

    private static final String EVENT_PREFIX = "event:";

    private final ConfigService configService = mock(ConfigService.class);

    private final NacosRouteProperties properties = new NacosRouteProperties();

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RecordingRouteStore store;

    private NacosRouteInitializer initializer;

    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        appender = new ListAppender<>();
        appender.start();
        initializerLogger().addAppender(appender);
        store = new RecordingRouteStore();
        initializer = new NacosRouteInitializer(configService, properties, objectMapper, store, store);
        initializer.setApplicationEventPublisher(
            event -> store.operations.add(EVENT_PREFIX + event.getClass().getName()));
    }

    @AfterEach
    void tearDown() {
        initializerLogger().detachAppender(appender);
    }

    private static Logger initializerLogger() {
        return (Logger) LoggerFactory.getLogger(NacosRouteInitializer.class);
    }

    private List<String> warnMessages() {
        return appender.list.stream()
            .filter(event -> event.getLevel() == Level.WARN)
            .map(ILoggingEvent::getFormattedMessage)
            .toList();
    }

    // ------------------------------------------------------------------ 原子增量

    @Test
    @DisplayName("★ 增量应用：先 save 全部新路由，再只 delete「旧有新无」的 id，事件最后发布")
    void incrementalApplySavesFirstThenDeletesOnlyStaleIds() throws Exception {
        store.seed(route("r1"), route("r2"));
        String config = routesJson("r2", "r3");

        apply(config);

        assertThat(store.savedIds()).as("新路由全部写入（r2 同 id 覆盖、r3 新增）")
            .containsExactlyInAnyOrder("r2", "r3");
        assertThat(store.deletedIds()).as("只删除「旧有新无」的 r1，r2 不得被删除").containsExactly("r1");
        assertThat(store.operations.indexOf("delete:r1"))
            .as("delete 必须发生在全部 save 完成之后")
            .isGreaterThan(store.operations.indexOf("save:r2"))
            .isGreaterThan(store.operations.indexOf("save:r3"));
        assertThat(store.operations.getLast()).isEqualTo(EVENT_PREFIX + RefreshRoutesEvent.class.getName());
        assertThat(store.everEmpty).as("任何时刻内存中都必须保留一套可用路由").isFalse();
        assertThat(store.routes.keySet()).containsExactlyInAnyOrder("r2", "r3");
        assertThat(initializer.currentValidConfig()).as("成功应用后记录最后一份有效配置").isEqualTo(config);
    }

    @Test
    @DisplayName("★ 新旧 id 全覆盖时不产生任何 delete（仅 save 覆盖）")
    void noDeleteWhenAllOldIdsStillPresent() throws Exception {
        store.seed(route("r1"), route("r2"));

        apply(routesJson("r1", "r2"));

        assertThat(store.deletedIds()).isEmpty();
        assertThat(store.savedIds()).containsExactlyInAnyOrder("r1", "r2");
        assertThat(store.routes.keySet()).containsExactlyInAnyOrder("r1", "r2");
        assertThat(store.everEmpty).isFalse();
    }

    @Test
    @DisplayName("★ 旧路由 id 为 null 时被跳过：不 delete(null)、不崩溃")
    void oldRouteWithNullIdIsSkipped() throws Exception {
        store.seed(new RouteDefinition(), route("r1"));

        apply(routesJson("r2"));

        assertThat(store.operations).containsExactly(
            "save:r2", "delete:r1", EVENT_PREFIX + RefreshRoutesEvent.class.getName());
    }

    @Test
    @DisplayName("★ 新配置含空 id 条目：丢弃并告警，其余有效路由照常 save，不因 IAE 中止整批")
    void newRouteWithEmptyIdIsDroppedAndOthersStillApplied() throws Exception {
        store.seed(route("r1"));
        // 真实 InMemoryRouteDefinitionRepository.save 对空 id 直接 Mono.error(IllegalArgumentException)，
        // 未过滤时整批 save 会中止（r2 也不会被写入）；替身已按同一契约拒绝空 id
        String config = "[{\"id\":null,\"uri\":\"lb://bad\"},{\"id\":\"r2\",\"uri\":\"lb://r2\"}]";

        apply(config);

        assertThat(store.savedIds()).as("空 id 被丢弃后，有效路由必须照常保存").containsExactly("r2");
        assertThat(store.routes).as("空 id 条目不得进入路由表").doesNotContainKey(null);
        assertThat(store.deletedIds()).as("r1 不在新配置的有效路由中，按既有增量语义删除").containsExactly("r1");
        assertThat(store.operations.getLast()).isEqualTo(EVENT_PREFIX + RefreshRoutesEvent.class.getName());
        assertThat(store.everEmpty).as("任何时刻都必须保留一套可用路由").isFalse();
        assertThat(warnMessages()).as("被丢弃的条目数必须告警，不得静默丢弃")
            .anySatisfy(message -> assertThat(message).contains("1 条路由的 id 为空").contains("已丢弃"));
    }

    @Test
    @DisplayName("★ 新配置全部条目 id 为空：按空列表语义保留旧路由，不 save/delete、不发事件，且告警")
    void allNewRoutesWithEmptyIdsKeepCurrentRoutes() throws Exception {
        store.seed(route("r1"));
        String config = "[{\"id\":null,\"uri\":\"lb://bad1\"},{\"uri\":\"lb://bad2\"}]";

        apply(config);

        assertThat(store.operations).as("无有效新路由 ⇒ 不产生任何写操作与刷新事件").isEmpty();
        assertThat(store.routes.keySet()).containsExactly("r1");
        assertThat(warnMessages())
            .anySatisfy(message -> assertThat(message).contains("已丢弃"))
            .anySatisfy(message -> assertThat(message).contains("没有有效路由"));
    }

    @Test
    @DisplayName("★ 反证：按旧 delete-all→save-all 顺序驱动替身会置位空窗标志（证明 everEmpty 断言可区分新旧实现）")
    void emptyWindowDetectorCatchesDeleteAllOrdering() {
        store.seed(route("r1"), route("r2"));

        store.delete(Mono.just("r1")).block();
        store.delete(Mono.just("r2")).block();
        store.save(Mono.just(route("r3"))).block();

        assertThat(store.everEmpty).as("旧实现会留出「一条路由都没有」的窗口").isTrue();
        assertThat(store.routes.keySet()).containsExactly("r3");
    }

    @Test
    @DisplayName("★ save 抛异常：旧路由一条都不删除、不发布刷新事件（旧路由完好）")
    void saveFailureKeepsOldRoutesAndSkipsDelete() throws Exception {
        RouteDefinition oldR2 = route("r2");
        store.seed(route("r1"), oldR2);
        store.failOnSaveIds.add("r2");

        apply(routesJson("r2", "r3"));

        assertThat(store.operations).as("失败后不得有任何 delete 或刷新事件")
            .noneMatch(operation -> operation.startsWith("delete:"))
            .noneMatch(operation -> operation.startsWith(EVENT_PREFIX));
        assertThat(store.routes).containsKeys("r1", "r2");
        assertThat(store.routes.get("r2")).as("失败的新定义不得覆盖旧定义").isSameAs(oldR2);
        assertThat(store.everEmpty).isFalse();
    }

    @Test
    @DisplayName("★ 监听回调触发的二次应用同样是增量：delete 只在 save 之后")
    void listenerCallbackAppliesIncrementally() throws Exception {
        store.seed(route("r1"), route("r2"));
        apply(routesJson("r1", "r2"));

        ArgumentCaptor<Listener> listenerCaptor = ArgumentCaptor.forClass(Listener.class);
        verify(configService).addListener(eq(DATA_ID), eq(GROUP), listenerCaptor.capture());

        store.operations.clear();
        listenerCaptor.getValue().receiveConfigInfo(routesJson("r2", "r3"));

        assertThat(store.deletedIds()).containsExactly("r1");
        assertThat(store.operations.indexOf("delete:r1"))
            .isGreaterThan(store.operations.indexOf("save:r2"))
            .isGreaterThan(store.operations.indexOf("save:r3"));
        assertThat(store.everEmpty).isFalse();
    }

    // ------------------------------------------------------------------ 保留当前路由的既有语义

    @Test
    @DisplayName("★ 合法空列表 []：保留当前路由，不 save/delete、不发布事件")
    void emptyListKeepsCurrentRoutes() throws Exception {
        store.seed(route("r1"));

        apply("[]");

        assertThat(store.operations).as("空列表不产生任何写操作与事件").isEmpty();
        assertThat(store.routes.keySet()).containsExactly("r1");
    }

    @Test
    @DisplayName("★ JSON 解析失败：保留当前路由，不 save/delete、不发布事件")
    void parseFailureKeepsCurrentRoutes() throws Exception {
        store.seed(route("r1"));

        apply("{\"routes\": [");

        assertThat(store.operations).isEmpty();
        assertThat(store.routes.keySet()).containsExactly("r1");
    }

    @Test
    @DisplayName("★ JSON 字面量 null：保留当前路由（与解析失败同策略）")
    void jsonNullKeepsCurrentRoutes() throws Exception {
        store.seed(route("r1"));

        apply("null");

        assertThat(store.operations).isEmpty();
        assertThat(store.routes.keySet()).containsExactly("r1");
    }

    // ------------------------------------------------------------------ 测试辅助

    private void apply(String config) throws Exception {
        when(configService.getConfig(anyString(), anyString(), anyLong())).thenReturn(config);
        initializer.run(mock(ApplicationArguments.class));
    }

    private static RouteDefinition route(String id) {
        // 注意：RouteDefinition(String) 构造器是按 "name=value" 解析谓词的快捷构造，不是 id 构造器，
        // 必须显式 setId
        RouteDefinition definition = new RouteDefinition();
        definition.setId(id);
        definition.setUri(URI.create("lb://" + id));
        return definition;
    }

    private static String routesJson(String... ids) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"id\":\"").append(ids[i]).append("\",\"uri\":\"lb://").append(ids[i]).append("\"}");
        }
        return json.append(']').toString();
    }

    /**
     * 内存路由存储替身：同时扮演 {@link RouteDefinitionLocator} 与 {@link RouteDefinitionWriter}
     * （与真实的 {@code InMemoryRouteDefinitionRepository} 一致），记录每次 save/delete 的先后顺序。
     */
    private static final class RecordingRouteStore implements RouteDefinitionLocator, RouteDefinitionWriter {

        private final Map<String, RouteDefinition> routes = new LinkedHashMap<>();

        private final List<String> operations = new ArrayList<>();

        private final Set<String> failOnSaveIds = new LinkedHashSet<>();

        /** 是否出现过「内存中一条路由都没有」的瞬间（旧 delete-all 实现会置位）。 */
        private boolean everEmpty;

        void seed(RouteDefinition... definitions) {
            for (RouteDefinition definition : definitions) {
                routes.put(definition.getId(), definition);
            }
        }

        List<String> savedIds() {
            return recordedIds("save:");
        }

        List<String> deletedIds() {
            return recordedIds("delete:");
        }

        private List<String> recordedIds(String prefix) {
            return operations.stream()
                .filter(operation -> operation.startsWith(prefix))
                .map(operation -> operation.substring(prefix.length()))
                .toList();
        }

        @Override
        public Flux<RouteDefinition> getRouteDefinitions() {
            return Flux.fromIterable(new ArrayList<>(routes.values()));
        }

        @Override
        public Mono<Void> save(Mono<RouteDefinition> route) {
            return route.doOnNext(definition -> {
                // 与真实 InMemoryRouteDefinitionRepository 同一契约：空 id（null / ""）直接拒绝，
                // 否则这个替身会比生产实现更宽容，掩盖「一条坏条目中止整批」的缺陷
                if (ObjectUtils.isEmpty(definition.getId())) {
                    throw new IllegalArgumentException("id may not be empty");
                }
                if (failOnSaveIds.contains(definition.getId())) {
                    throw new IllegalStateException("simulated save failure: " + definition.getId());
                }
                routes.put(definition.getId(), definition);
                operations.add("save:" + definition.getId());
                everEmpty |= routes.isEmpty();
            }).then();
        }

        @Override
        public Mono<Void> delete(Mono<String> routeId) {
            return routeId.doOnNext(id -> {
                routes.remove(id);
                operations.add("delete:" + id);
                everEmpty |= routes.isEmpty();
            }).then();
        }
    }
}

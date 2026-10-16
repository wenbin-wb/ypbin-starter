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
package cn.ypbin.starter.cache.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.cache.multilevel.CacheMetricsRecorder;
import cn.ypbin.starter.cache.multilevel.MicrometerCacheMetrics;
import cn.ypbin.starter.cache.multilevel.MultiLevelCacheService;
import com.github.benmanes.caffeine.cache.Cache;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * {@link MultiLevelCacheAutoConfiguration} 装配测试。
 *
 * <p>锁定两条最容易静默出错的装配边界：</p>
 * <ol>
 *   <li>「有 Caffeine 但无 spring-data-redis」：即便显式 {@code multi-level.enabled=true}，上下文也必须
 *       正常启动、不因配置类内省触碰 Redis 类型而抛 {@code NoClassDefFoundError}，且不装配多级缓存；</li>
 *   <li><strong>「micrometer 在类路径但没有 {@code MeterRegistry} Bean」</strong>：指标必须干净退化为
 *       {@link CacheMetricsRecorder#NOOP} 而<strong>不是启动失败</strong>。这正是本仓反复出现的
 *       optional 依赖陷阱——微服务宿主未引入 actuator/micrometer-registry 时，缓存能力绝不能连坐。</li>
 * </ol>
 *
 * @author wenbin
 * @since 2026-08-05
 */
class MultiLevelCacheAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MultiLevelCacheAutoConfiguration.class));

    @Test
    void withoutRedis_startsAndSkipsMultiLevel_evenWhenEnabled() {
        runner.withClassLoader(new FilteredClassLoader(StringRedisTemplate.class))
            .withPropertyValues("ypbin.cache.multi-level.enabled=true")
            .run(context -> {
                // 关键：无 Redis 时整类跳过，上下文不崩
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(MultiLevelCacheService.class);
            });
    }

    @Test
    void withoutEnabled_skipsMultiLevel() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(MultiLevelCacheService.class);
        });
    }

    @Test
    @DisplayName("★ 有 micrometer 但宿主未注册 MeterRegistry：照常启动，埋点退化为 NOOP（不得 NPE/启动失败）")
    void withoutMeterRegistryBean_startsAndDegradesMetricsToNoop() {
        enabledRunner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(CacheMetricsRecorder.class);
            assertThat(context.getBean(CacheMetricsRecorder.class))
                .as("★ 无 MeterRegistry Bean 时必须退化为零开销空实现；若在此抛 NPE，整块缓存能力会连坐起不来")
                .isSameAs(CacheMetricsRecorder.NOOP);
            assertThat(context).hasSingleBean(MultiLevelCacheService.class);

            // 埋点退化为 NOOP 后，读路径仍必须正常：记录器抛错/被传 null 都会改变缓存调用语义
            putIntoL1(context, "k", "v");
            assertThat(context.getBean(MultiLevelCacheService.class).get("k", String.class))
                .as("NOOP 埋点不得影响缓存读语义（L1 命中不触碰 Redis）")
                .isEqualTo("v");
        });
    }

    @Test
    @DisplayName("★ 宿主注册 MeterRegistry：装配 MicrometerCacheMetrics，cache 标签为固定标识 multi-level")
    void withMeterRegistryBean_wiresMicrometerMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        enabledRunner().withBean(SimpleMeterRegistry.class, () -> registry).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(CacheMetricsRecorder.class))
                .as("★ 有 MeterRegistry 时指标必须真的接上，而不是静默停在 NOOP")
                .isInstanceOf(MicrometerCacheMetrics.class);
            assertThat(registry.getMeters()).as("读指标必须在装配阶段完成注册").isNotEmpty();
            assertThat(registry.getMeters()).allSatisfy(meter -> assertThat(meter.getId().getTag("cache"))
                .as("cache 标签必须是固定的多级缓存标识")
                .isEqualTo(MultiLevelCacheAutoConfiguration.METRICS_CACHE_NAME));
        });
    }

    @Test
    @DisplayName("★ 类路径完全没有 micrometer：指标配置整块跳过，cacheService 仍按 NOOP 兜底装配")
    void withoutMicrometerOnClasspath_cacheServiceFallsBackToNoop() {
        enabledRunner().withClassLoader(new FilteredClassLoader(MeterRegistry.class)).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(CacheMetricsRecorder.class);
            assertThat(context.getBean(MultiLevelCacheService.class))
                .as("★ 指标配置缺席时，cacheService 必须靠自身的 NOOP 兜底装配成功")
                .isNotNull();
        });
    }

    /**
     * 多级缓存已启用、关闭广播（避免额外依赖 {@code RedisConnectionFactory}）、提供 L2 模板的装配环境。
     *
     * <p>L2 模板用 Mockito 替身：真实 {@code RedisTemplate} 的 {@code afterPropertiesSet()} 要求
     * {@code RedisConnectionFactory}，而本用例只关心装配与指标接线，不需要真实连接。</p>
     */
    @SuppressWarnings("unchecked")
    private ApplicationContextRunner enabledRunner() {
        RedisTemplate<String, Object> redisTemplate = Mockito.mock(RedisTemplate.class);
        return runner.withPropertyValues(
                "ypbin.cache.multi-level.enabled=true",
                "ypbin.cache.multi-level.invalidation-broadcast=false")
            .withBean("redisTemplate", RedisTemplate.class, () -> redisTemplate);
    }

    @SuppressWarnings("unchecked")
    private static void putIntoL1(ApplicationContext context, String key, Object value) {
        Cache<String, Object> l1 = context.getBean("l1Cache", Cache.class);
        l1.put(key, value);
    }
}

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
package cn.ypbin.starter.messaging.autoconfigure;

import cn.ypbin.starter.messaging.push.DefaultPushService;
import cn.ypbin.starter.messaging.push.PushService;
import cn.ypbin.starter.messaging.sse.InMemorySseTicketStore;
import cn.ypbin.starter.messaging.sse.JacksonSseFanoutCodec;
import cn.ypbin.starter.messaging.sse.RedisSseFanoutPublisher;
import cn.ypbin.starter.messaging.sse.RedisSseTicketStore;
import cn.ypbin.starter.messaging.sse.SseAudienceResolver;
import cn.ypbin.starter.messaging.sse.SseEmitterManager;
import cn.ypbin.starter.messaging.sse.SseFanoutBridge;
import cn.ypbin.starter.messaging.sse.SseFanoutCodec;
import cn.ypbin.starter.messaging.sse.SseFanoutSubscriber;
import cn.ypbin.starter.messaging.sse.SseProperties;
import cn.ypbin.starter.messaging.sse.SseSubscribeController;
import cn.ypbin.starter.messaging.sse.SseTicketController;
import cn.ypbin.starter.messaging.sse.SseTicketStore;
import cn.ypbin.starter.messaging.sse.SseUserIdResolver;
import cn.ypbin.starter.messaging.sse.TextSseFanoutCodec;
import java.time.Duration;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

/**
 * SSE 与统一推送门面自动配置。
 *
 * <p>仅在 Servlet Web 环境、类路径存在 {@link SseEmitter} 且 {@code ypbin.sse.enabled=true} 时生效。
 * 装配连接管理器、统一推送门面 {@link PushService}，并按需注册内置订阅端点。所有 Bean 可被业务方覆盖。</p>
 *
 * <p><strong>为何必须有 {@code @AutoConfigureAfter(DataRedisAutoConfiguration)}：</strong>
 * {@code @ConditionalOnBean} 只看得见<em>先于它处理完</em>的 Bean 定义。本模块包名 {@code cn.ypbin.*}
 * 在 Boot 的自动配置排序中排在 {@code org.springframework.*} <b>之前</b>（无显式顺序时按类名字典序），
 * 故下面两个 Redis 嵌套配置的 {@code @ConditionalOnBean(StringRedisTemplate.class)} 会在 Boot 注册模板
 * 之前被求值 ⇒ 判定「没有 Redis」⇒ <b>票据存储静默退化为进程内、跨实例扇出桥整块不装配</b>
 * （多副本下 SSE 只推本地连接、订阅票据不可跨实例消费，且没有任何报错）。这是 2026-10-15 租约锁同类
 * 线上事故排查时一并发现的缺陷，已用真实 Boot 自动配置组合的装配用例钉死。</p>
 *
 * <p><strong>为何把 Redis 票据存储放进嵌套的 {@link RedisTicketStoreConfiguration}</strong>：方法级
 * {@code @ConditionalOnClass} 只能阻止 Bean 注册，阻止不了 Spring 对配置类做方法内省——若本类某个
 * {@code @Bean} 方法签名里含 {@code StringRedisTemplate} 而 classpath 无 spring-data-redis，内省阶段即抛
 * {@code NoClassDefFoundError}，条件根本来不及生效。故收拢到<strong>类级</strong>
 * {@code @ConditionalOnClass(StringRedisTemplate.class)} 的嵌套配置，无 Redis 时整个类被跳过、其
 * {@code @Bean} 方法不被内省。用 {@code @Import} 引入（先于本类自身 {@code @Bean} 方法注册），
 * 故存在 Redis 时优先生效，内存兜底的 {@code @ConditionalOnMissingBean} 正确退让。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
@AutoConfiguration
@ConditionalOnClass(SseEmitter.class)
@AutoConfigureAfter(name = "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = SseProperties.PREFIX, name = "enabled", havingValue = "true")
@EnableConfigurationProperties(SseProperties.class)
@Import({SseAutoConfiguration.RedisTicketStoreConfiguration.class, SseAutoConfiguration.RedisFanoutConfiguration.class,
    SseAutoConfiguration.JacksonCodecConfiguration.class})
public class SseAutoConfiguration {

    /**
     * 可选的 audience 维度扩展点。
     *
     * <p>经<strong>配置类构造器</strong>注入而不是加到 {@code @Bean} 方法参数上：加参数会改动
     * {@code sseSubscribeController(...)} 的签名，japicmp 会判为 binary incompatible
     * （该门禁比对 Central 上一发布版）。构造器注入不进入公开 API 签名。</p>
     */
    @Nullable
    private final ObjectProvider<SseAudienceResolver> audienceResolver;

    /**
     * 容器内的扇出 codec 提供者：{@link SseEmitterManager} 必须与 {@code SseFanoutSubscriber} 用
     * <strong>同一个</strong> codec（发布侧写进帧的 codecId 会被订阅侧比对，不一致即整批丢弃），
     * 故这里把容器里选中的那个 Bean（宿主自定义 / Jackson / Text 兜底）回填给管理器。
     *
     * <p>为何不放进构造器：本类已发布版本的构造器签名不得改动（japicmp 比对 Central 上一发布版，
     * 增删参数判为 binary incompatible），故新增依赖走 setter 注入；{@link Autowired} 方法同样在
     * {@code @Bean} 方法被调用<em>之前</em>完成注入。用 {@link ObjectProvider} 而不是直接注入 codec：
     * 取值推迟到 Bean <em>实例化</em>阶段，那时全部 Bean 定义已注册完毕，<b>与自动配置顺序无关</b>。</p>
     */
    @Nullable
    private ObjectProvider<SseFanoutCodec> sseFanoutCodecProvider;

    /**
     * 保留无参构造器：本类已发布版本只有隐式默认构造器，新增带参构造器会让默认构造器消失，
     * japicmp 判为 {@code CONSTRUCTOR_REMOVED}（binary incompatible）。Spring 侧则由
     * {@link Autowired} 选中带参构造器。
     */
    public SseAutoConfiguration() {
        this.audienceResolver = null;
    }

    @Autowired
    public SseAutoConfiguration(ObjectProvider<SseAudienceResolver> audienceResolver) {
        this.audienceResolver = audienceResolver;
    }

    /**
     * 注入容器内的扇出 codec 提供者（见 {@link #sseFanoutCodecProvider}）。
     *
     * @param sseFanoutCodecProvider codec 提供者（延迟取值）
     */
    @Autowired
    void setSseFanoutCodecProvider(ObjectProvider<SseFanoutCodec> sseFanoutCodecProvider) {
        this.sseFanoutCodecProvider = sseFanoutCodecProvider;
    }

    /**
     * 连接管理器。默认装配为<strong>单实例</strong>形态（无扇出桥）；若 classpath 与容器都存在
     * {@code StringRedisTemplate}，{@link RedisFanoutConfiguration} 里条件装配的扇出桥会通过
     * {@link SseEmitterManager#setFanoutBridge} 回填，使其升级为多实例扇出。
     *
     * <p><strong>为何用 setter 回填而不是构造注入</strong>：桥的订阅者需要管理器来做本地投递，
     * 管理器需要在发布时拿到桥——构造注入会成环。用 {@code @Import} 让 Redis 相关装配先于本 Bean
     * 注册，再由一个显式的 {@code fanoutWiring} Bean 完成回填，既无环、也不改既有 Bean 方法签名
     * （api-compat 门禁要求）。</p>
     *
     * <p><strong>codec 取容器里的 Bean</strong>：发布侧（本管理器的 {@code publishFanout}）与订阅侧
     * （{@code SseFanoutSubscriber}）必须用同一个 codec，否则帧里的 codecId 对不上，对端会把
     * <em>全部</em>跨实例帧丢弃。此前这里恒用 {@code new TextSseFanoutCodec()}，宿主的
     * {@link SseFanoutCodec} Bean 只被订阅侧使用 ⇒ 自定义/Jackson codec 下跨实例扇出实际失效。
     * 现在直接走 4 参构造器把同一个 Bean 交进去（该构造器早已发布，签名未变）。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public SseEmitterManager sseEmitterManager(SseProperties properties) {
        ObjectProvider<SseFanoutCodec> provider = sseFanoutCodecProvider;
        SseFanoutCodec codec = provider == null
            ? new TextSseFanoutCodec()
            : provider.getIfAvailable(TextSseFanoutCodec::new);
        return new SseEmitterManager(properties.getTimeout(), properties.getHeartbeatIntervalSeconds(), null, codec);
    }

    /**
     * 扇出载荷 codec 的 <strong>Text 兜底</strong>：类路径无 Jackson、或容器里没有 {@code ObjectMapper}
     * Bean 时生效（无 Redis 时也用得上——管理器始终持有默认实现，故不由 Redis 配置限定）。
     *
     * <p>类路径存在 Jackson 且容器里有 {@code ObjectMapper} Bean 时，由 {@link JacksonCodecConfiguration}
     * 先注册同名 Bean（结构化载荷走 JSON）；两者都注册为 {@code sseFanoutCodec}，
     * 故宿主按名字或类型注入的写法在升级前后一致。</p>
     */
    @Bean
    @ConditionalOnMissingBean(SseFanoutCodec.class)
    public SseFanoutCodec sseFanoutCodec() {
        return new TextSseFanoutCodec();
    }

    /**
     * Jackson 结构化载荷 codec 装配：类路径存在 Jackson 3 的 {@link ObjectMapper} 且容器里有该 Bean 时，
     * 由本配置注册 {@link JacksonSseFanoutCodec}（否则退回 {@link TextSseFanoutCodec}）。
     *
     * <p><strong>为何是「类级 {@code @ConditionalOnClass} + {@code ObjectProvider}」，而不是
     * {@code @ConditionalOnBean(ObjectMapper.class)}</strong>：{@code @ConditionalOnBean} 只看得到
     * <em>先于它处理完</em>的 Bean 定义，而 {@code ObjectMapper} 由 Boot 的 Jackson 自动配置提供，
     * 本模块包名 {@code cn.ypbin.*} 在 Boot 自动配置排序中排在 {@code org.springframework.*}
     * <strong>之前</strong>（无显式顺序时按类名字典序）⇒ 条件会在 Boot 注册 mapper 之前求值、
     * <b>恒假</b> ⇒ Jackson codec 静默不生效（正是本仓 2026-10-15 那类事故的形态）。
     * {@link ObjectProvider} 在 Bean <em>实例化</em>阶段才取值，那时全部 Bean 定义已注册完毕，
     * 故与自动配置顺序无关；类级 {@code @ConditionalOnClass} 则在 Jackson 缺席时整体跳过本类，
     * 使其 {@code @Bean} 方法不被内省、不触碰 {@link ObjectMapper}（与
     * {@link RedisTicketStoreConfiguration} 同一模式）。</p>
     *
     * <p><strong>为何取不到 mapper 时返回 {@link TextSseFanoutCodec} 而不是 {@code null}</strong>：
     * {@code @Bean} 方法返回 {@code null} 会被 Spring 注册成 {@code NullBean}——Bean 定义仍在，
     * 外层 {@link SseAutoConfiguration#sseFanoutCodec()} 的 {@code @ConditionalOnMissingBean} 会退让，最终容器里
     * <strong>没有任何可用的 codec</strong>（实测 {@code getBeanProvider(SseFanoutCodec.class)
     * .getIfAvailable()} 为 {@code null}，比不装配更糟：启动不报错、SSE 投递才失败）。故此处显式兜底。</p>
     *
     * <p><strong>Bean 名与注册顺序</strong>：本嵌套配置类经外层 {@code @Import} 引入，先于本类自身的
     * {@code @Bean} 方法注册（与 {@link RedisTicketStoreConfiguration} 同理），故 Text 兜底会正确退让；
     * 方法名刻意与外层同名，使升级前后 Bean 名都保持 {@code sseFanoutCodec}。即便顺序被破坏，两侧都有
     * {@code @ConditionalOnMissingBean}，后注册的一方会退让，不会出现同名覆盖异常。</p>
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(ObjectMapper.class)
    static class JacksonCodecConfiguration {

        /**
         * Jackson codec；无 {@code ObjectMapper} Bean 时返回 Text 实现（见类注释）。
         *
         * @param objectMapperProvider 容器里的 Jackson 序列化器（弱依赖：缺席即退回纯文本协议）
         * @return 扇出载荷 codec
         */
        @Bean
        @ConditionalOnMissingBean(SseFanoutCodec.class)
        SseFanoutCodec sseFanoutCodec(ObjectProvider<ObjectMapper> objectMapperProvider) {
            ObjectMapper objectMapper = objectMapperProvider.getIfAvailable();
            return objectMapper == null ? new TextSseFanoutCodec() : new JacksonSseFanoutCodec(objectMapper);
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public PushService pushService(SseEmitterManager sseEmitterManager) {
        return new DefaultPushService(sseEmitterManager);
    }

    /**
     * 一次性订阅票据存储：内存兜底，仅当容器中不存在任何 {@link SseTicketStore} 时装配
     * （Redis 实现存在时即退让，见 {@link RedisTicketStoreConfiguration}）。
     */
    @Bean
    @ConditionalOnMissingBean(SseTicketStore.class)
    public SseTicketStore inMemorySseTicketStore() {
        return new InMemorySseTicketStore();
    }

    /**
     * Redis Pub/Sub 跨实例扇出装配。与 {@link RedisTicketStoreConfiguration} 同一模式：类级
     * {@code @ConditionalOnClass(StringRedisTemplate.class)} + {@code @ConditionalOnBean}，无 Redis 时整类
     * 被跳过、方法不被内省（否则 {@code StringRedisTemplate} 出现在方法签名里，内省阶段即
     * {@code NoClassDefFoundError}）。
     *
     * <p>发布器与订阅容器用<strong>同一个</strong>实例标识：订阅方据此丢弃自己实例发布的帧，
     * 从而杜绝自广播回环（照抄同仓 {@code RedisCacheInvalidationPublisher} 的长度前缀定界做法）。</p>
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnProperty(prefix = SseProperties.PREFIX, name = "fanout-enabled", havingValue = "true",
        matchIfMissing = true)
    static class RedisFanoutConfiguration {

        /**
         * 实例标识：同一 JVM 内发布器与订阅者共享，用于识别并丢弃自广播帧。UUID 每次启动重新生成即可
         * （旧实例的帧在重启后已无意义，且不会与新实例撞号）。
         */
        @Bean
        @ConditionalOnMissingBean(name = "sseFanoutInstanceId")
        String sseFanoutInstanceId() {
            return UUID.randomUUID().toString();
        }

        /**
         * Redis Pub/Sub 扇出桥（发布侧）。
         *
         * <p><strong>这里解开「管理器 ↔ 桥」的构造环</strong>：订阅者需要管理器来做「收到对端帧后投本实例
         * 连接」，管理器需要在发布时拿到桥。做法是让本方法把管理器作为参数拿到、把桥回填进去
         * （{@link SseEmitterManager#setFanoutBridge}），而管理器自身的 {@code @Bean} 方法签名与语义
         * 完全不变（api-compat 门禁要求）。</p>
         */
        @Bean
        @ConditionalOnMissingBean(SseFanoutBridge.class)
        SseFanoutBridge sseFanoutBridge(StringRedisTemplate redisTemplate, SseProperties properties,
                                        String sseFanoutInstanceId, SseEmitterManager sseEmitterManager) {
            SseFanoutBridge bridge =
                new RedisSseFanoutPublisher(redisTemplate, properties.getFanoutChannel(), sseFanoutInstanceId);
            sseEmitterManager.setFanoutBridge(bridge);
            return bridge;
        }

        /**
         * 扇出订阅者：收到其它实例的帧后投给<strong>本实例</strong>的连接。
         *
         * <p><strong>独立成 Bean</strong>而不是在监听容器里就地 {@code new}：① 宿主自建
         * {@link RedisMessageListenerContainer} 时（见 {@link #sseFanoutListenerContainer} 的
         * {@code @ConditionalOnMissingBean}）可直接注入本 Bean 挂上，不必自己拼装；② 装配用例可从容器
         * 里拿到订阅者，端到端验证「管理器与订阅者用同一个 codec」这条约束。</p>
         *
         * <p><strong>codec 不再作为参数</strong>：订阅者一律用
         * {@link SseEmitterManager#getCodec()}（见 {@link SseFanoutSubscriber} 类注释）。于是即便宿主
         * 自定义 {@code SseEmitterManager}（其 codec 未必等于容器里的 {@link SseFanoutCodec} Bean），
         * 发布侧与订阅侧仍结构上同源，不会出现 codecId 对不上导致跨实例帧被整批丢弃。</p>
         */
        @Bean
        @ConditionalOnMissingBean
        SseFanoutSubscriber sseFanoutSubscriber(SseEmitterManager sseEmitterManager,
                                                SseFanoutBridge sseFanoutBridge) {
            return new SseFanoutSubscriber(sseEmitterManager, sseFanoutBridge.instanceId());
        }

        /**
         * 订阅容器：收到其它实例的扇出帧后投给<strong>本实例</strong>的连接。
         *
         * <p>Bean 交给 Spring 管理生命周期（启动即订阅、容器关闭自动停止），照抄同仓
         * {@code MultiLevelCacheAutoConfiguration#cacheInvalidationListenerContainer} 的做法：
         * 不要在业务代码里手工 {@code afterPropertiesSet()/start()}——那会绕过 Spring 的生命周期与
         * 异常处理。</p>
         *
         * <p>{@code @ConditionalOnMissingBean(RedisMessageListenerContainer.class)}：宿主已有自己的订阅
         * 容器时不重复建连（与 cache 模块同一取舍）。宿主自建容器时可注入本配置注册的
         * {@link SseFanoutSubscriber} Bean 挂上（该 Bean 始终存在，与宿主是否自建容器无关）。</p>
         */
        @Bean
        @ConditionalOnMissingBean(RedisMessageListenerContainer.class)
        RedisMessageListenerContainer sseFanoutListenerContainer(RedisConnectionFactory connectionFactory,
                                                                 SseFanoutSubscriber sseFanoutSubscriber,
                                                                 SseProperties properties) {
            RedisMessageListenerContainer container = new RedisMessageListenerContainer();
            container.setConnectionFactory(connectionFactory);
            container.addMessageListener(sseFanoutSubscriber, new ChannelTopic(properties.getFanoutChannel()));
            return container;
        }
    }

    /**
     * Redis 分布式票据存储配置。类级 {@code @ConditionalOnClass(StringRedisTemplate.class)}：无 Redis 时
     * 整个类被跳过、其 {@code @Bean} 方法不被内省，从而外层配置在无 spring-data-redis 环境下也能正常启动。
     *
     * <p>类级 {@code @ConditionalOnClass} 只判断 classpath 是否有该类，不代表容器里真有可用的
     * {@link StringRedisTemplate} Bean（如未配置 Redis 连接、或测试环境只引入了依赖未装配自动配置）。
     * 叠加 {@code @ConditionalOnBean(StringRedisTemplate.class)}，两者都满足才展开本配置，避免 Redis Bean
     * 因缺少注入源在启动期抛 {@code UnsatisfiedDependencyException}。</p>
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnBean(StringRedisTemplate.class)
    static class RedisTicketStoreConfiguration {

        /**
         * Redis 分布式票据存储：多节点共享、Lua 原子消费，存在 Redis 时优先装配。
         */
        @Bean
        @ConditionalOnMissingBean(SseTicketStore.class)
        public SseTicketStore redisSseTicketStore(StringRedisTemplate redisTemplate) {
            return new RedisSseTicketStore(redisTemplate);
        }
    }

    /**
     * 内置订阅端点。仅当容器内存在 {@link SseUserIdResolver}（如引入 security 模块）时注册——没有鉴权来源
     * 就不暴露端点，避免无鉴权订阅。用户标识由端点从登录态或一次性票据解析，不接收前端传入的 userId。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(SseUserIdResolver.class)
    @ConditionalOnProperty(prefix = SseProperties.PREFIX, name = "register-endpoint", havingValue = "true", matchIfMissing = true)
    public SseSubscribeController sseSubscribeController(
        SseEmitterManager sseEmitterManager, SseUserIdResolver sseUserIdResolver, SseTicketStore sseTicketStore) {
        // 可选扩展点经构造器注入的 ObjectProvider 取（见本类构造器）：没有 SseAudienceResolver 时为 null
        // ⇒ 只按 userId，行为与接入前完全一致。
        //
        // ⚠️ 本方法签名**不得**改动：它出现在已发布版本里，api-compat 门禁（japicmp 比对 Central 上一
        // 发布版）会把新增/删除参数判为 binary incompatible。需要更多依赖时，走本配置类的构造器注入，
        // 不要把参数加到 @Bean 方法上。
        return new SseSubscribeController(sseEmitterManager, sseUserIdResolver, sseTicketStore,
            audienceResolver == null ? null : audienceResolver.getIfAvailable());
    }

    /**
     * 一次性票据签发端点。与订阅端点同条件注册（存在鉴权来源 + register-endpoint）。用于 Header 令牌鉴权
     * 场景：{@code EventSource} 不能带 Authorization 头，前端先用带令牌的普通请求换票，再用 ticket 订阅。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(SseUserIdResolver.class)
    @ConditionalOnProperty(prefix = SseProperties.PREFIX, name = "register-endpoint", havingValue = "true", matchIfMissing = true)
    public SseTicketController sseTicketController(
        SseTicketStore sseTicketStore, SseUserIdResolver sseUserIdResolver, SseProperties properties) {
        return new SseTicketController(
            sseTicketStore, sseUserIdResolver, Duration.ofSeconds(properties.getTicketTtlSeconds()));
    }
}

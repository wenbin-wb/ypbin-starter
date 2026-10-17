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
package cn.ypbin.starter.ai.autoconfigure.rag;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.ai.chat.AiEmbeddingConfigResolver;
import cn.ypbin.starter.ai.rag.AiRagService;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration;
import org.springframework.ai.vectorstore.redis.autoconfigure.RedisVectorStoreAutoConfiguration;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;

/**
 * {@code AiRagAutoConfiguration} 的 {@code @ConditionalOnBean(VectorStore.class)} 顺序风险的
 * <b>运行时</b>回归用例。
 *
 * <p><b>为何必须用真实 {@code AutoConfigurations}：</b>若把 {@code VectorStore} 作为<b>用户配置</b>
 * Bean 塞进上下文，它的 Bean 定义先于自动配置处理就已存在，顺序问题会被<b>结构性掩盖</b>——
 * 这正是本仓 2026-10-15 事故（v3.9.1）注释里写明的盲区。因此这里只把第三方
 * {@link RedisVectorStoreAutoConfiguration}（真实提供者）与 {@code AiRagAutoConfiguration}
 * 一起交给 Boot 的排序器，仅把<b>被测类型无关</b>的依赖（{@code EmbeddingModel}、
 * {@code JedisConnectionFactory}）作为用户 Bean 提供。</p>
 *
 * <p><b>根因：</b>{@code @ConditionalOnBean} 是 {@code REGISTER_BEAN} 相位条件，只能看见
 * 「先于本配置类处理完」的 Bean 定义；{@code cn.ypbin.*} 在 Boot 自动配置排序中排在
 * {@code org.springframework.*} 之前 ⇒ 第三方向量库提供的 {@code VectorStore} 尚未注册 ⇒
 * 条件恒假 ⇒ {@code AiRagService} 整块静默不装配。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
class AiRagVectorStoreOrderingRuntimeTest {

    /** 真实第三方提供者 + 本仓 RAG 配置，交由 Boot 排序器决定处理顺序。 */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            RedisVectorStoreAutoConfiguration.class,
            AiRagAutoConfiguration.class));

    @Test
    @DisplayName("★ 第三方（Redis）向量库提供 VectorStore 时，AiRagService 必须装配")
    void aiRagServiceAssembledWhenThirdPartyVectorStoreProvidesVectorStore() {
        runner.withPropertyValues("ypbin.ai.rag.enabled=true")
            .withBean(EmbeddingModel.class, StubEmbeddingModel::new)
            .withBean(JedisConnectionFactory.class, JedisConnectionFactory::new)
            .run(context -> {
                printVectorStoreConditionReport(context);

                assertThat(context).hasSingleBean(VectorStore.class);
                assertThat(context.getBeansOfType(AiRagService.class))
                    .as("第三方向量库提供 VectorStore 时 AiRagService 必须装配；缺失即说明 "
                        + "@ConditionalOnBean(VectorStore.class) 因缺少 @AutoConfigureAfter 而恒假"
                        + "（2026-10-15 同类第 5 例，静默失效）")
                    .hasSize(1);
            });
    }

    @Test
    @DisplayName("★ 仓内提供者路径不得被顺序修改误伤（AiVectorStoreAutoConfiguration 仍先于 AiRag）")
    void inRepoVectorStorePathStillAssemblesAiRagService() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                AiVectorStoreAutoConfiguration.class,
                AiRagAutoConfiguration.class))
            .withPropertyValues("ypbin.ai.rag.enabled=true")
            .withBean(AiEmbeddingConfigResolver.class, StubEmbeddingResolver::new)
            .run(context -> {
                assertThat(context).hasSingleBean(VectorStore.class);
                assertThat(context.getBeansOfType(AiRagService.class))
                    .as("仓内 LazySimpleVectorStore 路径必须继续装配 AiRagService")
                    .hasSize(1);
            });
    }

    @Test
    @DisplayName("★ 真实宿主形态：仓内 AiVectorStore 与第三方 Redis 同时在场，观察谁真正提供 VectorStore")
    void realHostShapeWithBothInRepoAndThirdPartyProviders() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                RedisVectorStoreAutoConfiguration.class,
                AiVectorStoreAutoConfiguration.class,
                AiRagAutoConfiguration.class))
            .withPropertyValues("ypbin.ai.rag.enabled=true")
            .withBean(AiEmbeddingConfigResolver.class, StubEmbeddingResolver::new)
            .withBean(EmbeddingModel.class, StubEmbeddingModel::new)
            .withBean(JedisConnectionFactory.class, JedisConnectionFactory::new)
            .run(context -> {
                printVectorStoreConditionReport(context);
                System.out.println("[真实宿主形态] VectorStore 全部实现 = "
                    + context.getBeansOfType(VectorStore.class).keySet());
                assertThat(context.getBeansOfType(AiRagService.class))
                    .as("两类提供者同时在场时 AiRagService 仍须装配")
                    .hasSize(1);
                System.out.println("[真实宿主形态] AiRagService 实际持有的 VectorStore = "
                    + injectedVectorStoreType(context.getBean(AiRagService.class)));
            });
    }

    /**
     * 读取 {@code DefaultAiRagService.vectorStore} 的实际类型。
     *
     * <p>两类提供者在场时会有两个 {@code VectorStore} Bean，按名（形参名 {@code vectorStore}）而非
     * 按类型解析出其中哪一个，直接决定 RAG 落到哪个向量库；这里显式取证，避免用推断代替事实。</p>
     *
     * @param service 已装配的 RAG 服务
     * @return 其内部持有的向量库类型名
     */
    private static String injectedVectorStoreType(AiRagService service) {
        try {
            Field field = service.getClass().getDeclaredField("vectorStore");
            field.setAccessible(true);
            return ((VectorStore) field.get(service)).getClass().getName();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("无法读取 AiRagService 内部 VectorStore", ex);
        }
    }

    /**
     * 结构性掩盖的实证：把 {@code VectorStore} 作为<b>用户配置</b> Bean 塞进上下文时，
     * 顺序问题确实会被掩盖。此用例只用于说明「为何该缺陷长期未被单测发现」，
     * <b>不得</b>作为「无需修复」的依据。
     */
    @Test
    @DisplayName("对照：用户配置直接提供 VectorStore 时条件为真（说明单测为何看不见该缺陷）")
    void userDefinedVectorStoreMasksOrderingProblem() {
        VectorStore userDefinedStore = new StubVectorStore();
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AiRagAutoConfiguration.class))
            .withPropertyValues("ypbin.ai.rag.enabled=true")
            .withBean(VectorStore.class, () -> userDefinedStore)
            .run(context -> assertThat(context.getBeansOfType(AiRagService.class))
                .as("用户配置 Bean 先于自动配置处理即存在 ⇒ @ConditionalOnBean 为真 ⇒ 顺序缺陷被掩盖")
                .hasSize(1));
    }

    @Test
    @DisplayName("★ 第三方（PGVector）向量库提供 VectorStore 时，AiRagService 必须装配")
    void aiRagServiceAssembledWhenPgVectorProvidesVectorStore() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                PgVectorStoreAutoConfiguration.class,
                AiRagAutoConfiguration.class))
            .withPropertyValues(
                "ypbin.ai.rag.enabled=true",
                // 关闭建表与表结构校验：默认值即为 false，显式写出以钉住「本用例不访问真实数据库」
                "spring.ai.vectorstore.pgvector.initialize-schema=false",
                "spring.ai.vectorstore.pgvector.schema-validation=false")
            .withBean(EmbeddingModel.class, StubEmbeddingModel::new)
            .withBean(JdbcTemplate.class, () -> new JdbcTemplate(new StubDataSource()))
            .run(context -> {
                printVectorStoreConditionReport(context);

                assertThat(context).hasSingleBean(VectorStore.class);
                assertThat(context.getBeansOfType(AiRagService.class))
                    .as("PGVector 向量库提供 VectorStore 时 AiRagService 必须装配（第二个第三方提供者路径）")
                    .hasSize(1);
            });
    }

    /** 打印条件求值的运行时原文（{@code ConditionEvaluationReport}），供回执取证。 */
    private static void printVectorStoreConditionReport(AssertableApplicationContext context) {
        ConfigurableApplicationContext source =
            context.getSourceApplicationContext(ConfigurableApplicationContext.class);
        ConfigurableListableBeanFactory beanFactory = source.getBeanFactory();
        ConditionEvaluationReport report = ConditionEvaluationReport.get(beanFactory);

        System.out.println("========== [取证] VectorStore 相关条件判定原文 ==========");
        report.getConditionAndOutcomesBySource().forEach((source1, outcomes) -> {
            boolean interesting = source1.contains("AiRagAutoConfiguration")
                || source1.contains("RedisVectorStoreAutoConfiguration");
            if (!interesting) {
                return;
            }
            outcomes.forEach(outcome -> System.out.println(
                "[条件报告] source=" + source1
                    + " | condition=" + outcome.getCondition().getClass().getSimpleName()
                    + " | match=" + outcome.getOutcome().isMatch()
                    + " | message=" + outcome.getOutcome().getMessage()));
        });
        System.out.println("[装配] VectorStore beans = " + context.getBeansOfType(VectorStore.class));
        System.out.println("[装配] AiRagService beans = " + context.getBeansOfType(AiRagService.class));
        System.out.println("======================================================");
    }

    /** 仅用于满足第三方向量库的 {@code EmbeddingModel} 依赖，不参与被测条件判定。 */
    static final class StubEmbeddingModel implements EmbeddingModel {

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            return new EmbeddingResponse(List.of());
        }

        @Override
        public float[] embed(Document document) {
            return new float[] {0.0F};
        }
    }

    /** 仅用于满足仓内提供者的扩展点依赖。 */
    static final class StubEmbeddingResolver implements AiEmbeddingConfigResolver {

        @Override
        public AiModelInfo resolve() {
            return new AiModelInfo("http://127.0.0.1:1", "stub-key", "stub-model");
        }
    }

    /** 用户配置形态的最小 {@link VectorStore}，仅用于「结构性掩盖」对照。 */
    static final class StubVectorStore implements VectorStore {

        @Override
        public void add(List<Document> documents) {
            // 对照用例不触发写入
        }

        @Override
        public void delete(List<String> idList) {
            // 对照用例不触发删除
        }

        @Override
        public void delete(Filter.Expression filterExpression) {
            // 对照用例不触发删除
        }

        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            return List.of();
        }

        @Override
        public List<Document> similaritySearch(String query) {
            return List.of();
        }
    }

    /**
     * 占位数据源：仅满足 {@link JdbcTemplate} 的必填属性。
     *
     * <p>PGVector 用例已把 {@code initialize-schema} 与 {@code schema-validation} 都置为
     * {@code false}（这也是默认值），因此不会有任何连接被获取；{@link #getConnection()} 一旦被调用
     * 即抛出 {@link UnsupportedOperationException}，避免用例「悄悄连库」而变成假绿。</p>
     */
    static final class StubDataSource extends AbstractDataSource {

        @Override
        public Connection getConnection() {
            throw new UnsupportedOperationException("PGVector 用例不应获取数据库连接");
        }

        @Override
        public Connection getConnection(String username, String password) {
            throw new UnsupportedOperationException("PGVector 用例不应获取数据库连接");
        }
    }
}

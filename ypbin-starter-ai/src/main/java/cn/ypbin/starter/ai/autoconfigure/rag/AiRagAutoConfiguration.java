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

import cn.ypbin.starter.ai.rag.AiRagService;
import cn.ypbin.starter.ai.rag.DefaultAiRagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * AI RAG 自动配置。
 *
 * <p>仅当 classpath 存在 {@link VectorStore}（已引入向量库 starter）且
 * {@code ypbin.ai.rag.enabled=true} 时才装配。
 *
 * <p><b>为何必须有 {@code @AutoConfigureAfter}：</b>本类的 {@code aiRagService} Bean 用
 * {@code @ConditionalOnBean(VectorStore.class)} 做守卫，而 {@code @ConditionalOnBean} 是
 * {@code REGISTER_BEAN} 相位条件，<b>只看得见「先于它处理完」的 Bean 定义</b>。
 * {@link VectorStore} 的 Bean 由第三方（Spring AI）自动配置提供，且 Spring AI 2.0.1 提供向量库的
 * 自动配置<b>没有一个</b>声明与本类的顺序。不加顺序约束时，{@code cn.ypbin.starter.ai.*} 在 Boot 自动配置
 * 排序中排在 {@code org.springframework.*} <b>之前</b>（无显式顺序时按类名字典序）⇒ 条件求值时第三方
 * {@code VectorStore} 尚未注册 ⇒ <b>条件恒假</b> ⇒ {@code AiRagService} 整块静默不装配（无任何报错）
 * ⇒ 宿主开了 {@code ypbin.ai.rag.enabled=true} 并引入<b>任一</b>向量库 starter 时，RAG/知识库检索
 * <b>静默不可用</b>。</p>
 *
 * <p>这是 2026-10-15 starter v3.9.1 修掉的「{@code @ConditionalOnBean} 判定自动配置提供的类型导致
 * 条件恒假」同一根因。首修只覆盖了 pom 内 optional 声明的 Redis / PGVector 两个提供者，其余向量库
 * 在场时条件仍恒假（issue #85：同类第 7 例）。运行时实证见
 * {@code AiRagVectorStoreOrderingRuntimeTest}：未加顺序时条件报告为
 * {@code @ConditionalOnBean ... did not find any beans}，而 {@code VectorStore} Bean 实际存在。</p>
 *
 * <p><b>提供者清单的一手取证（2026-10-16）：</b>从 Maven Central 下载 Spring AI 2.0.1 全部
 * {@code spring-ai-autoconfigure-vector-store-*} 构件（{@code groupId:artifactId:version} 均为
 * {@code org.springframework.ai:spring-ai-autoconfigure-vector-store-*:2.0.1}），解压读
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}，
 * 并用 {@code javap} 确认其 {@code @Bean} 方法的返回类型实现（或继承自实现
 * {@code org.springframework.ai.vectorstore.VectorStore} 的）{@code ...VectorStore}。
 * 结论：2.0.1 共有 20 个向量库自动配置注册 {@code VectorStore} Bean，已全部登记在下方；
 * {@code vector-store-observation}（只装观测装饰）与 {@code vector-store-redis-semantic-cache}
 * （只装语义缓存）经同样取证<b>不</b>提供 {@code VectorStore}，故不登记。详细坐标见
 * {@code AutoConfigurationOrderingTest#buildThirdPartyProvidedTypes()}。</p>
 *
 * <p><b>为何用 {@code name} 而非 {@code Class} 引用：</b>全部向量库 starter 均为 {@code optional}
 * 依赖，宿主可能只引其一或都不引；{@code name} 形式的 FQCN 由 Boot 的 {@code AutoConfigurationSorter}
 * 通过元数据读取，<b>不会类加载</b>，未出现在当前 classpath 的名字被排序器直接忽略（仅在与
 * 本次实际参与的自动配置集合求交时生效）。这也与仓内 v3.9.1 各处既有处方保持一致。</p>
 *
 * <p>与 {@link AiVectorStoreAutoConfiguration} 的关系：后者带
 * {@code @AutoConfigureBefore(AiRagAutoConfiguration.class)}，故「仓内 LazySimpleVectorStore」
 * 这条路径本来就成立；本类的 {@code @AutoConfigureAfter} 补的是「第三方向量库」路径。</p>
 *
 * @author wenbin
 * @since 2026-08-15
 */
@AutoConfiguration
@AutoConfigureAfter(name = {
    // Spring AI 2.0.1 全部「注册 VectorStore Bean」的第三方自动配置，按 FQCN 字典序排列。
    // 每个 FQCN 均来自对应构件 AutoConfiguration.imports 的一手取证，详见类注释与架构门禁登记。
    "org.springframework.ai.vectorstore.azure.autoconfigure.AzureVectorStoreAutoConfiguration",
    "org.springframework.ai.vectorstore.bedrockknowledgebase.autoconfigure.BedrockKnowledgeBaseVectorStoreAutoConfiguration",
    "org.springframework.ai.vectorstore.cassandra.autoconfigure.CassandraVectorStoreAutoConfiguration",
    "org.springframework.ai.vectorstore.chroma.autoconfigure.ChromaVectorStoreAutoConfiguration",
    "org.springframework.ai.vectorstore.couchbase.autoconfigure.CouchbaseSearchVectorStoreAutoConfiguration",
    "org.springframework.ai.vectorstore.elasticsearch.autoconfigure.ElasticsearchVectorStoreAutoConfiguration",
    "org.springframework.ai.vectorstore.gemfire.autoconfigure.GemFireVectorStoreAutoConfiguration",
    "org.springframework.ai.vectorstore.mariadb.autoconfigure.MariaDbStoreAutoConfiguration",
    "org.springframework.ai.vectorstore.milvus.autoconfigure.MilvusVectorStoreAutoConfiguration",
    "org.springframework.ai.vectorstore.mongodb.autoconfigure.MongoDBAtlasVectorStoreAutoConfiguration",
    "org.springframework.ai.vectorstore.neo4j.autoconfigure.Neo4jVectorStoreAutoConfiguration",
    "org.springframework.ai.vectorstore.opensearch.autoconfigure.OpenSearchVectorStoreAutoConfiguration",
    "org.springframework.ai.vectorstore.oracle.autoconfigure.OracleVectorStoreAutoConfiguration",
    "org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration",
    "org.springframework.ai.vectorstore.pinecone.autoconfigure.PineconeVectorStoreAutoConfiguration",
    "org.springframework.ai.vectorstore.qdrant.autoconfigure.QdrantVectorStoreAutoConfiguration",
    "org.springframework.ai.vectorstore.redis.autoconfigure.RedisVectorStoreAutoConfiguration",
    "org.springframework.ai.vectorstore.s3.autoconfigure.S3VectorStoreAutoConfiguration",
    "org.springframework.ai.vectorstore.typesense.autoconfigure.TypesenseVectorStoreAutoConfiguration",
    "org.springframework.ai.vectorstore.weaviate.autoconfigure.WeaviateVectorStoreAutoConfiguration"
})
@ConditionalOnClass(VectorStore.class)
@ConditionalOnProperty(prefix = AiRagProperties.PREFIX, name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AiRagProperties.class)
public class AiRagAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AiRagAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(AiRagService.class)
    @ConditionalOnBean(VectorStore.class)
    public AiRagService aiRagService(VectorStore vectorStore, AiRagProperties props) {
        log.debug("[ypbin-ai] AiRagService configured with VectorStore: {}",
            vectorStore.getClass().getSimpleName());
        return new DefaultAiRagService(vectorStore, props);
    }
}

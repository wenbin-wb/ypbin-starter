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
package cn.ypbin.starter.cache.redis;

import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

/**
 * Redis 值序列化器工厂（Jackson 3）。
 *
 * <p>独立成工厂类，使「生产装配」与「真实 Redis 往返集成测试」共用同一构造逻辑，
 * 避免测试各自复刻配置导致漂移——序列化配置一旦回归，真机往返测试即可捕获。</p>
 *
 * @author wenbin
 * @since 2026-09-13
 */
public final class RedisJsonSerializerFactory {

    private RedisJsonSerializerFactory() {
    }

    /**
     * 构建 Redis 值序列化器：以容器 JsonMapper 的构建器副本为基底，多态类型交给序列化器自身管理，
     * 并在写入前对不可变集合做规范化。
     *
     * <p><strong>不可在传入的 mapper 上预先 {@code activateDefaultTyping}</strong>：
     * {@link GenericJacksonJsonRedisSerializer} 会自行安装多态类型处理，若外部再开一次，
     * 写路径与读路径的类型标识形态会不一致，反序列化将直接失败。因此这里用序列化器自带的 builder，
     * 以容器 mapper 的构建器副本为基底（保留 JavaTime 与大数字规则），再统一开启多态类型。</p>
     *
     * <p><strong>写入前必须规范化不可变集合</strong>：见 {@link ImmutableCollectionNormalizer}
     * ——JDK 不可变集合为 final 类型，多态类型标识无法写入，会导致读回时反序列化失败。</p>
     *
     * <p>多态校验器仅信任所有基类型以支持任意缓存对象——缓存内容由服务端自身写入，
     * 不存在反序列化外部不可信数据的风险。序列化配置落在独立 mapper 上，不会把
     * {@code @class} 混入 HTTP 响应。</p>
     *
     * @param baseMapper 容器 JsonMapper（可为 {@code null}，此时用默认构建器）
     * @return Redis 值序列化器
     */
    public static RedisSerializer<Object> create(@Nullable JsonMapper baseMapper) {
        JsonMapper base = baseMapper != null ? baseMapper : JsonMapper.builder().build();
        GenericJacksonJsonRedisSerializer delegate = GenericJacksonJsonRedisSerializer
            .builder(base::rebuild)
            .enableDefaultTyping(
                BasicPolymorphicTypeValidator.builder().allowIfBaseType(Object.class).build())
            .build();
        return new ImmutableSafeRedisSerializer(delegate);
    }

    /**
     * 委托 {@link GenericJacksonJsonRedisSerializer} 并在写入前规范化不可变集合。
     *
     * <p>读路径完全交给委托序列化器：类型标识由它写入，也由它还原。</p>
     */
    private static final class ImmutableSafeRedisSerializer implements RedisSerializer<Object> {

        private final GenericJacksonJsonRedisSerializer delegate;

        private ImmutableSafeRedisSerializer(GenericJacksonJsonRedisSerializer delegate) {
            this.delegate = delegate;
        }

        @Override
        public byte[] serialize(@Nullable Object value) throws SerializationException {
            return delegate.serialize(ImmutableCollectionNormalizer.normalize(value));
        }

        @Override
        public @Nullable Object deserialize(@Nullable byte[] bytes) throws SerializationException {
            return delegate.deserialize(bytes);
        }
    }
}

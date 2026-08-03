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
package cn.ypbin.starter.messaging.sse;

import java.time.Duration;
import java.util.Optional;

/**
 * SSE 一次性订阅票据存储。
 *
 * <p>用于「先换票再订阅」流程：带鉴权凭据的普通请求签发票据（{@link #save}），随后 {@code EventSource}
 * 用票据订阅时校验并<strong>原子性消费</strong>（{@link #consume}）。票据短时有效、且只能用一次——消费即失效，
 * 杜绝重放。默认内存实现，分布式场景由 Redis 实现覆盖，多节点共享票据。</p>
 *
 * @author wenbin
 * @since 2026-08-03
 */
public interface SseTicketStore {

    /**
     * 保存一张票据，绑定用户标识与有效期。
     *
     * @param ticket 票据（不透明随机串）
     * @param userId 绑定的用户标识
     * @param ttl    有效期
     */
    void save(String ticket, String userId, Duration ttl);

    /**
     * 原子性消费票据：取出绑定的用户标识并同时删除，保证一张票据只能用一次。
     *
     * @param ticket 票据
     * @return 绑定的用户标识；票据不存在、已过期或已被消费时为 {@link Optional#empty()}
     */
    Optional<String> consume(String ticket);
}

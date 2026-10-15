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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.ypbin.starter.core.exception.BusinessException;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * {@link SseSubscribeController} 的 audience 维度用例。
 *
 * <p>核心契约：不提供 {@link SseAudienceResolver} 时<strong>行为与接入前完全一致</strong>（只按 userId 建连）；
 * 提供时按返回的每个目标各注册一条连接；返回空集合按未登录拒绝（不建「连上但永远收不到」的僵尸连接）。</p>
 *
 * @author wenbin
 * @since 2026-10-15
 */
class SseSubscribeControllerAudienceTest {

    private static final SseUserIdResolver USER = () -> Optional.of("u1");

    private static final SseTicketStore NO_TICKET = new SseTicketStore() {
        @Override
        public void save(String ticket, String userId, java.time.Duration ttl) {
            // 本用例不走票据路径
        }

        @Override
        public Optional<String> consume(String ticket) {
            return Optional.empty();
        }
    };

    @Test
    void withoutAudienceResolverOnlyUserIdIsRegistered() {
        SseEmitterManager manager = new SseEmitterManager(60_000L, 0);
        SseSubscribeController controller =
            new SseSubscribeController(manager, USER, NO_TICKET);

        controller.subscribe(null);

        assertThat(manager.onlineAudiences()).containsExactly("u1");
    }

    @Test
    void audienceResolverRegistersEveryResolvedTarget() {
        SseEmitterManager manager = new SseEmitterManager(60_000L, 0);
        SseSubscribeController controller = new SseSubscribeController(
            manager, USER, NO_TICKET, userId -> Set.of(userId, "tenant:7"));

        controller.subscribe(null);

        assertThat(manager.onlineAudiences()).containsExactlyInAnyOrder("u1", "tenant:7");
    }

    @Test
    void emptyAudienceSetIsRejectedAsUnauthorized() {
        SseEmitterManager manager = new SseEmitterManager(60_000L, 0);
        SseSubscribeController controller = new SseSubscribeController(
            manager, USER, NO_TICKET, userId -> Set.of());

        assertThatThrownBy(() -> controller.subscribe(null))
            .isInstanceOf(BusinessException.class);
        // 关键：不得留下无人匹配的僵尸连接
        assertThat(manager.onlineAudiences()).isEmpty();
    }
}

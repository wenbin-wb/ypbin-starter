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

/**
 * SSE 跨实例扇出的传输桥（发布侧）。
 *
 * <p>连接注册表是<strong>内存态</strong>，单实例内直接投递即可；多实例部署时连接散落在各 JVM，
 * 需要把一次推送经外部通道（当前实现是 Redis Pub/Sub）带给其它实例，由各实例投给<strong>本实例</strong>
 * 的连接。本接口即该传输的抽象：{@link SseEmitterManager} 只依赖本接口，无 Redis 环境时该 Bean
 * 不装配（{@code null}），管理器退化为纯单实例投递——<strong>行为与接入本能力前完全一致</strong>。</p>
 *
 * <p>实现须保证「发布 ≠ 本地投递」：本地连接由 {@link SseEmitterManager} 直接投递一次，
 * 订阅方必须丢弃自己发出的帧（见 {@link SseFanoutProtocol} 的 instanceId 定界），否则本地连接会收到两份。</p>
 *
 * @author wenbin
 * @since 2026-10-15
 */
public interface SseFanoutBridge {

    /**
     * 发布一条扇出消息到外部通道。
     *
     * @param message 扇出消息
     */
    void publish(SseFanoutMessage message);

    /**
     * 本实例的扇出标识（与帧内 instanceId 同一来源）：订阅方据此判定「这帧是不是我自己发的」。
     *
     * @return 实例标识
     */
    String instanceId();
}

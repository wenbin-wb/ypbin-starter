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
package cn.ypbin.starter.messaging.mqtt;

/**
 * MQTT 消息消费回调。
 *
 * <p>业务方实现本接口并注册为 Spring Bean，即可在应用启动后自动订阅 {@link #topic()} 指定的主题，
 * 收到消息时回调 {@link #handle(String, String)}。适合业务消息消费场景；临时订阅仍可直接使用
 * {@link MqttSubscriber#subscribe(String, java.util.function.BiConsumer)}。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
public interface MqttMessageHandler {

    /**
     * 订阅主题，支持 {@code +} / {@code #} 通配。
     *
     * @return 订阅主题
     */
    String topic();

    /**
     * 订阅 QoS。返回 {@code null} 时使用 {@code ypbin.mqtt.default-qos}。
     *
     * @return QoS，或 null 表示使用默认值
     */
    default Integer qos() {
        return null;
    }

    /**
     * 消费 MQTT 消息。
     *
     * @param topic   实际命中的主题
     * @param payload UTF-8 解码后的消息体
     */
    void handle(String topic, String payload);
}

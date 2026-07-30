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

import java.nio.charset.StandardCharsets;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

/**
 * MQTT 发布器。
 *
 * <p>对 Paho {@link IMqttClient} 的轻封装，提供按主题发布消息的便捷方法。订阅侧因回调场景多样，
 * 交由业务方直接使用注入的 {@link IMqttClient} 自行 subscribe。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class MqttPublisher {

    private final IMqttClient client;
    private final int defaultQos;

    public MqttPublisher(IMqttClient client, int defaultQos) {
        this.client = client;
        this.defaultQos = defaultQos;
    }

    /**
     * 以默认 QoS 发布消息。
     *
     * @param topic   主题
     * @param payload 消息内容
     */
    public void publish(String topic, String payload) {
        publish(topic, payload, defaultQos, false);
    }

    /**
     * 发布消息。
     *
     * @param topic    主题
     * @param payload  消息内容
     * @param qos      服务质量（0/1/2）
     * @param retained 是否保留消息
     */
    public void publish(String topic, String payload, int qos, boolean retained) {
        if (topic == null || payload == null) {
            throw new IllegalArgumentException("MQTT 主题与消息内容不能为空");
        }
        try {
            MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(qos);
            message.setRetained(retained);
            client.publish(topic, message);
        } catch (MqttException e) {
            throw new IllegalStateException("MQTT 发布失败，topic=" + topic, e);
        }
    }

    /**
     * 获取底层客户端（用于订阅等高级操作）。
     *
     * @return {@link IMqttClient}
     */
    public IMqttClient getClient() {
        return client;
    }
}

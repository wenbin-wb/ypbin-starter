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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MQTT 订阅器。
 *
 * <p>对 Paho {@link IMqttClient} 订阅侧的封装：以「主题 → 回调」方式接收消息，回调收到主题与
 * 解码为 UTF-8 字符串的消息体。内部记录已订阅主题与 QoS，在断线自动重连后重新订阅——Paho 重连后
 * 原订阅会丢失，这是常见踩坑点，本类透明处理。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
public class MqttSubscriber {

    private static final Logger log = LoggerFactory.getLogger(MqttSubscriber.class);

    private final IMqttClient client;

    private final int defaultQos;

    /** 已订阅主题 → QoS，用于重连后恢复订阅 */
    private final Map<String, Integer> subscriptions = new ConcurrentHashMap<>();

    /** 已订阅主题 → 业务回调 */
    private final Map<String, BiConsumer<String, String>> handlers = new ConcurrentHashMap<>();

    public MqttSubscriber(IMqttClient client, int defaultQos) {
        this.client = client;
        this.defaultQos = defaultQos;
    }

    /**
     * 以默认 QoS 订阅主题。
     *
     * @param topic   主题（支持 {@code +} / {@code #} 通配）
     * @param handler 消息回调，参数为 (topic, payload)
     */
    public void subscribe(String topic, BiConsumer<String, String> handler) {
        subscribe(topic, defaultQos, handler);
    }

    /**
     * 以指定 QoS 订阅主题。
     *
     * @param topic   主题（支持 {@code +} / {@code #} 通配）
     * @param qos     服务质量（0/1/2）
     * @param handler 消息回调，参数为 (topic, payload)
     */
    public void subscribe(String topic, int qos, BiConsumer<String, String> handler) {
        if (topic == null || handler == null) {
            throw new IllegalArgumentException("MQTT 订阅主题与回调不能为空");
        }
        try {
            subscriptions.put(topic, qos);
            handlers.put(topic, handler);
            client.subscribe(topic, qos, (t, msg) ->
                handler.accept(t, new String(msg.getPayload(), StandardCharsets.UTF_8)));
        } catch (MqttException e) {
            throw new IllegalStateException("MQTT 订阅失败，topic=" + topic, e);
        }
    }

    /**
     * 取消订阅主题。
     *
     * @param topic 主题
     */
    public void unsubscribe(String topic) {
        try {
            subscriptions.remove(topic);
            handlers.remove(topic);
            client.unsubscribe(topic);
        } catch (MqttException e) {
            throw new IllegalStateException("MQTT 取消订阅失败，topic=" + topic, e);
        }
    }

    /**
     * 重新订阅所有已登记主题。供断线重连后恢复订阅（由自动配置在重连回调中调用）。
     */
    public void resubscribeAll() {
        subscriptions.forEach((topic, qos) -> {
            BiConsumer<String, String> handler = handlers.get(topic);
            if (handler == null) {
                return;
            }
            try {
                client.subscribe(topic, qos, (t, msg) ->
                    handler.accept(t, new String(msg.getPayload(), StandardCharsets.UTF_8)));
                log.debug("[ypbin-starter] MQTT 重连后恢复订阅：topic={}, qos={}", topic, qos);
            } catch (MqttException e) {
                log.warn("[ypbin-starter] MQTT 重连后恢复订阅失败：topic={}", topic, e);
            }
        });
    }

    /**
     * 当前已登记的订阅主题数。
     *
     * @return 订阅数量
     */
    public int subscriptionCount() {
        return subscriptions.size();
    }
}

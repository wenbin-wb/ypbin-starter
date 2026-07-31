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

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.util.StringUtils;

/**
 * MQTT 消息消费回调注册器。
 *
 * @author wenbin
 * @since 2026-07-31
 */
public class MqttMessageHandlerRegistrar implements InitializingBean, Ordered {

    private static final Logger log = LoggerFactory.getLogger(MqttMessageHandlerRegistrar.class);

    private final MqttSubscriber subscriber;

    private final List<MqttMessageHandler> handlers;

    private final int defaultQos;

    public MqttMessageHandlerRegistrar(MqttSubscriber subscriber, List<MqttMessageHandler> handlers, int defaultQos) {
        this.subscriber = subscriber;
        this.handlers = handlers;
        this.defaultQos = defaultQos;
    }

    @Override
    public void afterPropertiesSet() {
        if (handlers == null || handlers.isEmpty()) {
            return;
        }
        handlers.stream()
            .sorted(AnnotationAwareOrderComparator.INSTANCE)
            .forEach(this::subscribeHandler);
    }

    private void subscribeHandler(MqttMessageHandler handler) {
        String topic = handler.topic();
        if (!StringUtils.hasText(topic)) {
            throw new IllegalArgumentException("MQTT 消费回调 topic 不能为空：" + handler.getClass().getName());
        }
        Integer qos = handler.qos();
        int actualQos = qos == null ? defaultQos : qos;
        subscriber.subscribe(topic.trim(), actualQos, handler::handle);
        log.info("[ypbin-starter] MQTT 消费回调已订阅：handler={}, topic={}, qos={}",
            handler.getClass().getName(), topic.trim(), actualQos);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}

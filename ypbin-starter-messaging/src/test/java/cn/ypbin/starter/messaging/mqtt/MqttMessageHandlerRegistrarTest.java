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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * {@link MqttMessageHandlerRegistrar} 单元测试。
 *
 * @author wenbin
 * @since 2026-07-31
 */
class MqttMessageHandlerRegistrarTest {

    @Test
    void afterPropertiesSet_shouldSubscribeAllHandlers() throws Exception {
        IMqttClient client = Mockito.mock(IMqttClient.class);
        MqttSubscriber subscriber = new MqttSubscriber(client, 1);
        List<String> received = new ArrayList<>();
        MqttMessageHandler handler = new MqttMessageHandler() {
            @Override
            public String topic() {
                return "device/+/up";
            }

            @Override
            public Integer qos() {
                return 2;
            }

            @Override
            public void handle(String topic, String payload) {
                received.add(topic + "=" + payload);
            }
        };

        new MqttMessageHandlerRegistrar(subscriber, List.of(handler), 1).afterPropertiesSet();

        verify(client).subscribe(eq("device/+/up"), eq(2), any(IMqttMessageListener.class));
    }

    @Test
    void afterPropertiesSet_shouldRejectBlankTopic() {
        IMqttClient client = Mockito.mock(IMqttClient.class);
        MqttSubscriber subscriber = new MqttSubscriber(client, 1);
        MqttMessageHandler handler = new MqttMessageHandler() {
            @Override
            public String topic() {
                return " ";
            }

            @Override
            public void handle(String topic, String payload) {
            }
        };

        assertThatThrownBy(() -> new MqttMessageHandlerRegistrar(subscriber, List.of(handler), 1).afterPropertiesSet())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("topic 不能为空");
    }
}

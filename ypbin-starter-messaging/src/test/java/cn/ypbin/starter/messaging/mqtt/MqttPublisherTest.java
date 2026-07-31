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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * {@link MqttPublisher} 单元测试（mock 客户端）。
 *
 * @author wenbin
 * @since 2026-07-31
 */
class MqttPublisherTest {

    @Test
    void publish_shouldSendWithDefaultQos() throws Exception {
        IMqttClient client = Mockito.mock(IMqttClient.class);
        MqttPublisher publisher = new MqttPublisher(client, 1);

        publisher.publish("sensor/temp", "25.5");

        ArgumentCaptor<MqttMessage> captor = ArgumentCaptor.forClass(MqttMessage.class);
        verify(client).publish(eq("sensor/temp"), captor.capture());
        MqttMessage sent = captor.getValue();
        assertThat(new String(sent.getPayload(), StandardCharsets.UTF_8)).isEqualTo("25.5");
        assertThat(sent.getQos()).isEqualTo(1);
    }

    @Test
    void publish_shouldHonorExplicitQosAndRetained() throws Exception {
        IMqttClient client = Mockito.mock(IMqttClient.class);
        MqttPublisher publisher = new MqttPublisher(client, 0);

        publisher.publish("cfg/topic", "payload", 2, true);

        ArgumentCaptor<MqttMessage> captor = ArgumentCaptor.forClass(MqttMessage.class);
        verify(client).publish(eq("cfg/topic"), captor.capture());
        assertThat(captor.getValue().getQos()).isEqualTo(2);
        assertThat(captor.getValue().isRetained()).isTrue();
    }

    @Test
    void publish_shouldRejectNullArgs() {
        IMqttClient client = Mockito.mock(IMqttClient.class);
        MqttPublisher publisher = new MqttPublisher(client, 1);

        assertThatThrownBy(() -> publisher.publish(null, "x"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> publisher.publish("t", null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publish_shouldWrapMqttException() throws Exception {
        IMqttClient client = Mockito.mock(IMqttClient.class);
        Mockito.doThrow(new org.eclipse.paho.client.mqttv3.MqttException(0))
            .when(client).publish(any(), any(MqttMessage.class));
        MqttPublisher publisher = new MqttPublisher(client, 1);

        assertThatThrownBy(() -> publisher.publish("t", "p"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("MQTT 发布失败");
    }
}

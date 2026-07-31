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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * {@link MqttSubscriber} 单元测试（mock 客户端）。
 *
 * @author wenbin
 * @since 2026-07-31
 */
class MqttSubscriberTest {

    @Test
    void subscribe_shouldRegisterTopicAndTrack() throws Exception {
        IMqttClient client = Mockito.mock(IMqttClient.class);
        MqttSubscriber subscriber = new MqttSubscriber(client, 1);

        subscriber.subscribe("device/+/up", (topic, payload) -> { });

        verify(client).subscribe(eq("device/+/up"), eq(1), any(IMqttMessageListener.class));
        assertThat(subscriber.subscriptionCount()).isEqualTo(1);
    }

    @Test
    void unsubscribe_shouldRemoveTracking() throws Exception {
        IMqttClient client = Mockito.mock(IMqttClient.class);
        MqttSubscriber subscriber = new MqttSubscriber(client, 1);
        subscriber.subscribe("t/1", (topic, payload) -> { });

        subscriber.unsubscribe("t/1");

        verify(client).unsubscribe("t/1");
        assertThat(subscriber.subscriptionCount()).isZero();
    }

    @Test
    void resubscribeAll_shouldReapplyAllSubscriptions() throws Exception {
        IMqttClient client = Mockito.mock(IMqttClient.class);
        MqttSubscriber subscriber = new MqttSubscriber(client, 2);
        subscriber.subscribe("a", (t, p) -> { });
        subscriber.subscribe("b", (t, p) -> { });

        subscriber.resubscribeAll();

        // 首次订阅 2 次 + 重订阅 2 次 = 每个主题共 2 次
        verify(client, times(2)).subscribe(eq("a"), anyInt(), any(IMqttMessageListener.class));
        verify(client, times(2)).subscribe(eq("b"), anyInt(), any(IMqttMessageListener.class));
    }

    @Test
    void subscribe_shouldRejectNullArgs() {
        IMqttClient client = Mockito.mock(IMqttClient.class);
        MqttSubscriber subscriber = new MqttSubscriber(client, 1);

        assertThatThrownBy(() -> subscriber.subscribe(null, (t, p) -> { }))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> subscriber.subscribe("t", null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}

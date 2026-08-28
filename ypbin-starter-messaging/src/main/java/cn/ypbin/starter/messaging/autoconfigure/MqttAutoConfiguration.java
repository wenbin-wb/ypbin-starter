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
package cn.ypbin.starter.messaging.autoconfigure;

import cn.ypbin.starter.messaging.mqtt.MqttMessageHandler;
import cn.ypbin.starter.messaging.mqtt.MqttMessageHandlerRegistrar;
import cn.ypbin.starter.messaging.mqtt.MqttProperties;
import cn.ypbin.starter.messaging.mqtt.MqttPublisher;
import cn.ypbin.starter.messaging.mqtt.MqttSubscriber;
import java.util.List;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttClientPersistence;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * MQTT 自动配置。
 *
 * <p>仅在引入 Paho 客户端且 {@code ypbin.mqtt.enabled=true} 时生效。创建并连接 Paho 客户端，
 * 装配 {@link MqttPublisher}。客户端以 destroy 方法在容器关闭时断开。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@AutoConfiguration
@ConditionalOnClass(MqttClient.class)
@ConditionalOnProperty(prefix = "ypbin.mqtt", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(MqttProperties.class)
public class MqttAutoConfiguration {

    // 不用 destroyMethod="close"：Paho 的 close() 在仍连接时抛 MqttException(32100)。
    // 由下方 DisposableBean 先 disconnect 再 close，优雅释放连接与持久化资源。
    @Bean
    @ConditionalOnMissingBean
    public IMqttClient mqttClient(MqttProperties properties) throws Exception {
        String clientId = (properties.getClientId() != null && !properties.getClientId().isBlank())
            ? properties.getClientId()
            : MqttClient.generateClientId();

        // 配置了持久化目录则用文件持久化（重启后 QoS1/2 未确认消息不丢），否则内存
        MqttClientPersistence persistence =
            (properties.getPersistenceDir() != null && !properties.getPersistenceDir().isBlank())
                ? new MqttDefaultFilePersistence(properties.getPersistenceDir())
                : new MemoryPersistence();
        MqttClient client = new MqttClient(properties.getUrl(), clientId, persistence);

        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(properties.isAutomaticReconnect());
        options.setMaxReconnectDelay(properties.getMaxReconnectDelay());
        options.setCleanSession(properties.isCleanSession());
        options.setConnectionTimeout(properties.getConnectionTimeout());
        options.setKeepAliveInterval(properties.getKeepAliveInterval());
        options.setMaxInflight(properties.getMaxInflight());
        if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
            options.setUserName(properties.getUsername());
        }
        if (properties.getPassword() != null && !properties.getPassword().isBlank()) {
            options.setPassword(properties.getPassword().toCharArray());
        }
        client.connect(options);
        return client;
    }

    @Bean
    @ConditionalOnMissingBean
    public MqttPublisher mqttPublisher(IMqttClient client, MqttProperties properties) {
        return new MqttPublisher(client, properties.getDefaultQos());
    }

    // 断线自动重连后 Paho 会丢失原订阅，这里在 connectComplete(reconnect=true) 时恢复所有订阅。
    @Bean
    @ConditionalOnMissingBean
    public MqttSubscriber mqttSubscriber(IMqttClient client, MqttProperties properties) {
        MqttSubscriber subscriber = new MqttSubscriber(client, properties.getDefaultQos());
        client.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverUri) {
                if (reconnect) {
                    subscriber.resubscribeAll();
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                // 交由 Paho 自动重连；恢复订阅在 connectComplete 中处理
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                // 具体主题回调由 subscribe(...) 时注册的 IMqttMessageListener 处理，此处不用兜底
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // 发布确认，无需处理
            }
        });
        return subscriber;
    }

    /**
     * 自动注册业务方声明的 MQTT 消费回调 Bean。业务只需实现 {@link MqttMessageHandler}，无需再手动调用
     * subscribe，即可在容器启动后完成订阅并进入消费回调。
     */
    @Bean
    @ConditionalOnMissingBean
    public MqttMessageHandlerRegistrar mqttMessageHandlerRegistrar(MqttSubscriber subscriber,
            List<MqttMessageHandler> handlers, MqttProperties properties) {
        return new MqttMessageHandlerRegistrar(subscriber, handlers, properties.getDefaultQos());
    }

    /**
     * MQTT 客户端优雅关闭：先 disconnect 再 close，避免 Paho 在仍连接时 close() 抛
     * MqttException(32100)，并释放持久化资源。
     */
    @Bean
    public DisposableBean mqttClientShutdown(IMqttClient client) {
        return () -> {
            try {
                if (client.isConnected()) {
                    client.disconnect();
                }
            } finally {
                client.close();
            }
        };
    }
}

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
package cn.ypbin.starter.core.blocklist;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * 客户端 IP 解析用例。
 *
 * @author wenbin
 * @since 2026-11-04
 */
class ClientIpResolverTest {

    @Test
    @DisplayName("无转发头时使用直连地址")
    void usesRemoteAddressWithoutForwarding() {
        ClientIpResolver resolver = new ClientIpResolver(List.of("10.0.0.1"));

        assertThat(resolver.resolve("203.0.113.9", null)).isEqualTo("203.0.113.9");
        assertThat(resolver.resolve("203.0.113.9", " ")).isEqualTo("203.0.113.9");
    }

    @Test
    @DisplayName("非可信直连地址忽略伪造的 XFF")
    void ignoresForwardingFromUntrustedPeer() {
        ClientIpResolver resolver = new ClientIpResolver(List.of("10.0.0.1"));

        assertThat(resolver.resolve("203.0.113.9", "1.2.3.4")).isEqualTo("203.0.113.9");
    }

    @Test
    @DisplayName("可信代理多跳时从右往左取第一个不可信地址")
    void picksFirstUntrustedHopFromRight() {
        ClientIpResolver resolver = new ClientIpResolver(List.of("10.0.0.0/8"));

        assertThat(resolver.resolve("10.0.0.1", "198.51.100.7, 203.0.113.9, 10.0.0.2"))
            .isEqualTo("203.0.113.9");
    }

    @Test
    @DisplayName("XFF 中的域名被忽略，不触发解析")
    void ignoresDomainInForwardedChain() {
        ClientIpResolver resolver = new ClientIpResolver(List.of("10.0.0.1"));

        assertThat(resolver.resolve("10.0.0.1", "face.de, 1.1.1.1")).isEqualTo("1.1.1.1");
    }

    @Test
    @DisplayName("XFF 缺失时使用 X-Real-IP")
    void fallsBackToRealIp() {
        ClientIpResolver resolver = new ClientIpResolver(List.of("10.0.0.1"));

        assertThat(resolver.resolve("10.0.0.1", null, "198.51.100.7")).isEqualTo("198.51.100.7");
    }

    @Test
    @DisplayName("未配置可信代理时，同机反代按直连回环地址处理（不会静默全放行）")
    void loopbackPeerWithoutTrustedProxiesKeepsLoopback() {
        ClientIpResolver resolver = new ClientIpResolver(List.of());

        assertThat(resolver.resolve("127.0.0.1", "203.0.113.9")).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("配置了可信代理但直连地址不命中时告警一次")
    void warnsWhenTrustedProxyMismatch() {
        ClientIpResolver resolver = new ClientIpResolver(List.of("10.0.0.0/8"));
        Logger logger = (Logger) LoggerFactory.getLogger(ClientIpResolver.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThat(resolver.resolve("203.0.113.7", "1.1.1.1")).isEqualTo("203.0.113.7");
            assertThat(resolver.resolve("203.0.113.7", "1.1.1.1")).isEqualTo("203.0.113.7");
        } finally {
            logger.detachAppender(appender);
        }

        long warnCount = appender.list.stream()
            .filter(event -> event.getLevel() == Level.WARN)
            .filter(event -> event.getFormattedMessage().contains("trusted-proxies"))
            .count();
        assertThat(warnCount).isEqualTo(1L);
    }

    @Test
    @DisplayName("直连地址为空返回 unknown")
    void unknownWhenRemoteMissing() {
        ClientIpResolver resolver = new ClientIpResolver(List.of());

        assertThat(resolver.resolve(null, "1.2.3.4")).isEqualTo("unknown");
    }
}

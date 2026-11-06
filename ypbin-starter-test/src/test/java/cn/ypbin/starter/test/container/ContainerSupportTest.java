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
package cn.ypbin.starter.test.container;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import org.junit.jupiter.api.Test;

/**
 * Nacos 宿主端口分配的约束用例。
 *
 * <p>端口对是 Nacos 容器能连通的前提（客户端固定按「服务端口 + 1000」连 gRPC），
 * 且探测不准会把偶发端口冲突放大成测试套件必现失败，故这里直接锁定两条不变量：
 * 成对偏移、以及「能被别人占用」的端口必须报告为不可用。</p>
 *
 * @author wenbin
 * @since 2026-11-06
 */
class ContainerSupportTest {

    private static final int BASE_PORT = 18848;

    private static final int GRPC_PORT_OFFSET = 1000;

    private static final int SCAN_RANGE = 200;

    @Test
    void freePortPairShouldCarryGrpcOffsetAndBeBindable() {
        int base = ContainerSupport.findFreePortPair();

        assertThat(base).isBetween(BASE_PORT, BASE_PORT + SCAN_RANGE - 1);
        assertThat(ContainerSupport.isPortFree(base)).as("服务端口应可绑定").isTrue();
        assertThat(ContainerSupport.isPortFree(base + GRPC_PORT_OFFSET)).as("gRPC 端口应可绑定").isTrue();
    }

    @Test
    void occupiedPortShouldNotBeReportedFree() throws IOException {
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress("127.0.0.1", 0));
            int port = socket.getLocalPort();

            assertThat(ContainerSupport.isPortFree(port)).as("被监听的端口不得报告为空闲").isFalse();
        }
    }

    @Test
    void pairScanShouldSkipBaseWhoseGrpcPortIsOccupied() throws IOException {
        int occupiedBase = ContainerSupport.findFreePortPair();
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress("127.0.0.1", occupiedBase + GRPC_PORT_OFFSET));

            assertThat(ContainerSupport.findFreePortPair())
                .as("gRPC 端口被占用的候选基址必须整对跳过")
                .isNotEqualTo(occupiedBase);
        }
    }
}

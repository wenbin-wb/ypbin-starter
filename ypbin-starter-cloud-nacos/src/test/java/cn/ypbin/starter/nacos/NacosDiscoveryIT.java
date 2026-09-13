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
package cn.ypbin.starter.nacos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import cn.ypbin.starter.test.container.ContainerSupport;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Nacos 注册发现与配置中心真实集成测试（双模式）。
 *
 * <p>仅在 {@code -Pit}（failsafe）下执行。两种运行模式，按优先级选择：</p>
 * <ol>
 *   <li>外部环境：给定 {@code -Dypbin.it.nacos-addr=host:port}，直连已部署的 Nacos；</li>
 *   <li>容器模式：未给外部地址且本机 Docker 可用时，用 Testcontainers 拉起 Nacos
 *   （镜像与部署保持一致，取 {@link ContainerSupport#NACOS_IMAGE}）。</li>
 * </ol>
 * <p>两者都不满足时通过 {@link Assumptions} 优雅跳过，不判失败。</p>
 *
 * <p><strong>容器模式的端口约定</strong>：Nacos 客户端固定按「服务端口 + 1000」连接 gRPC（3.x 客户端
 * 无端口偏移配置项），而 Testcontainers 默认把 8848/9848 映射到互不相干的两个随机端口，
 * 客户端必然连不上并停在 {@code Client not connected, current status:STARTING}。
 * 因此这里显式把 8848/9848 绑定到一对相隔 1000 的连续空闲宿主端口，维持服务端口与 gRPC 端口的
 * 推导关系。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
class NacosDiscoveryIT {

    private static final String EXTERNAL_ADDR_PROP = "ypbin.it.nacos-addr";

    /** 容器模式起始宿主端口；需与其 +1000 的 gRPC 端口同时空闲 */
    private static final int CONTAINER_BASE_PORT = 18848;

    /** Nacos 客户端固定按「服务端口 + 1000」连接 gRPC，故此偏移必须与端口绑定保持一致 */
    private static final int GRPC_PORT_OFFSET = 1000;

    private static final SecureRandom RANDOM = new SecureRandom();

    private static GenericContainer<?> nacosContainer;

    private static String serverAddr;

    @BeforeAll
    static void resolveNacos() {
        String external = System.getProperty(EXTERNAL_ADDR_PROP);
        if (external != null && !external.isBlank()) {
            serverAddr = external.trim();
            return;
        }
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
            "未提供 -D" + EXTERNAL_ADDR_PROP + " 且本机无 Docker，跳过 Nacos 集成测试");
        // 关键：客户端按 serverAddr 端口 +1000 推导 gRPC 端口，而 Testcontainers 默认给 8848/9848
        // 分配互不相干的随机端口，客户端必然连不上（表现为 Nacos Client not connected, status:STARTING）。
        // 故显式绑定「宿主 base / base+1000」两个连续空闲端口，保持与服务端口关系一致。
        int basePort = findFreePortPair();
        nacosContainer = new GenericContainer<>(DockerImageName.parse(ContainerSupport.NACOS_IMAGE))
            .withEnv("MODE", "standalone")
            .withEnv("PREFER_HOST_MODE", "hostname")
            // Nacos 3 镜像启动脚本强制要求鉴权三件套（缺失即 exit 255）；显式关闭鉴权后
            // 开放 API 无需用户名口令，测试不必再维护凭据。
            .withEnv("NACOS_AUTH_ENABLE", "false")
            .withEnv("NACOS_AUTH_TOKEN", randomBase64Token())
            .withEnv("NACOS_AUTH_IDENTITY_KEY", "serverIdentity")
            .withEnv("NACOS_AUTH_IDENTITY_VALUE", randomHexIdentity())
            .withExposedPorts(8848, 9848)
            .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withPortBindings(
                new PortBinding(Ports.Binding.bindPort(basePort), new ExposedPort(8848)),
                new PortBinding(Ports.Binding.bindPort(basePort + GRPC_PORT_OFFSET), new ExposedPort(9848))))
            .waitingFor(Wait.forListeningPorts(8848, 9848).withStartupTimeout(Duration.ofMinutes(3)));
        nacosContainer.start();
        serverAddr = nacosContainer.getHost() + ":" + basePort;
    }

    /** Nacos 要求 Base64 且解码后 ≥32 字节的鉴权 token */
    private static String randomBase64Token() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    /** 鉴权身份值，等同于部署脚本的 rand_hex 32 */
    private static String randomHexIdentity() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /** 找到一对连续相隔 {@link #GRPC_PORT_OFFSET} 且都空闲的宿主端口 */
    private static int findFreePortPair() {
        for (int base = CONTAINER_BASE_PORT; base < CONTAINER_BASE_PORT + 200; base++) {
            if (isPortFree(base) && isPortFree(base + GRPC_PORT_OFFSET)) {
                return base;
            }
        }
        throw new IllegalStateException("未找到可用的 Nacos 宿主端口对（起始 " + CONTAINER_BASE_PORT + "）");
    }

    private static boolean isPortFree(int port) {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress("127.0.0.1", port));
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    @AfterAll
    static void stopNacos() {
        if (nacosContainer != null) {
            nacosContainer.stop();
        }
    }

    private Properties props() {
        Properties p = new Properties();
        p.put("serverAddr", serverAddr);
        return p;
    }

    @Test
    void shouldRegisterAndDiscoverInstance() throws Exception {
        NamingService naming = NacosFactory.createNamingService(props());
        String service = "ypbin-it-service";
        naming.registerInstance(service, "10.10.10.10", 8888);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            List<Instance> instances = naming.getAllInstances(service);
            assertThat(instances).extracting(Instance::getIp).contains("10.10.10.10");
        });

        naming.deregisterInstance(service, "10.10.10.10", 8888);
        naming.shutDown();
    }

    @Test
    void shouldPublishAndReadConfig() throws Exception {
        ConfigService config = NacosFactory.createConfigService(props());
        String dataId = "ypbin-it.yaml";
        String group = "DEFAULT_GROUP";

        boolean published = config.publishConfig(dataId, group, "ypbin:\n  it: true");
        assertThat(published).isTrue();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            String content = config.getConfig(dataId, group, 3000);
            assertThat(content).contains("it: true");
        });

        config.removeConfig(dataId, group);
        config.shutDown();
    }
}

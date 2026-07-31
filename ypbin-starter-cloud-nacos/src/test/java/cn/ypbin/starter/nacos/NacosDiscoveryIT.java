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

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import java.time.Duration;
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
 * <p>仅在 {@code -Pit} 下参与构建。两种运行模式，按优先级选择：</p>
 * <ol>
 *   <li>外部环境：给定 {@code -Dypbin.it.nacos-addr=host:8848}，直连已部署的 Nacos，跳过容器；</li>
 *   <li>本地 Docker：未给外部地址且本机 Docker 可用时，用 Testcontainers 自动拉起 Nacos 容器。</li>
 * </ol>
 * <p>两者都不满足时通过 {@link Assumptions} 优雅跳过，不判失败。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
class NacosDiscoveryIT {

    private static final String EXTERNAL_ADDR_PROP = "ypbin.it.nacos-addr";

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
        nacosContainer = new GenericContainer<>(DockerImageName.parse("nacos/nacos-server:v2.4.3"))
            .withEnv("MODE", "standalone")
            .withExposedPorts(8848, 9848)
            .waitingFor(Wait.forHttp("/nacos/v1/console/health/readiness").forPort(8848)
                .withStartupTimeout(Duration.ofMinutes(3)));
        nacosContainer.start();
        serverAddr = nacosContainer.getHost() + ":" + nacosContainer.getMappedPort(8848);
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

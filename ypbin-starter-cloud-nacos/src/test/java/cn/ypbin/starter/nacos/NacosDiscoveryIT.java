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

import cn.ypbin.starter.test.condition.EnabledIfNacosAvailable;
import cn.ypbin.starter.test.container.ContainerSupport;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Nacos 注册发现与配置中心真实集成测试。
 *
 * <p>仅在 {@code -Pit}（failsafe）下执行。地址解析交给共享测试基座
 * {@link ContainerSupport#nacosServerAddress()}：外部地址优先
 * （{@code -Dypbin.it.nacos-addr=host:port} 或 {@code YPBIN_TEST_NACOS_ADDR}），
 * 否则用 Testcontainers 拉起 Nacos 容器；两者都不可用时由 {@link EnabledIfNacosAvailable} 跳过。
 * 容器模式的端口约定与鉴权三件套等细节见 {@link ContainerSupport}。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
@EnabledIfNacosAvailable
class NacosDiscoveryIT {

    private static String serverAddr;

    @BeforeAll
    static void resolveNacos() {
        serverAddr = ContainerSupport.nacosServerAddress();
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

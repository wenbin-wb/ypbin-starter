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
package cn.ypbin.starter.core.diagnostic;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ClassUtils;

/**
 * ypbin-starter 自诊断与装配自省 Actuator 端点。
 *
 * <p>通过 {@code /actuator/ypbin} 端点输出当前应用中激活的 Starter 列表、
 * 运行基线版本与关键基础设施装配状态，方便排查组件加载情况。</p>
 *
 * @author wenbin
 * @since 2026-08-28
 */
@Endpoint(id = "ypbin")
public class StarterDiagnosticEndpoint {

    private static final String AUTO_CONFIGURATION_IMPORTS =
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    private final ApplicationContext applicationContext;

    public StarterDiagnosticEndpoint(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 读取 starter 诊断全景信息。
     *
     * @return 诊断数据 Map
     */
    @ReadOperation
    public Map<String, Object> diagnosticInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("framework", "ypbin-starter");
        info.put("version", resolveStarterVersion());
        info.put("javaVersion", System.getProperty("java.version"));
        info.put("springBootVersion", SpringBootVersion.getVersion());

        List<String> autoConfigs = resolveAutoConfigurations();
        info.put("activeAutoConfigurations", autoConfigs);
        info.put("activeConfigurationCount", autoConfigs.size());
        info.put("features", probeFeatures());

        return info;
    }

    private String resolveStarterVersion() {
        Package pkg = StarterDiagnosticEndpoint.class.getPackage();
        if (pkg != null && pkg.getImplementationVersion() != null) {
            return pkg.getImplementationVersion();
        }
        return "1.4.0";
    }

    private List<String> resolveAutoConfigurations() {
        TreeSet<String> configs = new TreeSet<>();
        try {
            ClassLoader classLoader = applicationContext != null ? applicationContext.getClassLoader() : null;
            if (classLoader == null) {
                classLoader = ClassUtils.getDefaultClassLoader();
            }
            if (classLoader != null) {
                Enumeration<URL> urls = classLoader.getResources(AUTO_CONFIGURATION_IMPORTS);
                while (urls.hasMoreElements()) {
                    URL url = urls.nextElement();
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            line = line.trim();
                            if (!line.isEmpty() && !line.startsWith("#") && line.startsWith("cn.ypbin.starter")) {
                                configs.add(line);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // 容错降级返回已有结果
        }
        return new ArrayList<>(configs);
    }

    private Map<String, Object> probeFeatures() {
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("security", isPresent("cn.dev33.satoken.SaManager"));
        features.put("ai", isPresent("org.springframework.ai.chat.client.ChatClient"));
        features.put("excel", isPresent("org.apache.fesod.sheet.FesodSheet"));
        features.put("redis", isPresent("org.springframework.data.redis.core.RedisTemplate"));
        features.put("mybatisPlus", isPresent("com.baomidou.mybatisplus.core.MybatisConfiguration"));
        features.put("tenant", isPresent("cn.ypbin.starter.tenant.core.TenantContext"));
        features.put("gateway", isPresent("org.springframework.cloud.gateway.config.GatewayAutoConfiguration"));
        features.put("license", isPresent("cn.ypbin.starter.license.annotation.LicenseCheck"));
        return Collections.unmodifiableMap(features);
    }

    private boolean isPresent(String className) {
        ClassLoader cl = applicationContext != null ? applicationContext.getClassLoader() : ClassUtils.getDefaultClassLoader();
        return ClassUtils.isPresent(className, cl);
    }
}

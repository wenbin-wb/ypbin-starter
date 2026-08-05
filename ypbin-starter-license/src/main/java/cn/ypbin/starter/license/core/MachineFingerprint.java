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
package cn.ypbin.starter.license.core;

import cn.ypbin.starter.tools.crypto.Sm3Utils;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * 机器指纹采集器。
 *
 * <p>采集运行环境的稳定硬件/系统特征（网卡 MAC、CPU 架构与核数、操作系统名称、主机名），
 * 排序去重后拼接，经国密 SM3 摘要得到不可逆的 64 位十六进制指纹。指纹用于将授权绑定到具体机器，
 * 防止 jar 包被复制到其它环境运行。</p>
 *
 * <p>采集失败的单项会被跳过而不是静默用空值顶替：只要能取到任一稳定特征即可生成指纹；
 * 若一项特征都取不到，则抛出异常以暴露环境异常，而非返回一个所有机器都相同的空指纹。</p>
 *
 * @author wenbin
 * @since 2026-08-05
 */
public final class MachineFingerprint {

    private MachineFingerprint() {
    }

    /**
     * 生成当前机器的指纹。
     *
     * @return 64 位十六进制 SM3 指纹（小写）
     */
    public static String current() {
        List<String> features = collectFeatures();
        if (features.isEmpty()) {
            throw new IllegalStateException("未能采集到任何机器特征，无法生成机器指纹");
        }
        Collections.sort(features);
        String raw = String.join("|", features);
        return Sm3Utils.digestHex(raw);
    }

    /**
     * 采集稳定的机器特征项。
     *
     * @return 特征列表（可能为空）
     */
    private static List<String> collectFeatures() {
        List<String> features = new ArrayList<>();
        collectMacAddresses(features);
        collectSystemProperties(features);
        collectHostName(features);
        return features;
    }

    /**
     * 采集所有非回环、非虚拟网卡的 MAC 地址。
     *
     * @param features 特征收集容器
     */
    private static void collectMacAddresses(List<String> features) {
        try {
            var interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || ni.isVirtual() || !ni.isUp()) {
                    continue;
                }
                byte[] mac = ni.getHardwareAddress();
                if (mac != null && mac.length > 0) {
                    features.add("MAC:" + HexFormat.of().formatHex(mac));
                }
            }
        } catch (Exception e) {
            // 网卡枚举失败不影响其它特征采集；指纹由剩余特征生成，缺项会导致指纹变化从而拒绝授权（暴露而非掩盖）
        }
    }

    /**
     * 采集操作系统与 CPU 相关的系统属性。
     *
     * @param features 特征收集容器
     */
    private static void collectSystemProperties(List<String> features) {
        addIfPresent(features, "OS", System.getProperty("os.name"));
        addIfPresent(features, "ARCH", System.getProperty("os.arch"));
        features.add("CPU:" + Runtime.getRuntime().availableProcessors());
    }

    /**
     * 采集主机名。
     *
     * @param features 特征收集容器
     */
    private static void collectHostName(List<String> features) {
        try {
            addIfPresent(features, "HOST", InetAddress.getLocalHost().getHostName());
        } catch (Exception e) {
            // 主机名获取失败时跳过，由其它特征参与指纹计算
        }
    }

    /**
     * 当值非空时以「前缀:值」形式加入特征列表。
     *
     * @param features 特征收集容器
     * @param prefix   特征前缀
     * @param value    特征值
     */
    private static void addIfPresent(List<String> features, String prefix, String value) {
        if (value != null && !value.isBlank()) {
            features.add(prefix + ":" + value.trim().toLowerCase(Locale.ROOT));
        }
    }
}

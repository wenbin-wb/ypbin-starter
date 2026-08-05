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

import cn.ypbin.starter.core.util.SpringUtils;

/**
 * License 授权静态工具。
 *
 * <p>面向非 Spring 托管场景（工具方法、静态上下文）提供授权断言，内部委托容器中的
 * {@link LicenseManager} 单例。首次调用时经 {@link SpringUtils} 懒获取并缓存该 Bean 引用。</p>
 *
 * <p>Spring 托管组件仍应优先直接注入 {@link LicenseManager} 或使用 {@code @LicenseCheck} 注解，
 * 语义更清晰、更易测试；本工具仅用于拿不到注入的场景。需引入 {@code ypbin-starter-license}
 * 且开启 {@code ypbin.license.enabled=true}。</p>
 *
 * @author wenbin
 * @since 2026-08-05
 */
public final class LicenseVerifier {

    private static volatile LicenseManager manager;

    private LicenseVerifier() {
    }

    /**
     * 懒获取容器中的 {@link LicenseManager} Bean（双重检查，线程安全）。
     *
     * @return 授权状态机实例
     */
    private static LicenseManager manager() {
        if (manager == null) {
            synchronized (LicenseVerifier.class) {
                if (manager == null) {
                    manager = SpringUtils.getBean(LicenseManager.class);
                }
            }
        }
        return manager;
    }

    /**
     * 断言当前授权可用（合法或宽限期内）。
     */
    public static void check() {
        manager().assertUsable();
    }

    /**
     * 断言指定功能模块已授权。
     *
     * @param module 模块标识
     */
    public static void checkModule(String module) {
        manager().assertModule(module);
    }

    /**
     * 断言某业务额度未超限。
     *
     * @param key     业务参数名
     * @param current 当前使用量
     */
    public static void checkQuota(String key, long current) {
        manager().assertQuota(key, current);
    }

    /**
     * 当前授权状态。
     *
     * @return 授权状态
     */
    public static LicenseStatus status() {
        return manager().getStatus();
    }

    /**
     * 当前授权内容。
     *
     * @return 授权内容；未加载时为 {@code null}
     */
    public static LicenseContent content() {
        return manager().getContent();
    }
}

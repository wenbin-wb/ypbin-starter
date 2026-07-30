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
package cn.ypbin.starter.core.constant;

/**
 * 配置属性前缀常量。
 *
 * <p>所有模块的 {@code @ConfigurationProperties} 前缀集中于此，统一根前缀 {@code ypbin}，
 * 层级用 {@link StringConstants#DOT} 拼接，避免各处硬编码字符串导致前缀漂移。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public interface PropertiesConstants {

    /** 全局根前缀 */
    String YPBIN = "ypbin";

    /** 通用启用开关后缀，配合 {@code @ConditionalOnProperty} 使用 */
    String ENABLED = "enabled";

    /** Web 模块 */
    String WEB = YPBIN + StringConstants.DOT + "web";

    /** 跨域 */
    String CORS = WEB + StringConstants.DOT + "cors";

    /** XSS 过滤 */
    String XSS = WEB + StringConstants.DOT + "xss";

    /** 数据模块 */
    String DATA = YPBIN + StringConstants.DOT + "data";

    /** 缓存模块 */
    String CACHE = YPBIN + StringConstants.DOT + "cache";

    /** 安全/认证模块 */
    String SECURITY = YPBIN + StringConstants.DOT + "security";

    /** 日志模块 */
    String LOG = YPBIN + StringConstants.DOT + "log";

    /** API 文档模块 */
    String API_DOC = YPBIN + StringConstants.DOT + "api-doc";

    /** 文件存储模块 */
    String STORAGE = YPBIN + StringConstants.DOT + "storage";
}

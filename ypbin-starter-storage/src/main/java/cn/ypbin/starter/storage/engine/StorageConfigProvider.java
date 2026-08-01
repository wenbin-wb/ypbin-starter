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
package cn.ypbin.starter.storage.engine;

import cn.ypbin.starter.storage.autoconfigure.StorageProperties.LocalConfig;
import cn.ypbin.starter.storage.autoconfigure.StorageProperties.OssConfig;
import java.util.List;

/**
 * 存储源配置来源扩展点。
 *
 * <p>starter 默认提供配置文件版实现（读 {@code ypbin.storage.*}）。业务系统若把存储源配置做成后台可配置
 * （如存 sys_storage 表），实现本接口返回动态配置即可覆盖，改完调用
 * {@link StorageStrategyRebuilder#rebuild()} 即时生效，无需重启。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public interface StorageConfigProvider {

    /**
     * 本地存储源配置列表。
     *
     * @return 本地配置列表
     */
    List<LocalConfig> getLocalConfigs();

    /**
     * 对象存储源配置列表。
     *
     * @return OSS 配置列表
     */
    List<OssConfig> getOssConfigs();

    /**
     * 默认存储平台标识，可空（空则取第一个可用源）。
     *
     * @return 默认平台
     */
    String getDefaultPlatform();
}

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
package cn.ypbin.starter.sign.core;

import java.util.Optional;

/**
 * 开放应用来源扩展点。
 *
 * <p>签名校验时通过本接口按 Access Key 查询应用。starter 默认提供配置文件版实现，
 * 业务系统有应用管理表时实现本接口从数据库加载即可（配合密钥加密存储）。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public interface SignAppProvider {

    /**
     * 按 Access Key 查询应用。
     *
     * @param accessKey 访问密钥
     * @return 应用信息
     */
    Optional<SignApp> findByAccessKey(String accessKey);
}

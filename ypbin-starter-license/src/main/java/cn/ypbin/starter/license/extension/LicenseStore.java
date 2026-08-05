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
package cn.ypbin.starter.license.extension;

/**
 * 授权串存取扩展点。
 *
 * <p>抽象授权串（Base64 授权码/授权文件内容）的持久化来源。starter 内置基于本地文件的默认实现，
 * 业务侧可实现本接口改为从数据库、配置中心或对象存储读写，以支持在线更新授权（上传新授权文件后落库）
 * 与多实例共享。</p>
 *
 * <p>{@link #load()} 读不到授权时返回 {@code null}，由上层按「未授权」处理并暴露状态，而非返回空串
 * 伪装成已授权。{@link #save(String)} 用于在线更新场景写入新授权串。</p>
 *
 * @author wenbin
 * @since 2026-08-05
 */
public interface LicenseStore {

    /**
     * 读取当前授权串。
     *
     * @return Base64 授权串；不存在时返回 {@code null}
     */
    String load();

    /**
     * 保存授权串（在线更新授权时调用）。
     *
     * @param authCode Base64 授权串
     */
    void save(String authCode);
}

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
package cn.ypbin.starter.data.crypto;

/**
 * 字段加密器静态持有器。
 *
 * <p>MyBatis 的 {@code TypeHandler} 由 MyBatis 自行实例化，无法通过 Spring 注入依赖，
 * 因此用静态持有器桥接：自动配置阶段把容器中的 {@link FieldEncryptor} 注入本持有器，
 * TypeHandler 运行时从此处取用。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public final class FieldEncryptorHolder {

    private static volatile FieldEncryptor encryptor;

    private FieldEncryptorHolder() {
    }

    public static void setEncryptor(FieldEncryptor encryptor) {
        FieldEncryptorHolder.encryptor = encryptor;
    }

    /**
     * 获取加密器。
     *
     * @return 加密器
     * @throws IllegalStateException 未配置加密器（未设置 ypbin.data.encrypt.key）时
     */
    public static FieldEncryptor getEncryptor() {
        FieldEncryptor current = encryptor;
        if (current == null) {
            throw new IllegalStateException(
                "字段加密器未初始化，请配置 ypbin.data.encrypt.key 或提供 FieldEncryptor Bean");
        }
        return current;
    }
}

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
package cn.ypbin.starter.data;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.data.core.EntityStatus;
import cn.ypbin.starter.data.crypto.FieldEncryptor;
import cn.ypbin.starter.data.crypto.FieldEncryptorHolder;
import cn.ypbin.starter.data.util.IdGenerator;
import org.junit.jupiter.api.Test;

/**
 * 数据模块核心工具测试：雪花 ID、字段加密持有器、状态枚举。
 *
 * @author wenbin
 * @since 2026-08-31
 */
class DataCoreTest {

    @Test
    void idGeneratorShouldProduceUniqueLongs() {
        long a = IdGenerator.nextId();
        long b = IdGenerator.nextId();
        assertThat(a).isNotEqualTo(b);
        assertThat(a).isPositive();
        assertThat(IdGenerator.nextIdStr()).matches("\\d+");
        assertThat(IdGenerator.simpleUuid()).hasSize(32);
    }

    @Test
    void fieldEncryptorHolderShouldBindAndExpose() {
        FieldEncryptor encryptor = new FieldEncryptor() {
            @Override
            public String encrypt(String plain) {
                return "enc:" + plain;
            }

            @Override
            public String decrypt(String cipher) {
                return cipher.startsWith("enc:") ? cipher.substring(4) : cipher;
            }
        };
        FieldEncryptorHolder.setEncryptor(encryptor);
        assertThat(FieldEncryptorHolder.getEncryptor()).isSameAs(encryptor);
        assertThat(FieldEncryptorHolder.getEncryptor().encrypt("abc")).isEqualTo("enc:abc");
        FieldEncryptorHolder.setEncryptor(null);
    }

    @Test
    void entityStatusShouldExposeCodeAndDesc() {
        assertThat(EntityStatus.ENABLED.getCode()).isEqualTo(1);
        assertThat(EntityStatus.ENABLED.getDesc()).isEqualTo("启用");
        assertThat(EntityStatus.DISABLED.getCode()).isEqualTo(0);
        assertThat(EntityStatus.DISABLED.getDesc()).isEqualTo("禁用");
    }
}

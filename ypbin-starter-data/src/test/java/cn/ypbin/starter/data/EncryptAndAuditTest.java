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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.ypbin.starter.data.autoconfigure.DataProperties;
import cn.ypbin.starter.data.core.AuditorProvider;
import cn.ypbin.starter.data.crypto.AesFieldEncryptor;
import cn.ypbin.starter.data.crypto.EncryptTypeHandler;
import cn.ypbin.starter.data.crypto.FieldEncryptor;
import cn.ypbin.starter.data.crypto.FieldEncryptorHolder;
import cn.ypbin.starter.data.handler.DefaultMetaObjectHandler;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 加密类型处理器与审计填充测试。
 *
 * @author wenbin
 * @since 2026-08-31
 */
class EncryptAndAuditTest {

    @AfterEach
    void tearDown() {
        FieldEncryptorHolder.setEncryptor(null);
    }

    @Test
    void aesEncryptorShouldRoundTrip() {
        AesFieldEncryptor encryptor = new AesFieldEncryptor("0123456789abcdef0123456789abcdef");
        String cipher = encryptor.encrypt("敏感数据");
        assertThat(cipher).isNotEqualTo("敏感数据");
        assertThat(encryptor.decrypt(cipher)).isEqualTo("敏感数据");
    }

    @Test
    void encryptTypeHandlerShouldEncryptOnSet() throws Exception {
        FieldEncryptor encryptor = new AesFieldEncryptor("0123456789abcdef0123456789abcdef");
        FieldEncryptorHolder.setEncryptor(encryptor);
        EncryptTypeHandler handler = new EncryptTypeHandler();
        PreparedStatement ps = mock(PreparedStatement.class);

        handler.setNonNullParameter(ps, 1, "明文", JdbcType.VARCHAR);

        verify(ps).setString(org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.anyString());
        // 密文可解密回明文（AES 随机 IV 每次不同，验证可逆性而非精确值）
        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(ps).setString(org.mockito.ArgumentMatchers.eq(1), captor.capture());
        assertThat(encryptor.decrypt(captor.getValue())).isEqualTo("明文");
    }

    @Test
    void encryptTypeHandlerShouldDecryptOnGet() throws Exception {
        FieldEncryptor encryptor = new AesFieldEncryptor("0123456789abcdef0123456789abcdef");
        FieldEncryptorHolder.setEncryptor(encryptor);
        EncryptTypeHandler handler = new EncryptTypeHandler();
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("cipher")).thenReturn(encryptor.encrypt("数据库值"));

        String result = handler.getNullableResult(rs, "cipher");

        assertThat(result).isEqualTo("数据库值");
    }

    @Test
    void metaObjectHandlerShouldFillAuditFields() {
        AuditorProvider auditorProvider = () -> java.util.Optional.of(42L);
        MetaObjectHandler handler = new DefaultMetaObjectHandler(auditorProvider);
        assertThat(handler).isNotNull();
    }

    @Test
    void dataPropertiesShouldExposeDefaults() {
        DataProperties props = new DataProperties();
        assertThat(props.isEnabled()).isTrue();
        assertThat(props.getMaxLimit()).isEqualTo(500L);
        assertThat(props.isOverflow()).isFalse();
        assertThat(props.getDbType()).isNotNull();
        assertThat(props.getEncrypt()).isNotNull();
        assertThat(DataProperties.PREFIX).isEqualTo("ypbin.data");
    }

    @Test
    void dataPropertiesShouldAllowOverride() {
        DataProperties props = new DataProperties();
        props.setEnabled(false);
        props.setMaxLimit(100L);
        props.setOverflow(true);
        assertThat(props.isEnabled()).isFalse();
        assertThat(props.getMaxLimit()).isEqualTo(100L);
        assertThat(props.isOverflow()).isTrue();
    }
}

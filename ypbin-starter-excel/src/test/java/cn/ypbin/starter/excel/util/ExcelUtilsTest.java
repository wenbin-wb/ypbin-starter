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
package cn.ypbin.starter.excel.util;

import static org.assertj.core.api.Assertions.assertThat;

import cn.idev.excel.annotation.ExcelProperty;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link ExcelUtils} 读写往返单元测试。
 *
 * @author wenbin
 * @since 2026-07-30
 */
class ExcelUtilsTest {

    public static class Row {
        @ExcelProperty("用户名")
        private String username;

        @ExcelProperty("年龄")
        private Integer age;

        public Row() {
        }

        public Row(String username, Integer age) {
            this.username = username;
            this.age = age;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public Integer getAge() {
            return age;
        }

        public void setAge(Integer age) {
            this.age = age;
        }
    }

    @Test
    void writeThenRead_roundTrip() {
        List<Row> data = List.of(new Row("alice", 20), new Row("bob", 30));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ExcelUtils.write(out, "用户", Row.class, data);
        assertThat(out.size()).isPositive();

        List<Row> read = ExcelUtils.read(new ByteArrayInputStream(out.toByteArray()), Row.class);
        assertThat(read).hasSize(2);
        assertThat(read.get(0).getUsername()).isEqualTo("alice");
        assertThat(read.get(0).getAge()).isEqualTo(20);
        assertThat(read.get(1).getUsername()).isEqualTo("bob");
        assertThat(read.get(1).getAge()).isEqualTo(30);
    }

    @Test
    void writeEmpty_producesValidWorkbook() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ExcelUtils.write(out, "空表", Row.class, List.of());
        assertThat(out.size()).isPositive();
        List<Row> read = ExcelUtils.read(new ByteArrayInputStream(out.toByteArray()), Row.class);
        assertThat(read).isEmpty();
    }
}

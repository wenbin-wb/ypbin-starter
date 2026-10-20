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
package cn.ypbin.starter.data.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link BaseEnumScanner#scanAll(long)} 的<b>成本上界</b>测试。
 *
 * <p><b>要防的是真实事故</b>（2026-10-20）：某宿主零个 {@code BaseEnum} 实现、堆只有
 * {@code -Xmx384m}，零命中诊断以空前缀扫全 classpath 时读到 groovy 巨类，直接
 * {@code OutOfMemoryError} 把服务打挂——「诊断信息」不该有这个代价。</p>
 *
 * <p>本类钉住三件事：① 上界会中止扫描且<b>不抛异常</b>（诊断失败不得影响启动）；
 * ② 上界被触发后仍返回列表（部分结果），调用方可安全使用；③ 非法上界快速失败。</p>
 *
 * @author wenbin
 * @since 2026-10-20
 */
class BaseEnumScannerBoundTest {

    @Test
    @DisplayName("极小上界：扫描中止但**不抛异常**，返回部分结果（诊断不影响启动）")
    void tinyBoundShouldAbortWithoutThrowing() {
        // 1 个资源的上界必然立刻触发中止；关键是「不抛出去」
        List<Class<?>> found = BaseEnumScanner.scanAll(1L);

        assertThat(found).as("上界触发后返回部分结果（可能为空），不得抛异常打断启动").isNotNull();
    }

    @Test
    @DisplayName("上界触发路径可重复调用（不残留状态）")
    void abortShouldNotPoisonSubsequentCalls() {
        BaseEnumScanner.scanAll(1L);
        BaseEnumScanner.scanAll(1L);
        // 默认上界足够大，本测试 classpath 上应能扫到本包内的探针枚举
        List<Class<?>> all = BaseEnumScanner.scanAll(BaseEnumScanner.DEFAULT_MAX_RESOURCES);

        assertThat(all).as("默认上界下应能扫到 classpath 上的 BaseEnum 实现").isNotEmpty();
    }

    @Test
    @DisplayName("非法上界快速失败（不静默按 0 处理）")
    void invalidBoundShouldFailFast() {
        assertThatThrownBy(() -> BaseEnumScanner.scanAll(0L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxResources");
        assertThatThrownBy(() -> BaseEnumScanner.scanAll(-5L))
            .isInstanceOf(IllegalArgumentException.class);
    }
}

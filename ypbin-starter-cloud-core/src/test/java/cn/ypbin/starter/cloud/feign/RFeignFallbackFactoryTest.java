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
package cn.ypbin.starter.cloud.feign;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.cloud.feign.fallback.FeignFallbackException;
import cn.ypbin.starter.cloud.feign.fallback.RFeignFallbackFactory;
import cn.ypbin.starter.core.exception.GlobalErrorCode;
import cn.ypbin.starter.core.model.R;
import org.junit.jupiter.api.Test;

/**
 * {@link RFeignFallbackFactory} 单元测试。
 *
 * @author wenbin
 * @since 2026-07-31
 */
class RFeignFallbackFactoryTest {

    @Test
    void shouldCreateInternalErrorFallbackResponse() {
        DemoClient client = new DemoFallbackFactory().create(new IllegalStateException("远程超时"));

        R<String> result = client.get();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(GlobalErrorCode.INTERNAL_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("远程超时");
    }

    @Test
    void shouldKeepCustomFallbackCode() {
        DemoClient client = new DemoFallbackFactory().create(new FeignFallbackException(409, "库存不足"));

        R<String> result = client.get();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(409);
        assertThat(result.getMessage()).isEqualTo("库存不足");
    }

    private interface DemoClient {
        R<String> get();
    }

    private static class DemoFallbackFactory extends RFeignFallbackFactory<DemoClient> {

        @Override
        public DemoClient create(Throwable cause) {
            return () -> fail(cause);
        }
    }
}

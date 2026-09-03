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
package cn.ypbin.starter.cloud.feign.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.model.R;
import org.junit.jupiter.api.Test;

class FeignResponsesTest {

    @Test
    void dataOrThrowShouldReturnDataWhenSuccess() {
        R<String> ok = R.ok("value");
        assertThat(FeignResponses.dataOrThrow(ok, "error")).isEqualTo("value");
    }

    @Test
    void dataOrThrowShouldThrowWhenNullResponse() {
        assertThatThrownBy(() -> FeignResponses.dataOrThrow(null, "error"))
            .isInstanceOf(BusinessException.class)
            .hasMessage("error");
    }

    @Test
    void dataOrThrowShouldThrowWhenFailResponse() {
        R<String> fail = R.fail("fail");
        assertThatThrownBy(() -> FeignResponses.dataOrThrow(fail, "error"))
            .isInstanceOf(BusinessException.class)
            .hasMessage("error");
    }

    @Test
    void dataOrElseShouldReturnFallbackWhenFail() {
        assertThat(FeignResponses.dataOrElse(R.fail("fail"), "fb")).isEqualTo("fb");
        assertThat(FeignResponses.dataOrElse(null, "fb")).isEqualTo("fb");
    }

    @Test
    void dataOrElseShouldReturnNullWhenDataNullAndFallbackNull() {
        String value = FeignResponses.dataOrElse(R.ok(null), null);
        assertThat(value).isNull();
    }

    @Test
    void optionalDataShouldReturnEmptyWhenFail() {
        assertThat(FeignResponses.optionalData(R.fail("fail"))).isEmpty();
        assertThat(FeignResponses.optionalData(null)).isEmpty();
    }

    @Test
    void isSuccessWithDataShouldWork() {
        assertThat(FeignResponses.isSuccessWithData(R.ok("x"))).isTrue();
        assertThat(FeignResponses.isSuccessWithData(R.ok(null))).isFalse();
        assertThat(FeignResponses.isSuccessWithData(R.fail("fail"))).isFalse();
        assertThat(FeignResponses.isSuccessWithData(null)).isFalse();
    }

    @Test
    void supplyOrThrowShouldWork() {
        assertThat(FeignResponses.supplyOrThrow(() -> R.ok("x"), "error")).isEqualTo("x");
        assertThatThrownBy(() -> FeignResponses.supplyOrThrow(() -> R.fail("fail"), "error"))
            .isInstanceOf(BusinessException.class)
            .hasMessage("error");
    }
}

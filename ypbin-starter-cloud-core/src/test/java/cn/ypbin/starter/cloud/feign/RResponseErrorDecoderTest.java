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

import cn.ypbin.starter.cloud.exception.FeignRemoteException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import feign.Request;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link RResponseErrorDecoder} 单元测试。
 *
 * @author wenbin
 * @since 2026-07-31
 */
class RResponseErrorDecoderTest {

    private final RResponseErrorDecoder decoder = new RResponseErrorDecoder(new ObjectMapper());

    @Test
    void shouldDecodeRResponseToFeignRemoteException() {
        Response response = response(503, "{\"code\":503,\"message\":\"服务不可用\",\"success\":false}");

        Exception exception = decoder.decode("DemoClient#get", response);

        assertThat(exception).isInstanceOf(FeignRemoteException.class);
        FeignRemoteException remoteException = (FeignRemoteException) exception;
        assertThat(remoteException.getCode()).isEqualTo(503);
        assertThat(remoteException.getMessage()).isEqualTo("服务不可用");
        assertThat(remoteException.getStatus()).isEqualTo(503);
        assertThat(remoteException.getMethodKey()).isEqualTo("DemoClient#get");
    }

    @Test
    void shouldFallbackToFeignDefaultWhenBodyIsNotR() {
        Response response = response(500, "plain error");

        Exception exception = decoder.decode("DemoClient#get", response);

        assertThat(exception).isInstanceOf(FeignException.class);
    }

    private static Response response(int status, String body) {
        Request request = Request.create(
            Request.HttpMethod.GET,
            "http://demo/test",
            Map.of(),
            null,
            StandardCharsets.UTF_8,
            null);
        return Response.builder()
            .request(request)
            .status(status)
            .reason("error")
            .body(body, StandardCharsets.UTF_8)
            .build();
    }
}

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
package __PACKAGE__;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * __APP_NAME__ 服务启动类（微服务形态）。
 *
 * <p>身份由网关校验后以 {@code X-User-Id} 等身份头下发，本服务通过
 * {@code ypbin.security.identity.enabled=true} 信任身份头，不再本地校验 token。</p>
 *
 * @author wenbin
 * @since 2026-09-13
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "__PACKAGE__.api")
public class __APP_CLASS__ {

    /**
     * 应用入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(__APP_CLASS__.class, args);
    }
}

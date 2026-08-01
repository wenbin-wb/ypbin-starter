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
package cn.ypbin.starter.sign.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口签名注解。
 *
 * <p>标注在方法或类上，要求该接口的请求必须携带并通过签名校验（accessKey + timestamp + nonce + sign）。
 * 用于对外提供给第三方对接的接口。与全局拦截模式（{@code ypbin.sign.mode=global}）二选一：
 * 注解模式下仅被标注的接口校验，更精准。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiSign {

    /**
     * 是否忽略签名（用于在已启用的类中排除个别方法）。
     *
     * @return 是否忽略
     */
    boolean ignore() default false;
}

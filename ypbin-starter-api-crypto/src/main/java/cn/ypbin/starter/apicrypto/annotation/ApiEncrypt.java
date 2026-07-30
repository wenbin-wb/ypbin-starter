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
package cn.ypbin.starter.apicrypto.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口加解密注解。
 *
 * <p>标注在接口方法上：请求进入前对请求体解密、响应返回前对响应体加密，对 Controller 透明。
 * 前端与后端约定同一密钥与算法。{@link #requestDecrypt()} / {@link #responseEncrypt()}
 * 可分别控制方向（如仅加密响应）。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiEncrypt {

    /**
     * 是否解密请求体。
     *
     * @return 是否解密请求
     */
    boolean requestDecrypt() default true;

    /**
     * 是否加密响应体。
     *
     * @return 是否加密响应
     */
    boolean responseEncrypt() default true;
}

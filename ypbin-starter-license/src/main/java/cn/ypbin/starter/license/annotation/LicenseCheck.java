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
package cn.ypbin.starter.license.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * License 授权校验注解。
 *
 * <p>一行集成：在需要保护的接口方法或整个 Service/Controller 类上标注即可，进入方法前自动校验当前授权。
 * 类上标注对其下所有方法生效，方法上可覆盖。校验不通过将抛出授权异常，由全局异常处理器转为统一响应体。</p>
 *
 * <p>校验维度按需组合：</p>
 * <ul>
 *   <li>基础可用性（默认）：授权须为合法或宽限期内，过期即拦截；</li>
 *   <li>{@link #module()}：要求授权范围包含该功能模块，实现模块级细粒度授权；</li>
 *   <li>{@link #online()}：要求本次调用触发一次联机回验（需接入联机校验扩展点）。</li>
 * </ul>
 *
 * <p>需引入 {@code ypbin-starter-license} 并开启 {@code ypbin.license.enabled=true}。</p>
 *
 * @author wenbin
 * @since 2026-08-05
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface LicenseCheck {

    /**
     * 要求已授权的功能模块标识。
     *
     * <p>非空时在基础可用性校验之外，额外要求授权范围包含该模块；为空则仅做基础可用性校验。</p>
     *
     * @return 模块标识
     */
    String module() default "";

    /**
     * 是否要求本次调用触发联机回验。
     *
     * <p>为 {@code true} 时结合联机校验扩展点做实时回验，可感知远程吊销；未接入扩展点时按离线状态校验。</p>
     *
     * @return 是否联机回验
     */
    boolean online() default false;
}

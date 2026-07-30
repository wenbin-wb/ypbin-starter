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
package cn.ypbin.starter.tools.idempotent;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 幂等注解。
 *
 * <p>标注在方法上，在 {@link #interval()} 秒内，相同幂等键的重复调用将被拒绝，
 * 用于防止表单重复提交、消息重复消费等。幂等键默认由「方法 + 参数指纹」生成，
 * 也可用 {@link #key()} 指定 SpEL 表达式（如取业务单号）。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /**
     * 幂等键（支持 SpEL，可引用方法入参）。留空则用「方法全限定名 + 参数指纹」。
     *
     * @return 幂等键
     */
    String key() default "";

    /**
     * 幂等有效期（秒），此窗口内的重复请求被拒绝。
     *
     * @return 有效期秒数
     */
    int interval() default 5;

    /**
     * 命中重复时的提示信息。
     *
     * @return 提示信息
     */
    String message() default "请勿重复提交";
}

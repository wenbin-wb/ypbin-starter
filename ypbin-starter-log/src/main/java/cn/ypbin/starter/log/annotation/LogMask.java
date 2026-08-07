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
package cn.ypbin.starter.log.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注请求/响应 DTO 字段，声明该字段在访问日志中不明文打印。
 *
 * <p>仅影响 {@code AccessLogAspect} 使用的日志专用 {@code ObjectMapper}（见
 * {@code LogMaskModule}），不影响该 DTO 正常序列化为接口响应的行为。密码、密钥等
 * 敏感字段应标注此注解，序列化到日志时固定替换为 {@code ******}。</p>
 *
 * @author wenbin
 * @since 2026-08-07
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogMask {
}

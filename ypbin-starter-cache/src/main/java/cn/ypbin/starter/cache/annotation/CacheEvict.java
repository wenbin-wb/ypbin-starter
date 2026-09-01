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
package cn.ypbin.starter.cache.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明式缓存失效。
 *
 * <p>标注在写操作方法上（新增/修改/删除/状态变更等），方法**执行成功后**按 {@link #keys()}
 * 解析出缓存键并删除，配合 {@code CacheUtils.getOrLoad} 的永久缓存实现「主动失效」：
 * 数据变更即清缓存，读侧下次访问自然回源。</p>
 *
 * <p>键为 SpEL 表达式：静态前缀用单引号字符串字面量（冒号等缓存键字符在字符串内合法），
 * {@code #参数名} 引用方法参数、{@code #参数名.字段} 取字段，用 {@code +} 拼接。
 * 例：{@code @CacheEvict(keys = {"'sys:user:id:' + #id", "'sys:user:username:' + #req.username"})}。</p>
 *
 * <p>方法抛异常（含事务回滚）时**不删缓存**——数据未真正变更，保留旧缓存是正确语义。</p>
 *
 * @author wenbin
 * @since 2026-09-01
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CacheEvict {

    /**
     * 要删除的缓存键（「静态前缀 + #{SpEL}」模板数组，至少一个）。
     */
    String[] keys();
}

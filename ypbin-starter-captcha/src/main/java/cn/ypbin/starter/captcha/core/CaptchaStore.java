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
package cn.ypbin.starter.captcha.core;

import java.time.Duration;

/**
 * 验证码存储扩展点。
 *
 * <p>存放"验证码 id -> 答案"，带过期时间。默认内存实现，分布式场景由 Redis 实现覆盖。
 * 校验一次即失效（取出后删除），防止重放。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public interface CaptchaStore {

    /**
     * 保存验证码答案。
     *
     * @param id      验证码标识
     * @param code    答案
     * @param timeout 有效期
     */
    void save(String id, String code, Duration timeout);

    /**
     * 取出并删除验证码答案（一次性）。
     *
     * @param id 验证码标识
     * @return 答案，不存在或已过期返回 {@code null}
     */
    String takeAndRemove(String id);
}

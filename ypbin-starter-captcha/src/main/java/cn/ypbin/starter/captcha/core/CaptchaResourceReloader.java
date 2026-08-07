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

/**
 * 验证码默认资源重新加载入口。
 *
 * <p>验证码 store 可能是 Redis 等外部存储，宿主服务不重启也可能出现 store 中数据被清空的情况
 * （如 Redis 重启未持久化）。{@link CaptchaService} 捕获到 store 为空的异常后通过该接口触发一次
 * 补载，而不依赖应用重启。</p>
 *
 * @author wenbin
 * @since 2026-08-07
 */
public interface CaptchaResourceReloader {

    /**
     * 幂等补载默认模板与背景图：store 中已有数据则不重复写入。
     */
    void reload();
}

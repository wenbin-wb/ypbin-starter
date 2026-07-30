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

import cn.ypbin.starter.captcha.autoconfigure.CaptchaProperties;
import com.wf.captcha.SpecCaptcha;
import com.wf.captcha.base.Captcha;
import java.time.Duration;
import java.util.UUID;

/**
 * 验证码服务。
 *
 * <p>生成图形验证码（返回 Base64 图片 + 标识），并将答案存入 {@link CaptchaStore}；
 * 校验时按标识取出答案比对，一次性失效。基于 easy-captcha 的算术/字符类型可配。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class CaptchaService {

    private final CaptchaStore store;
    private final CaptchaProperties properties;

    public CaptchaService(CaptchaStore store, CaptchaProperties properties) {
        this.store = store;
        this.properties = properties;
    }

    /**
     * 生成验证码。
     *
     * @return 验证码结果（含标识与 Base64 图片）
     */
    public CaptchaResult generate() {
        SpecCaptcha captcha = new SpecCaptcha(properties.getWidth(), properties.getHeight(),
            properties.getLength());
        captcha.setCharType(Captcha.TYPE_DEFAULT);
        String code = captcha.text().toLowerCase();
        String id = UUID.randomUUID().toString().replace("-", "");
        store.save(id, code, Duration.ofSeconds(properties.getExpireSeconds()));
        return new CaptchaResult(id, captcha.toBase64());
    }

    /**
     * 校验验证码（不区分大小写，一次性）。
     *
     * @param id   验证码标识
     * @param code 用户输入
     * @return 是否校验通过
     */
    public boolean verify(String id, String code) {
        if (id == null || code == null || code.isBlank()) {
            return false;
        }
        String expected = store.takeAndRemove(id);
        return expected != null && expected.equalsIgnoreCase(code.trim());
    }
}

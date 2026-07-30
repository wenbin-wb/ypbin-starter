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

import java.io.Serial;
import java.io.Serializable;

/**
 * 验证码生成结果。
 *
 * <p>返回给前端：{@code id} 用于后续校验时定位、{@code base64Image} 是可直接嵌入
 * {@code <img src>} 的图片数据。验证码答案不返回前端，仅存于服务端。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class CaptchaResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 验证码唯一标识（校验时回传） */
    private final String id;

    /** Base64 图片（形如 data:image/png;base64,xxx） */
    private final String base64Image;

    public CaptchaResult(String id, String base64Image) {
        this.id = id;
        this.base64Image = base64Image;
    }

    public String getId() {
        return id;
    }

    public String getBase64Image() {
        return base64Image;
    }
}

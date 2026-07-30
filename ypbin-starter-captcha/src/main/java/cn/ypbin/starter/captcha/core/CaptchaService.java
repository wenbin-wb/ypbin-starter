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

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.common.constant.CaptchaTypeConstant;
import cloud.tianai.captcha.common.response.ApiResponse;
import cloud.tianai.captcha.validator.common.model.dto.ImageCaptchaTrack;

/**
 * 验证码服务（薄封装）。
 *
 * <p>委托给 tianai-captcha 的 {@link ImageCaptchaApplication}，支持滑块、旋转、点选、拼接等
 * 行为验证码。生成的验证码数据（含图片、id）由前端渲染，用户完成行为后回传轨迹 {@link ImageCaptchaTrack}
 * 校验。验证码状态由 tianai starter 自带的缓存（本地/Redis 自动切换）管理，一次性有效。</p>
 *
 * <p>验证码类型见 {@link CaptchaTypeConstant}：SLIDER（滑块）/ ROTATE（旋转）/
 * CONCAT（拼接）/ WORD_IMAGE_CLICK（文字点选）。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class CaptchaService {

    private final ImageCaptchaApplication application;

    public CaptchaService(ImageCaptchaApplication application) {
        this.application = application;
    }

    /**
     * 生成默认（滑块）验证码。
     *
     * @return 验证码数据（含 id 与图片，供前端渲染）
     */
    public ApiResponse<?> generate() {
        return application.generateCaptcha(CaptchaTypeConstant.SLIDER);
    }

    /**
     * 生成指定类型的验证码。
     *
     * @param type 验证码类型（见 {@link CaptchaTypeConstant}）
     * @return 验证码数据
     */
    public ApiResponse<?> generate(String type) {
        return application.generateCaptcha(type);
    }

    /**
     * 校验用户行为轨迹（一次性）。
     *
     * @param id    生成时返回的验证码 id
     * @param track 前端采集的行为轨迹
     * @return 校验结果
     */
    public boolean verify(String id, ImageCaptchaTrack track) {
        ApiResponse<?> response = application.matching(id, track);
        return response != null && response.isSuccess();
    }

    /**
     * 获取底层 tianai 应用，用于高级定制场景。
     *
     * @return {@link ImageCaptchaApplication}
     */
    public ImageCaptchaApplication getApplication() {
        return application;
    }
}

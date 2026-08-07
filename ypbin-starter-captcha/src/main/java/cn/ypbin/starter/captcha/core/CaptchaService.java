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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

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
 * <p>资源 store 为 Redis 等外部存储时，可能在应用不重启的情况下被清空（如 Redis 重启未持久化），
 * 表现为 tianai 抛出 {@link IllegalStateException}（"随机获取模板/资源错误...为空"）。此时通过
 * {@link CaptchaResourceReloader} 补载一次默认资源后重试一次生成，避免必须重启应用才能恢复。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class CaptchaService {

    private static final Logger log = LoggerFactory.getLogger(CaptchaService.class);

    private final ImageCaptchaApplication application;

    @Nullable
    private final CaptchaResourceReloader resourceReloader;

    public CaptchaService(ImageCaptchaApplication application) {
        this(application, null);
    }

    public CaptchaService(ImageCaptchaApplication application, @Nullable CaptchaResourceReloader resourceReloader) {
        this.application = application;
        this.resourceReloader = resourceReloader;
    }

    /**
     * 生成默认（滑块）验证码。
     *
     * @return 验证码数据（含 id 与图片，供前端渲染）
     */
    public ApiResponse<?> generate() {
        return generate(CaptchaTypeConstant.SLIDER);
    }

    /**
     * 生成指定类型的验证码。
     *
     * @param type 验证码类型（见 {@link CaptchaTypeConstant}）
     * @return 验证码数据
     */
    public ApiResponse<?> generate(String type) {
        try {
            return application.generateCaptcha(type);
        } catch (IllegalStateException e) {
            if (resourceReloader == null) {
                throw e;
            }
            log.warn("[ypbin-starter] captcha 生成失败（{}），尝试补载默认资源后重试一次。", e.getMessage());
            resourceReloader.reload();
            return application.generateCaptcha(type);
        }
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

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

import static cloud.tianai.captcha.common.constant.CaptchaTypeConstant.SLIDER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.application.vo.ImageCaptchaVO;
import cloud.tianai.captcha.common.response.ApiResponse;
import org.junit.jupiter.api.Test;

/**
 * 验证码服务测试，重点覆盖 store 数据被清空（如 Redis 重启丢数据）后的自愈重试逻辑。
 *
 * @author wenbin
 * @since 2026-08-07
 */
class CaptchaServiceTest {

    @Test
    void shouldReturnDirectlyWhenGenerateSucceeds() {
        ImageCaptchaApplication application = mock(ImageCaptchaApplication.class);
        CaptchaResourceReloader reloader = mock(CaptchaResourceReloader.class);
        ApiResponse<ImageCaptchaVO> response = ApiResponse.ofSuccess(null);
        when(application.generateCaptcha(SLIDER)).thenReturn(response);
        CaptchaService service = new CaptchaService(application, reloader);

        ApiResponse<?> result = service.generate();

        assertThat(result).isSameAs(response);
        verify(reloader, never()).reload();
    }

    @Test
    void shouldReloadAndRetryOnceWhenStoreEmpty() {
        ImageCaptchaApplication application = mock(ImageCaptchaApplication.class);
        CaptchaResourceReloader reloader = mock(CaptchaResourceReloader.class);
        ApiResponse<ImageCaptchaVO> response = ApiResponse.ofSuccess(null);
        when(application.generateCaptcha(SLIDER))
                .thenThrow(new IllegalStateException("随机获取模板错误，store中模板为空, type:" + SLIDER))
                .thenReturn(response);
        CaptchaService service = new CaptchaService(application, reloader);

        ApiResponse<?> result = service.generate();

        assertThat(result).isSameAs(response);
        verify(reloader, times(1)).reload();
        verify(application, times(2)).generateCaptcha(SLIDER);
    }

    @Test
    void shouldPropagateExceptionWhenRetryStillFails() {
        ImageCaptchaApplication application = mock(ImageCaptchaApplication.class);
        CaptchaResourceReloader reloader = mock(CaptchaResourceReloader.class);
        when(application.generateCaptcha(SLIDER)).thenThrow(new IllegalStateException("随机获取模板错误，store中模板为空, type:" + SLIDER));
        CaptchaService service = new CaptchaService(application, reloader);

        assertThatThrownBy(service::generate).isInstanceOf(IllegalStateException.class);

        verify(reloader, times(1)).reload();
        verify(application, times(2)).generateCaptcha(SLIDER);
    }

    @Test
    void shouldPropagateExceptionDirectlyWhenNoReloaderConfigured() {
        ImageCaptchaApplication application = mock(ImageCaptchaApplication.class);
        when(application.generateCaptcha(SLIDER)).thenThrow(new IllegalStateException("随机获取模板错误，store中模板为空, type:" + SLIDER));
        CaptchaService service = new CaptchaService(application);

        assertThatThrownBy(service::generate).isInstanceOf(IllegalStateException.class);

        verify(application, times(1)).generateCaptcha(SLIDER);
    }
}

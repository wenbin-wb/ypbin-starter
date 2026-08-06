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
package cn.ypbin.starter.captcha.autoconfigure;

import static cloud.tianai.captcha.common.constant.CommonConstant.DEFAULT_TAG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.common.constant.CaptchaTypeConstant;
import cloud.tianai.captcha.resource.DefaultBuiltInResources;
import cloud.tianai.captcha.resource.ResourceProviders;
import cloud.tianai.captcha.resource.impl.DefaultImageCaptchaResourceManager;
import cloud.tianai.captcha.resource.impl.LocalMemoryResourceStore;
import org.junit.jupiter.api.Test;

/**
 * 验证码默认资源初始化测试。
 *
 * <p>通过真实的 {@link DefaultImageCaptchaResourceManager}（内部用 {@code FontCache} 包装 store）验证
 * {@link CaptchaResourceInitializer} 能穿透字体缓存加载默认模板与背景图，且幂等、不重复加载。</p>
 *
 * @author wenbin
 * @since 2026-08-06
 */
class CaptchaResourceInitializerTest {

    private static final String TEMPLATE_PREFIX = "classpath:META-INF/cut-image/template";

    @Test
    void shouldLoadDefaultTemplatesAndBackgrounds() {
        LocalMemoryResourceStore store = newStore();
        CaptchaResourceInitializer initializer = newInitializer(store);

        initializer.init();

        assertThat(store.listTemplatesByTypeAndTag(CaptchaTypeConstant.SLIDER, DEFAULT_TAG)).hasSize(2);
        assertThat(store.listTemplatesByTypeAndTag(CaptchaTypeConstant.ROTATE, DEFAULT_TAG)).hasSize(1);
        assertThat(store.listResourcesByTypeAndTag(CaptchaTypeConstant.SLIDER, DEFAULT_TAG)).hasSize(1);
        assertThat(store.listResourcesByTypeAndTag(CaptchaTypeConstant.ROTATE, DEFAULT_TAG)).hasSize(1);
    }

    @Test
    void shouldBeIdempotent() {
        LocalMemoryResourceStore store = newStore();
        CaptchaResourceInitializer initializer = newInitializer(store);

        initializer.init();
        initializer.init();

        assertThat(store.listTemplatesByTypeAndTag(CaptchaTypeConstant.SLIDER, DEFAULT_TAG)).hasSize(2);
        assertThat(store.listResourcesByTypeAndTag(CaptchaTypeConstant.SLIDER, DEFAULT_TAG)).hasSize(1);
    }

    @Test
    void shouldNotDuplicateWhenTianaiPreloadedTemplates() {
        LocalMemoryResourceStore store = newStore();
        CaptchaResourceInitializer initializer = newInitializer(store);
        // 模拟 tianai captcha.init-default-resource=true：默认模板已加载，但背景图仍缺失
        new DefaultBuiltInResources(TEMPLATE_PREFIX).addDefaultTemplate(store);

        initializer.init();

        assertThat(store.listTemplatesByTypeAndTag(CaptchaTypeConstant.SLIDER, DEFAULT_TAG)).hasSize(2);
        assertThat(store.listResourcesByTypeAndTag(CaptchaTypeConstant.SLIDER, DEFAULT_TAG)).hasSize(1);
    }

    private LocalMemoryResourceStore newStore() {
        return new LocalMemoryResourceStore();
    }

    private CaptchaResourceInitializer newInitializer(LocalMemoryResourceStore store) {
        DefaultImageCaptchaResourceManager manager =
                new DefaultImageCaptchaResourceManager(store, new ResourceProviders());
        ImageCaptchaApplication application = mock(ImageCaptchaApplication.class);
        when(application.getImageCaptchaResourceManager()).thenReturn(manager);
        return new CaptchaResourceInitializer(application);
    }
}

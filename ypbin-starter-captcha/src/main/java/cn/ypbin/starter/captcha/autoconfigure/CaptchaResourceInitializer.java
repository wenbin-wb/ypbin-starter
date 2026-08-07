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

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.common.constant.CaptchaTypeConstant;
import cloud.tianai.captcha.resource.CrudResourceStore;
import cloud.tianai.captcha.resource.DefaultBuiltInResources;
import cloud.tianai.captcha.resource.ImageCaptchaResourceManager;
import cloud.tianai.captcha.resource.ResourceStore;
import cloud.tianai.captcha.resource.common.model.dto.Resource;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 验证码默认资源初始化。
 *
 * <p>tianai-captcha 默认不加载任何内置资源：{@code captcha.init-default-resource} 关闭（默认）时模板与背景全空，
 * 生成验证码即抛异常；即便打开该开关也只加载模板与字体、不加载背景图。本类在 {@link ImageCaptchaApplication}
 * 创建后幂等补齐内置资源（SLIDER/ROTATE 模板 + 字体、背景图），宿主零配置即可开箱使用。背景图默认加载
 * tianai 内置的单张图；宿主通过 {@link CaptchaProperties#getBackgroundResources()} 配置自定义背景时改为
 * 全部加载，验证码随机取用，避免背景单一。</p>
 *
 * <p>资源通过 {@link ImageCaptchaResourceManager#getResourceStore()} 解析，用 {@link ResourceStore#getTarget()}
 * 穿透字体缓存拿到真正可写的 {@link CrudResourceStore}，判空后再加载，避免与 tianai 自带的默认加载重复。</p>
 *
 * @author wenbin
 * @since 2026-08-06
 */
public class CaptchaResourceInitializer {

    private static final Logger log = LoggerFactory.getLogger(CaptchaResourceInitializer.class);

    /** tianai 内置默认模板根路径（核心 jar 内 META-INF/cut-image/template）。 */
    private static final String DEFAULT_TEMPLATE_PREFIX = "classpath:META-INF/cut-image/template";

    /** tianai 内置默认背景图（核心 jar 内 META-INF/cut-image/resource）。 */
    private static final String DEFAULT_BACKGROUND_IMAGE = "META-INF/cut-image/resource/1.jpg";

    /** 资源类型 classpath，与核心 jar 默认资源提供者一致。 */
    private static final String CLASS_PATH = "classpath";

    private final ImageCaptchaApplication application;

    private final List<String> backgroundResources;

    public CaptchaResourceInitializer(ImageCaptchaApplication application, CaptchaProperties properties) {
        this.application = application;
        this.backgroundResources = properties.getBackgroundResources();
    }

    /**
     * 幂等加载默认资源。模板判空后加载，背景图按验证码类型判空后注册。
     *
     * <p>{@code synchronized}：{@code initMethod} 由容器单次调用，加锁防止业务方并发重复触发初始化时
     * 出现判空到写入之间的竞态（同一背景被注册两次）。</p>
     */
    public synchronized void init() {
        ResourceStore store = application.getImageCaptchaResourceManager().getResourceStore().getTarget();
        if (!(store instanceof CrudResourceStore crudStore)) {
            log.error(
                    "[ypbin-starter] captcha 资源 store 不可写（{}），默认模板/背景加载失败，请宿主自行注册验证码资源。",
                    store == null ? "null" : store.getClass().getName());
            return;
        }
        loadDefaultTemplates(crudStore);
        loadBackgrounds(crudStore);
    }

    private void loadDefaultTemplates(CrudResourceStore store) {
        if (!store.listTemplatesByTypeAndTag(CaptchaTypeConstant.SLIDER, DEFAULT_TAG).isEmpty()) {
            return;
        }
        new DefaultBuiltInResources(DEFAULT_TEMPLATE_PREFIX).addDefaultTemplate(store);
        log.debug("[ypbin-starter] captcha 默认模板已加载（SLIDER/ROTATE/字体）。");
    }

    private void loadBackgrounds(CrudResourceStore store) {
        List<String> resources = backgroundResources.isEmpty() ? List.of(DEFAULT_BACKGROUND_IMAGE) : backgroundResources;
        for (String type : new String[] {CaptchaTypeConstant.SLIDER, CaptchaTypeConstant.ROTATE}) {
            if (!store.listResourcesByTypeAndTag(type, DEFAULT_TAG).isEmpty()) {
                continue;
            }
            for (String resource : resources) {
                store.addResource(type, new Resource(CLASS_PATH, resource));
            }
            log.debug("[ypbin-starter] captcha 背景图已注册（{}，{} 张）。", type, resources.size());
        }
    }
}

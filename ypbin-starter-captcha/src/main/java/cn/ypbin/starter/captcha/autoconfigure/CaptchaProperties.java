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

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 验证码配置项。
 *
 * @author wenbin
 * @since 2026-08-07
 */
@ConfigurationProperties(prefix = CaptchaProperties.PREFIX)
public class CaptchaProperties {

    public static final String PREFIX = "ypbin.captcha";

    /** 是否启用验证码自动配置 */
    private boolean enabled = true;

    /**
     * 自定义背景图资源（classpath 相对路径，如 {@code captcha/bg/1.jpg}，对应 {@code resources/captcha/bg/1.jpg}）。
     * 为空时回退加载 tianai 内置的单张默认背景图；配置多张时全部注册，验证码随机取用，避免背景单一。
     */
    private List<String> backgroundResources = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getBackgroundResources() {
        return backgroundResources;
    }

    public void setBackgroundResources(List<String> backgroundResources) {
        this.backgroundResources = backgroundResources;
    }
}

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
package cn.ypbin.starter.web.autoconfigure;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Web 默认属性注入器。
 *
 * <p>以最低优先级注入框架推荐的默认配置，使 404（无匹配处理器）能抛出
 * {@code NoHandlerFoundException}，从而被全局异常处理器捕获并返回统一 JSON，
 * 而非默认的 HTML Whitelabel 错误页。业务方在 {@code application.yml} 中的配置优先级更高，
 * 可随时覆盖。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class WebDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "ypbinWebDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }
        Map<String, Object> defaults = new HashMap<>();
        // 让 DispatcherServlet 在无匹配处理器时抛异常，交由全局异常处理器统一处理
        defaults.put("spring.mvc.throw-exception-if-no-handler-found", "true");
        // 关闭静态资源默认映射，避免 404 被资源处理器吞掉而不抛异常
        defaults.put("spring.web.resources.add-mappings", "false");
        // 追加到末尾：优先级最低，用户配置可覆盖
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
    }

    @Override
    public int getOrder() {
        // 尽量靠后，确保不覆盖用户及其它更高优先级来源
        return Ordered.LOWEST_PRECEDENCE;
    }
}

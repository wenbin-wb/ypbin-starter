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
package cn.ypbin.starter.messaging.autoconfigure;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * 消息默认属性注入器。
 *
 * <p>以最低优先级注入框架推荐的默认配置，使 sms4j 启动时不再打印第三方横幅，
 * 收敛与控制台输出（MyBatis-Plus、Sa-Token 等其它组件已由各自配置关闭）。业务方在
 * {@code application.yml} 中的配置优先级更高，可随时覆盖。</p>
 *
 * @author wenbin
 * @since 2026-08-06
 */
public class SmsDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "ypbinSmsDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }
        Map<String, Object> defaults = new HashMap<>();
        // 关闭 sms4j 启动横幅（SmsConfig.isPrint 默认 true，SmsMainConfig.init 据此打印）
        defaults.put("sms.is-print", "false");
        // 追加到末尾：优先级最低，用户配置可覆盖
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
    }

    @Override
    public int getOrder() {
        // 尽量靠后，确保不覆盖用户及其它更高优先级来源
        return Ordered.LOWEST_PRECEDENCE;
    }
}

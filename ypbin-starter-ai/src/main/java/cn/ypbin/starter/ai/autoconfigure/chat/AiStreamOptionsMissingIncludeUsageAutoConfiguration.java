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
package cn.ypbin.starter.ai.autoconfigure.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.ConfigurationPropertyState;
import org.springframework.core.env.Environment;

/**
 * AI「自定义了 stream-options 却没显式开启 include-usage」的启动期告警。
 *
 * <p>Spring AI 2.0.1 的 {@code OpenAiChatModel} 组装流式请求时，<strong>仅当
 * {@code OpenAiChatOptions#getStreamOptions()} 为 {@code null}</strong>才默认请求
 * {@code stream_options.include_usage=true}；一旦该字段非空，请求里的 {@code include_usage}
 * 就只取 {@code StreamOptions#includeUsage} 的值（{@code Boolean.TRUE.equals(...)}，未配置即 {@code false}）。
 * 于是宿主只要写了 {@code spring.ai.openai.chat.options.stream-options} 下的<strong>任意一项</strong>
 * （如 {@code include-obfuscation} 或 {@code additional-properties.*}）却没写 {@code include-usage: true}，
 * 上游就不回传用量：用量统计里的 token 会全部变成 {@code null}（未知）——<b>不报错、只丢数据</b>，
 * 属最难排查的一类静默失效，故在启动期显式说出来（禁静默失效）。</p>
 *
 * <p><b>默认必须安静</b>：宿主完全没配置 {@code stream-options.*} 时框架会替其默认请求
 * {@code include_usage=true}，是正常情形，本配置不打任何日志（否则会变成噪音与误报）。</p>
 *
 * <p><b>不作运行时兜底</b>：本配置只告警，<b>不</b>替宿主补 {@code include-usage}。自动补开会把
 * 「我配了却没生效」变成隐形行为，且等于替宿主做决定。</p>
 *
 * <p><b>生效范围</b>：该组属性只在宿主自行装配 yml OpenAI 模型（容器内存在 {@code ChatModel} Bean、
 * starter 直接复用）时参与请求组装；模型配置表驱动的主路径自建客户端且不读取 {@code spring.ai.*}，
 * 不受影响。</p>
 *
 * @author wenbin
 * @since 2026-09-18
 */
@AutoConfiguration
@ConditionalOnClass(ChatModel.class)
public class AiStreamOptionsMissingIncludeUsageAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AiStreamOptionsMissingIncludeUsageAutoConfiguration.class);

    /** OpenAI 流式选项前缀：宿主写了该前缀下任意一项，框架即不再默认请求用量。 */
    private static final String STREAM_OPTIONS_PREFIX = "spring.ai.openai.chat.options.stream-options";

    /** 显式开启用量回传的完整配置键。 */
    private static final String INCLUDE_USAGE_KEY = STREAM_OPTIONS_PREFIX + ".include-usage";

    AiStreamOptionsMissingIncludeUsageAutoConfiguration(Environment environment) {
        if (!isStreamOptionsConfigured(environment)) {
            // 宿主完全没配 stream-options.*：框架会自行请求 stream_options.include_usage=true，正常情形，保持安静
            return;
        }
        if (Binder.get(environment).bind(INCLUDE_USAGE_KEY, Boolean.class).orElse(false)) {
            return;
        }
        log.warn("[ypbin-ai] 检测到已配置 {}.*（自定义流式选项）但 {} 不是 true："
            + "AI 用量将无法统计——上游不再回传 usage，用量回调里的三个 token 会被如实记为 null（未知）"
            + "而不是 0；原因：Spring AI 只在宿主未配置 stream-options 时才默认请求 "
            + "stream_options.include_usage=true，配置了该组中的任意一项后，是否请求用量就只由该组内的 "
            + "include-usage 决定（不写等于不回传）。请在配置中显式补上 {}: true。",
            STREAM_OPTIONS_PREFIX, INCLUDE_USAGE_KEY, INCLUDE_USAGE_KEY);
    }

    /**
     * 判断宿主是否配置了 {@code stream-options} 下的<strong>任意一项</strong>。
     *
     * <p>按属性名（而非绑定结果）判定：前缀下可能有未知子键（如 {@code additional-properties.*}），
     * 用 Boot 自身的「该前缀下是否有后代属性」语义可覆盖全部情形，也不会因某个值无法转换而抛错；
     * 属性源不可枚举时结论为 {@code UNKNOWN}，此时按「未配置」处理（宁可安静，不制造误报）。</p>
     *
     * @param environment 环境
     * @return {@code true} 表示前缀下至少存在一个属性
     */
    private static boolean isStreamOptionsConfigured(Environment environment) {
        ConfigurationPropertyName prefix = ConfigurationPropertyName.of(STREAM_OPTIONS_PREFIX);
        for (ConfigurationPropertySource source : ConfigurationPropertySources.get(environment)) {
            if (source.containsDescendantOf(prefix) == ConfigurationPropertyState.PRESENT) {
                return true;
            }
        }
        return false;
    }
}

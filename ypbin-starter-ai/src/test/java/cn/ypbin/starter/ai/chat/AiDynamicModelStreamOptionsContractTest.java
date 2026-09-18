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
package cn.ypbin.starter.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * 「只设模型名」的 OpenAI 选项不自带 {@code stream-options} 的契约哨兵。
 *
 * <p>配置表驱动主路径（{@link DefaultAiChatService#resolveClient()}，只调用
 * {@code OpenAiChatOptions.builder().model(...)}，从不调用 {@code streamOptions(...)}）之所以能拿到用量，
 * 是因为 Spring AI 2.0.1 的 {@code OpenAiChatModel} 仅在 {@code getStreamOptions() == null} 时才默认请求
 * {@code stream_options.include_usage=true}。本用例把该前提固化为可执行断言：一旦框架升级改变默认
 * （例如 builder 自动补一个 stream-options），用量统计会静默失效，此处必须转红而不是无声退化。</p>
 *
 * <p><b>级别说明</b>：本用例只证明「starter 自建选项的 stream-options 为空」，属单测级；
 * 「上游真的按 include_usage=true 回传用量」需真机调用才能证实，未在此覆盖。</p>
 *
 * @author wenbin
 * @since 2026-09-18
 */
class AiDynamicModelStreamOptionsContractTest {

    @Test
    void optionsBuiltWithModelOnlyHaveNoStreamOptions() {
        OpenAiChatOptions options = OpenAiChatOptions.builder().model("deepseek-v4-flash").build();

        assertThat(options.getStreamOptions()).isNull();
    }
}

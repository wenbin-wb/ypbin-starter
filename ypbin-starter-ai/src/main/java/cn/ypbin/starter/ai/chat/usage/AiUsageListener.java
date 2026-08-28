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
package cn.ypbin.starter.ai.chat.usage;

/**
 * AI Token 消耗与用量监听器 SPI。
 *
 * <p>业务系统实现本接口并注入 Spring 容器后，AI 对话每次完成后将自动触发本回调，
 * 用于审计日志记录、计费扣减或用量监控看板统计。</p>
 *
 * @author wenbin
 * @since 2026-08-28
 */
@FunctionalInterface
public interface AiUsageListener {

    /**
     * 接收 AI 对话 Token 消耗用量数据。
     *
     * @param usage 用量元数据
     */
    void onUsage(AiUsageInfo usage);
}

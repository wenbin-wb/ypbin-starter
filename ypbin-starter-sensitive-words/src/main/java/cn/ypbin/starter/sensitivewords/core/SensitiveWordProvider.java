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
package cn.ypbin.starter.sensitivewords.core;

import java.util.Collection;

/**
 * 敏感词词库提供者扩展点。
 *
 * <p>敏感词来源因业务而异（配置文件、数据库、远程服务），本模块不预设词库，由业务方实现本接口
 * 提供词集合。未提供实现时使用配置项 {@code ypbin.sensitive-words.words} 中的静态词库。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@FunctionalInterface
public interface SensitiveWordProvider {

    /**
     * 提供敏感词集合。
     *
     * @return 敏感词集合
     */
    Collection<String> getWords();
}

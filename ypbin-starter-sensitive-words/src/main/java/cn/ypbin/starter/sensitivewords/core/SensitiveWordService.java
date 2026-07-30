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

import cn.hutool.dfa.WordTree;
import java.util.Collection;
import java.util.List;

/**
 * 敏感词服务。
 *
 * <p>基于 Hutool DFA（{@link WordTree}）做敏感词检测与替换。词库变更后调用 {@link #reload}
 * 重建词树。词树读多写少，用 volatile 引用保证可见性，重载时整体替换避免读到半更新状态。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class SensitiveWordService {

    private volatile WordTree wordTree;

    public SensitiveWordService(Collection<String> words) {
        reload(words);
    }

    /**
     * 重新加载词库。
     *
     * @param words 敏感词集合
     */
    public void reload(Collection<String> words) {
        WordTree tree = new WordTree();
        if (words != null) {
            words.stream().filter(w -> w != null && !w.isBlank()).forEach(tree::addWord);
        }
        this.wordTree = tree;
    }

    /**
     * 是否包含敏感词。
     *
     * @param text 待检测文本
     * @return 是否命中
     */
    public boolean contains(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return wordTree.isMatch(text);
    }

    /**
     * 找出文本中所有命中的敏感词。
     *
     * @param text 待检测文本
     * @return 命中的敏感词列表（可能重复出现，按出现顺序）
     */
    public List<String> findAll(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        return wordTree.matchAll(text);
    }

    /**
     * 将文本中的敏感词替换为指定字符。
     *
     * @param text        待处理文本
     * @param replacement 替换字符（每个敏感字符替换为一个该字符）
     * @return 替换后的文本
     */
    public String filter(String text, char replacement) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        List<String> hits = wordTree.matchAll(text);
        String result = text;
        for (String hit : hits) {
            result = result.replace(hit, String.valueOf(replacement).repeat(hit.length()));
        }
        return result;
    }
}

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
package cn.ypbin.starter.sensitivewords.util;

import cn.ypbin.starter.core.util.SpringUtils;
import cn.ypbin.starter.sensitivewords.core.SensitiveWordService;
import java.util.Collection;
import java.util.List;

/**
 * 敏感词静态工具。
 *
 * <p>面向非 Spring 托管场景（校验工具、DTO 自校验、静态方法等）提供敏感词检测/替换，内部委托容器中的
 * {@link SensitiveWordService} 单例。首次调用时经 {@link SpringUtils} 懒获取并缓存该 Bean 引用。</p>
 *
 * <p>Spring 托管组件仍应优先直接注入 {@link SensitiveWordService}。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
public final class SensitiveWordUtils {

    private static volatile SensitiveWordService service;

    private SensitiveWordUtils() {
    }

    /**
     * 懒获取容器中的 {@link SensitiveWordService} Bean（双重检查，线程安全）。
     *
     * @return 敏感词服务实例
     */
    private static SensitiveWordService service() {
        if (service == null) {
            synchronized (SensitiveWordUtils.class) {
                if (service == null) {
                    service = SpringUtils.getBean(SensitiveWordService.class);
                }
            }
        }
        return service;
    }

    /**
     * 是否包含敏感词。
     *
     * @param text 待检测文本
     * @return 是否命中
     */
    public static boolean contains(String text) {
        return service().contains(text);
    }

    /**
     * 找出文本中所有命中的敏感词。
     *
     * @param text 待检测文本
     * @return 命中的敏感词列表
     */
    public static List<String> findAll(String text) {
        return service().findAll(text);
    }

    /**
     * 将文本中的敏感词替换为指定字符。
     *
     * @param text        待处理文本
     * @param replacement 替换字符
     * @return 替换后的文本
     */
    public static String filter(String text, char replacement) {
        return service().filter(text, replacement);
    }

    /**
     * 重新加载词库（词库变更后调用）。
     *
     * @param words 敏感词集合
     */
    public static void reload(Collection<String> words) {
        service().reload(words);
    }
}

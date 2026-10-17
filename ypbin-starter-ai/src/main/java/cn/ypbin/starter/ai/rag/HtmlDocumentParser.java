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
package cn.ypbin.starter.ai.rag;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.ai.document.Document;

/**
 * html 解析器（jsoup）——把 jsoup 字节码从 {@link DocumentLoader} 隔离出来的实现类。
 *
 * <p><b>为何必须单独成类（本次实测的硬约束）：</b>{@code jsoup} 在本模块声明为 {@code optional}。
 * 若 jsoup 类型出现在 {@link DocumentLoader} 的方法体局部变量里（如 {@code Element main}），
 * StackMapTable 会让类校验阶段加载 {@code org.jsoup.nodes.Element}；jsoup 缺席时
 * {@link DocumentLoader} 在首次调用即抛 {@code NoClassDefFoundError: org/jsoup/nodes/Element}
 * ——连 txt/markdown 都解析不了（2026-10-16 剔除 jsoup jar 后运行 {@code loadAndChunk("a.txt")} 的实证，
 * 此时 {@code parseHtml} 根本没有被调用）。隔离后 {@link DocumentLoader} 不引用任何 jsoup 类型，
 * 由 {@code DocumentLoader#requireClass(String, String, String)} 做存在性探测。</p>
 *
 * <p>本类是包内实现细节，不作为对外 API。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
final class HtmlDocumentParser {

    private HtmlDocumentParser() {}

    /**
     * 解析 html：优先提取 article/main/[role=main] 正文，退回 body。
     *
     * @param bytes    文件字节（按 UTF-8 解码）
     * @param filename 文件名（仅用于 metadata）
     * @return 解析出的文档（未切片）
     */
    static List<Document> parse(byte[] bytes, String filename) {
        String html = new String(bytes, StandardCharsets.UTF_8);
        Element doc = Jsoup.parse(html);
        Element main = doc.selectFirst("article,main,[role=main]");
        // 不经 ownerDocument()（声明可空）：改用 body 元素；jsoup 解析后必然存在 body，取不到时退回整篇文本
        Element body = doc.selectFirst("body");
        String text = (main != null ? main : body != null ? body : doc).text();
        return List.of(new Document(text, Map.of("source", filename != null ? filename : "")));
    }
}

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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link DocumentLoader} 的可选依赖（{@code poi-ooxml} / {@code jsoup}）缺席行为回归测试。
 *
 * <p><b>为何用隔离 ClassLoader 而不是剔除 jar</b>：模块自身的测试 classpath 必须带上 POI/jsoup
 * 才能跑 docx/xlsx/html 的正常解析用例，无法在同一个 JVM 里「按用例删依赖」。这里用一个
 * <b>拒绝加载 POI/jsoup</b> 的子 ClassLoader 重新定义 {@link DocumentLoader}，即可在真实 classpath
 * 下复现「宿主未引入 optional 依赖」的形态——等价于 2026-10-16 手工剔除 jar 的实测。</p>
 *
 * <p><b>该测试守的是什么</b>：若有人把 POI/jsoup 类型（尤其局部变量）直接写回
 * {@link DocumentLoader} 的方法体，类校验会为 StackMapTable 加载这些类型，本测试的第一个用例
 * （无 POI/jsoup 时解析 txt）就会以 {@code NoClassDefFoundError} 失败——这正是 optional 化后
 * 必须由 {@link PoiDocumentParser}/{@link HtmlDocumentParser} 隔离的根本原因。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
class DocumentLoaderOptionalDependencyTest {

    private static final String DOCUMENT_LOADER = "cn.ypbin.starter.ai.rag.DocumentLoader";

    @Test
    @DisplayName("★ 无 POI/jsoup 时 DocumentLoader 仍可链接：txt 必须能正常解析")
    void plainTextStillWorksWithoutOptionalDependencies() throws Exception {
        Object chunks = invokeWithoutOptionalDeps("note.txt", "hello world".getBytes(StandardCharsets.UTF_8));

        assertThat((List<?>) chunks)
            .as("jsoup 缺席不得让整个 DocumentLoader 不可用（2026-10-16 实测：直接引用 Element 的旧实现"
                + "会让 txt 也抛 NoClassDefFoundError: org/jsoup/nodes/Element）")
            .isNotEmpty();
    }

    @Test
    @DisplayName("★ 无 POI 时 docx/xlsx 抛可操作的 IllegalStateException（含依赖坐标），而非 NoClassDefFoundError")
    void docxAndXlsxReportActionableErrorWithoutPoi() {
        Throwable docx = failureWithoutOptionalDeps("a.docx", new byte[] {1, 2, 3});
        assertThat(docx)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("org.apache.poi:poi-ooxml")
            .hasMessageContaining("docx");

        Throwable xlsx = failureWithoutOptionalDeps("a.xlsx", new byte[] {1, 2, 3});
        assertThat(xlsx)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("org.apache.poi:poi-ooxml")
            .hasMessageContaining("xlsx");
    }

    @Test
    @DisplayName("★ 无 jsoup 时 html 抛可操作的 IllegalStateException（含依赖坐标），而非 NoClassDefFoundError")
    void htmlReportsActionableErrorWithoutJsoup() {
        Throwable html = failureWithoutOptionalDeps("a.html",
            "<html><body>hi</body></html>".getBytes(StandardCharsets.UTF_8));

        assertThat(html)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("org.jsoup:jsoup")
            .hasMessageContaining("html");
    }

    @Test
    @DisplayName("可选依赖探测：存在返回 true，不存在返回 false")
    void isClassPresentProbeReflectsReality() {
        assertThat(DocumentLoader.isClassPresent("org.apache.poi.xwpf.usermodel.XWPFDocument")).isTrue();
        assertThat(DocumentLoader.isClassPresent("org.jsoup.Jsoup")).isTrue();
        assertThat(DocumentLoader.isClassPresent("cn.ypbin.starter.ai.rag.NoSuchClassForProbe")).isFalse();
    }

    /** 在「拒绝 POI/jsoup」的隔离 ClassLoader 中调用 {@link DocumentLoader#loadAndChunk}。 */
    private static Object invokeWithoutOptionalDeps(String filename, byte[] bytes) throws Exception {
        ClassLoader loader = new BlockingClassLoader(
            DocumentLoaderOptionalDependencyTest.class.getClassLoader(),
            DOCUMENT_LOADER,
            "org.apache.poi.",
            "org.jsoup.");
        Class<?> type = loader.loadClass(DOCUMENT_LOADER);
        Method method = type.getMethod("loadAndChunk", byte[].class, String.class, Map.class);
        return method.invoke(null, bytes, filename, Map.of());
    }

    /** 取隔离 ClassLoader 下调用的失败原因；未失败返回 {@code null}。 */
    private static Throwable failureWithoutOptionalDeps(String filename, byte[] bytes) {
        try {
            invokeWithoutOptionalDeps(filename, bytes);
            return null;
        } catch (InvocationTargetException e) {
            return e.getCause();
        } catch (Exception e) {
            return e;
        }
    }

    /**
     * 拒绝加载指定前缀（模拟 optional 依赖缺席）、并把 {@code childLoadedClass} 定义在自身
     * （保证其 classloader 就是本加载器）的 ClassLoader。
     */
    private static final class BlockingClassLoader extends ClassLoader {

        private final String childLoadedClass;

        private final String[] blockedPrefixes;

        BlockingClassLoader(ClassLoader parent, String childLoadedClass, String... blockedPrefixes) {
            super(parent);
            this.childLoadedClass = childLoadedClass;
            this.blockedPrefixes = blockedPrefixes.clone();
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            for (String prefix : blockedPrefixes) {
                if (name.startsWith(prefix)) {
                    throw new ClassNotFoundException(name + "（本测试模拟 optional 依赖缺席）");
                }
            }
            if (name.equals(childLoadedClass)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    loaded = defineFromParent(name);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
            return super.loadClass(name, resolve);
        }

        private Class<?> defineFromParent(String name) throws ClassNotFoundException {
            try (InputStream in = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                if (in == null) {
                    throw new ClassNotFoundException(name);
                }
                byte[] bytes = in.readAllBytes();
                return defineClass(name, bytes, 0, bytes.length);
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        }
    }
}

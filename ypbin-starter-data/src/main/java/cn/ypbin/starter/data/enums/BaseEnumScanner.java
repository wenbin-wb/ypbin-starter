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
package cn.ypbin.starter.data.enums;

import cn.ypbin.starter.core.enums.BaseEnum;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.filter.AssignableTypeFilter;

/**
 * 扫描 classpath 上所有 {@link BaseEnum} 的枚举实现。
 *
 * <p><b>为什么必须扫描</b>：MyBatis 的类型处理器注册是「类型 → 处理器」的显式映射，没有
 * 「按接口自动匹配」的能力。而实体的枚举字段是<b>具体枚举类</b>，要覆盖它们只能预先知道类型集合。
 * 扫描是唯一能让 starter「装上就生效」的办法——否则每个业务枚举都要手写
 * {@code @TableField(typeHandler = ...)}，这正是 #102 指出的「BaseEnum 零引用」的根源。</p>
 *
 * <p><b>为什么不用「classpath 通配所有 .class」的方式</b>：那会把整个 classpath 的字节码都读一遍
 * （含所有依赖 jar），启动代价高。{@link ClassPathScanningCandidateComponentProvider} 走 ASM 读元数据，
 * 不加载类，且用 {@link AssignableTypeFilter} 在字节码层判断继承关系，只对候选类做反射校验。</p>
 *
 * <h2>两个入口与「0 命中」的两种含义（关键区分）</h2>
 *
 * <p>「扫描到 0 个枚举」有两种完全不同的成因，处置也完全不同——混为一谈要么制造噪音、要么放过静默失效：</p>
 * <ol>
 *   <li><b>宿主根本没用这个能力</b>（classpath 上没有任何 {@link BaseEnum} 实现）：属正常情况，
 *       不应 WARN——否则绝大多数不涉及枚举落库的宿主每次启动都被一条无用告警骚扰，
 *       久而久之所有人都会忽略这条告警；</li>
 *   <li><b>有实现但没扫到</b>（classpath 上存在 {@link BaseEnum} 实现，却不在配置的
 *       {@code scan-packages} 内）：这是<b>配置错误</b>。所有 {@code BaseEnum} 会静默按
 *       {@code name()} 落库，与该能力「按 value 存储」的契约不符，且与其它服务/前端按 value
 *       约定的数据不一致，必须 WARN。</li>
 * </ol>
 *
 * <p>为区分二者，本类提供 {@link #scanAll()}（不带包过滤的全 classpath 扫描，仅用于上面的判别），
 * 调用成本见该方法注释。</p>
 *
 * <p><b>扫不到的边界</b>：JDK 模块、非目录/非 jar 的类加载器（如某些动态代理）可能扫不到。
 * 这类枚举仍需业务显式标注 {@code @TableField(typeHandler = BaseEnumTypeHandler.class)}
 * 或自行注册；这是已知局限，不在运行时假装成功。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
public final class BaseEnumScanner {

    private static final Logger log = LoggerFactory.getLogger(BaseEnumScanner.class);

    /** 全 classpath 扫描的根包（空前缀 = 从 classpath 根开始）。 */
    private static final String CLASSPATH_ROOT = "";

    /** 全量诊断扫描的默认资源上界（够覆盖普通应用，又不至于把低内存宿主的堆吃光）。 */
    public static final long DEFAULT_MAX_RESOURCES = 20_000L;

    /**
     * 不可能定义本框架 {@code BaseEnum} 的类名前缀（全量诊断扫描时跳过）。
     *
     * <p>刻意用「前缀白名单外一律扫描」而不是「只扫某几个包」：本诊断的用途正是发现
     * <b>未知位置</b>的实现，收得太紧会漏判。这里只排除确定无关且体积大的三方/平台包。</p>
     */
    private static final List<String> SKIPPED_CLASS_PREFIXES = List.of(
        "java.", "javax.", "jakarta.", "jdk.", "sun.", "com.sun.",
        "groovy.", "org.codehaus.groovy.",
        "org.springframework.", "org.apache.", "org.slf4j.", "org.jspecify.",
        "com.fasterxml.", "com.google.", "io.netty.", "io.micrometer.",
        "ch.qos.", "lombok.", "org.junit.", "org.mockito.", "net.bytebuddy.",
        "reactor.", "com.baomidou.", "com.zaxxer.", "cn.hutool.");

    private BaseEnumScanner() {
    }

    /**
     * 扫描指定包下所有 {@link BaseEnum} 的枚举实现。
     *
     * @param basePackages 扫描根包（不可为 null；空集合返回空列表）
     * @return 枚举类型集合（顺序稳定；非 null；不含接口与抽象类型）
     */
    public static List<Class<?>> scan(Iterable<String> basePackages) {
        Objects.requireNonNull(basePackages, "basePackages");
        Set<Class<?>> found = new LinkedHashSet<>();
        ClassPathScanningCandidateComponentProvider scanner = newScanner();
        for (String basePackage : basePackages) {
            if (basePackage == null || basePackage.isBlank()) {
                continue;
            }
            collect(scanner, basePackage, found);
        }
        if (log.isDebugEnabled()) {
            log.debug("[ypbin-starter] base-enum scan finished, {} enum type(s) found.", found.size());
        }
        return List.copyOf(found);
    }

    /**
     * 不带包过滤地扫描<b>整个 classpath</b>上的 {@link BaseEnum} 实现。
     *
     * <p><b>用途仅限判别「0 命中的成因」</b>：装配层只有在 {@link #scan(Iterable)} 返回空集合时
     * 才调本方法一次，用来区分「宿主没用该能力」（正常，INFO）与「有实现但 scan-packages 配错」
     * （配置错误，WARN）。<b>不要每次启动都调用</b>——上限保护就落在这个「仅在 0 命中时触发一次」的
     * 调用约束上。</p>
     *
     * <p><b>成本</b>：以空前缀为根包，会遍历 classpath 上所有目录与 jar 的条目。读的是字节码元数据
     * （ASM）而非加载类，但仍是本模块启动期最重的一次扫描。正常宿主（scan-packages 能命中）走不到
     * 这里；只有「不涉及该能力的宿主」会付一次，这个交换是可接受的。</p>
     *
     * @return 全 classpath 上的枚举实现（非 null；空列表表示宿主确实没有该能力的实现）
     */
    public static List<Class<?>> scanAll() {
        return scanAll(DEFAULT_MAX_RESOURCES);
    }

    /**
     * 带上界的全 classpath 扫描（诊断专用）。
     *
     * <p><b>为什么需要上界</b>（2026-10-20 真实事故）：某宿主（`-Xmx384m`）零个 {@code BaseEnum}
     * 实现 ⇒ 走本诊断 ⇒ 以空前缀扫全 classpath 时读到 groovy 巨类，直接 {@code OutOfMemoryError}
     * 把服务打挂。「诊断信息」不该有这个代价。故本方法：</p>
     * <ol>
     *   <li><b>跳过三方/平台的类名前缀</b>（{@link #SKIPPED_CLASS_PREFIXES}）——groovy、Spring、
     *       Apache、Netty 等不可能定义本框架的 {@code BaseEnum}，白扫且最贵；</li>
     *   <li><b>硬上界</b>：检查超过 {@code maxResources} 个资源即中止并返回已找到的结果
     *       （中止原因记 WARN，不静默），避免「诊断」把内存吃光。</li>
     * </ol>
     *
     * <p>语义退化说明：上界被触发时返回的是<b>部分</b>结果，可能误判为「宿主没有实现」。
     * 这是刻意取舍——诊断结论的可信度低于服务可用性；日志里会写明「扫描已中止」。</p>
     *
     * @param maxResources 最多检查的 classpath 资源数（必须 &gt; 0）
     * @return 扫描到的枚举实现（非 null；可能因上界而只是部分结果）
     */
    public static List<Class<?>> scanAll(long maxResources) {
        if (maxResources <= 0) {
            throw new IllegalArgumentException("maxResources 必须为正数: " + maxResources);
        }
        Set<Class<?>> found = new LinkedHashSet<>();
        BoundedScanner scanner = new BoundedScanner(maxResources);
        try {
            for (BeanDefinition candidate : scanner.findCandidateComponents(CLASSPATH_ROOT)) {
                Class<?> enumType = resolveEnumType(candidate.getBeanClassName());
                if (enumType != null) {
                    found.add(enumType);
                }
            }
        } catch (RuntimeException ex) {
            if (!isScanAborted(ex)) {
                throw ex;
            }
            // 上界命中：诊断降级为「部分结果」，但必须留痕（禁静默降级）
            log.warn("[ypbin-starter] base-enum 全量诊断扫描已达上界（{} 个资源）并中止，"
                + "结论可能不完整（只用于判断「是否零实现」，不作业务判据）。", maxResources);
        }
        if (log.isDebugEnabled()) {
            log.debug("[ypbin-starter] base-enum full-classpath scan finished, {} enum type(s) found.",
                found.size());
        }
        return List.copyOf(found);
    }

    /** 建扫描器：只认「实现了 BaseEnum」的候选，避免把全 classpath 的类都反射一遍。 */
    private static ClassPathScanningCandidateComponentProvider newScanner() {
        ClassPathScanningCandidateComponentProvider scanner =
            new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(BaseEnum.class));
        return scanner;
    }

    /**
     * 带上界的候选扫描器：按资源数计上界，并在读元数据前按类名前缀跳过三方包。
     *
     * <p>上界通过抛出 {@link ScanAbortedException} 实现——Spring 的扫描 API 没有「提前停止」的钩子，
     * 只有从 {@code isCandidateComponent} 抛异常才能中断遍历。异常不逃出本类：{@link #scanAll(long)}
     * 捕获后按「部分结果」处理。</p>
     */
    private static final class BoundedScanner extends ClassPathScanningCandidateComponentProvider {

        private final long maxResources;

        private long examined;

        BoundedScanner(long maxResources) {
            super(false);
            addIncludeFilter(new AssignableTypeFilter(BaseEnum.class));
            this.maxResources = maxResources;
        }

        @Override
        protected boolean isCandidateComponent(MetadataReader metadataReader) throws IOException {
            if (++examined > maxResources) {
                throw new ScanAbortedException();
            }
            if (isSkipped(metadataReader.getClassMetadata().getClassName())) {
                return false;
            }
            return super.isCandidateComponent(metadataReader);
        }

        private static boolean isSkipped(String className) {
            for (String prefix : SKIPPED_CLASS_PREFIXES) {
                if (className.startsWith(prefix)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * 判断异常链里是否含「扫描上界」信号。
     *
     * <p>Spring 会把 {@code isCandidateComponent} 抛出的运行时异常包装成
     * {@link org.springframework.beans.factory.BeanDefinitionStoreException}，所以不能只 catch 本类异常，
     * 必须沿 cause 链识别——否则上界信号会被当成真实故障抛给宿主，反而把启动搞崩。</p>
     *
     * @param throwable 捕获到的异常
     * @return {@code true} = 这是上界中止信号（可安全按部分结果处理）
     */
    private static boolean isScanAborted(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ScanAbortedException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /** 扫描上界命中的内部控制信号（不逃出本类）。 */
    private static final class ScanAbortedException extends RuntimeException {

        ScanAbortedException() {
            super(null, null, false, false);
        }
    }

    /** 扫描单个根包并把具体枚举收入 {@code found}。 */
    private static void collect(ClassPathScanningCandidateComponentProvider scanner,
                                String basePackage,
                                Set<Class<?>> found) {
        for (BeanDefinition candidate : scanner.findCandidateComponents(basePackage)) {
            Class<?> enumType = resolveEnumType(candidate.getBeanClassName());
            if (enumType != null) {
                found.add(enumType);
            }
        }
    }

    /**
     * 校验候选类确实是 {@link BaseEnum} 的具体枚举。
     *
     * <p>{@link AssignableTypeFilter} 只能判断「是 BaseEnum 的子类型」，也会把实现了 BaseEnum 的
     * 普通类（非枚举）纳入；此处再按「枚举 + 非抽象」过滤，避免把非枚举类型注册给 MyBatis
     * 的类型处理器（MyBatis 会因此报错或静默不匹配）。</p>
     *
     * @param className 候选类名（可为 {@code null}：{@code BeanDefinition#getBeanClassName} 在
     *                  「由工厂方法/Supplier 定义」的候选上返回 null，扫描器必须容忍）
     * @return 枚举类型；不是具体枚举时返回 {@code null}
     */
    @Nullable
    private static Class<?> resolveEnumType(@Nullable String className) {
        if (className == null) {
            return null;
        }
        try {
            Class<?> candidate = Class.forName(className, false, classLoader());
            if (!candidate.isEnum() || Modifier.isAbstract(candidate.getModifiers())) {
                return null;
            }
            return candidate;
        } catch (ClassNotFoundException ex) {
            // 扫描到的候选类加载失败属于「环境不一致」，记录后跳过而不是让整个应用启动失败：
            // 该枚举会退回 MyBatis 默认枚举处理（name 存储），业务可用显式注册修正
            log.warn("[ypbin-starter] base-enum candidate '{}' cannot be loaded, skipped.", className, ex);
            return null;
        }
    }

    private static ClassLoader classLoader() {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        return contextClassLoader != null ? contextClassLoader : BaseEnumScanner.class.getClassLoader();
    }
}

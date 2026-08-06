---
name: ypbin-starter-dev
description: ypbin-starter 框架库开发与合规审计标准。开发任何 starter 能力模块（AutoConfiguration/Properties/静态门面 Utils/Service 抽象），或审查已有 starter Java 代码是否合规时使用。强制自动装配约定、Bean 全可覆盖、ypbin.* 配置前缀、静态门面双检、类必带 @author wenbin + @since、禁内联全限定类名、禁静默降级、异常统一 200、分层 L1 不依赖 Spring Cloud、代码/文档禁品牌关键字。
---

# ypbin-starter-dev — ypbin-starter 框架库标准

面向 `ypbin-starter`（自研 Spring Boot 3 starter 合集，JDK 17 / Spring Boot 3.5.x / Spring Cloud 2025.x，发布到 Maven Central，groupId `cn.ypbin`）。它**不是业务应用，是被别人依赖的框架库**——每个能力模块提供「约定优于配置」的开箱即用能力，宿主项目（如 ypbin-admin）只 `<dependency>` 引入 + 少量 `ypbin.*` 配置即可用。定位与 ypbin-admin 的业务代码根本不同，规约也不同。

模块分层（见 ROADMAP.md）：
- **L1 基础层**（core / json / web / data / cache / log / security / i18n / tools 等）：不依赖 Spring Cloud，单体可用。
- **L2 扩展层**（extension-crud / extension-tenant / extension-datapermission）：在 L1 上叠通用业务骨架。
- **L3 微服务层**（cloud-core / cloud-nacos / cloud-gateway / cloud-sentinel / cloud-loadbalancer / cloud-observability）：依赖 Spring Cloud。
- **聚合/依赖**（app-web / app-cloud 起步聚合，bom / dependencies 版本管理）。

两种模式：
- **开发模式**：新增/扩展能力模块时，严格按下面的「新建能力模块配方」与铁律落地。
- **审计模式**：审查已有代码时，逐条对照「铁律」与「审计清单」，输出违规项 + 整改建议。

作者署名统一 `wenbin`。改动只编译验证不启动服务；本机命令行 PATH 无 java/mvn，但可直接调 IDEA 内置 Maven + `.jdks/azul-17.0.18` 跑构建（memory `build-env`，命令见「验证」段）。

## 铁律（RED — 违反即判不合格，必须整改）

这些是 starter 的硬约束，**全部不可豁免**。前 4 条是框架库特有的装配约定，后几条与 ypbin-admin 共享（来自用户明确表态，见各条关联 memory）。

1. **能力模块走标准自动装配，不靠宿主手动 `@Import`/`@ComponentScan`**。每个能力模块必须有：
   - `autoconfigure/XxxAutoConfiguration.java`，类上 `@AutoConfiguration`（需排序时用 `@AutoConfiguration(before/after = ...)`，参照 `CacheAutoConfiguration` 声明在 `RedisAutoConfiguration` 之前）。
   - `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`，每行一个 AutoConfiguration 全限定名。**新增 AutoConfiguration 必须同步登记到该文件**，否则不生效。
   - 禁止用 Spring Boot 2 的老式 `spring.factories`（Boot 3 已废弃）。

2. **每个对外 Bean 都要可被宿主覆盖，且按需生效**（约定优于配置的底线）。
   - `@Bean` 方法必带 `@ConditionalOnMissingBean`（或 `@ConditionalOnMissingBean(name = "...")`），保证宿主自定义同类型 Bean 时 starter 让位。
   - 依赖第三方类的装配加 `@ConditionalOnClass(Xxx.class)`（如 `@ConditionalOnClass(RedisTemplate.class)`），classpath 没有该类时整个配置静默不装配（这是**按条件不装配**，不是铁律6的静默降级）。
   - 能力开关用 `@ConditionalOnProperty(prefix = "ypbin.xxx", name = "enabled", havingValue = "true", matchIfMissing = true)`——默认开启，宿主可显式关。
   - 参照 `CacheAutoConfiguration`（`ypbin-starter-cache`）：`@AutoConfiguration(before=...)` + `@ConditionalOnClass` + `@ConditionalOnProperty` + 每个 `@Bean` 带 `@ConditionalOnMissingBean`。

3. **配置项统一 `ypbin.*` 前缀，用 `@ConfigurationProperties` + `PREFIX` 常量 + 合理默认值**。
   - `@ConfigurationProperties(prefix = XxxProperties.PREFIX)`，类内 `public static final String PREFIX = "ypbin.xxx";`（如 `ypbin.cache.multi-level`）。
   - 每个字段给**开箱即用的默认值**（约定优于配置），字段旁中文注释说明含义/默认。
   - 禁止散落的 `@Value("${...}")` 当模块级配置入口；禁止非 `ypbin.` 前缀的自定义配置命名空间。
   - 参照 `MultiLevelCacheProperties`（`ypbin-starter-cache`）。

4. **静态门面 Utils 统一「volatile + 双重检查 + SpringUtils 委派」范式，且 Javadoc 注明「Spring 组件应直接注入 Service」**。
   - starter 的 `CacheUtils`/`MailUtils`/`AsyncUtils`/`PushUtils`/`SensitiveWordUtils` 是给非 Spring 托管场景用的静态入口，模式固定：`final class` + 私有构造 + `private static volatile XxxService svc;` + `getService()` 里双重检查锁 `svc = SpringUtils.getBean(XxxService.class);` 委派。
   - **业务逻辑必须在 `XxxService` 里**，Utils 只做「取 bean + 转调」的薄壳，不写实现。
   - 类级 Javadoc 必须提示：Spring 管理的组件优先直接注入 `XxxService`，别滥用静态门面。参照 `CacheUtils`（`ypbin-starter-cache`）。

5. **分层依赖纪律：L1 基础层禁止依赖 Spring Cloud**。core/json/web/data/cache/log/security/i18n/tools 等 L1 模块的 `pom.xml` 不得引入 `spring-cloud-*`；需要 Cloud 能力的放 L3（cloud-* 模块）。反向依赖（L1 依赖 L2/L3）同样禁止。加依赖前先确认目标模块所在层级。

6. **禁止内联全限定类名，一律 import**（memory `ypbin-no-inline-fqn`）。方法签名/变量/返回类型/`new`/静态调用里引用其它包的类，先在文件顶部 `import`，正文只写简单类名（`RedisTemplate`，不是 `org.springframework.data.redis.core.RedisTemplate`）。导入顺序按 google-java-format：static 优先，再全字母序（无插件强制，靠 IDEA 保持）。已有内联 FQN 视为违规。

7. **不做掩盖问题的静默降级，错误要暴露**（memory `ypbin-no-silent-fallback`）。
   - 禁止空 `catch {}` 吞异常、失败静默 `return`、失败回退默认值假装正常。
   - **区分「条件装配」与「静默降级」**：`@ConditionalOnClass`/`@ConditionalOnProperty` 导致整个模块不装配，是 Spring 的按需生效机制（铁律2），**不算降级**；坏降级指的是运行期出错后偷偷咽掉、返回假结果。
   - starter 抛业务异常用 `BusinessException`（`ypbin-starter-core`/`-web`，全局处理器转 200 + 业务码）；框架初始化失败/必需配置缺失该显式抛异常或 error 日志，不静默兜底。
   - **例外（合理容错，非降级）**：可选能力探测失败可 warn 后跳过，但**必须记日志让人看见**。判据：错误是否被"看见"——看不见的就是坏降级。

8. **每个类的类级 Javadoc 末尾必带 `@author wenbin` + `@since <当天日期>`**（memory `ypbin-code-conventions`）。
   - 用 `@since`，**不用 `@date`**（非标准标签，IDEA 报未知标签黄线）；**不写版本号**。
   - 顶部保留完整 Apache-2.0 license 头，starter 用 `Copyright (c) 2024-present ypbin-starter authors.` 全块（含 `Unless required by applicable law...` 段，比 admin 长，别搞混）。格式：
     ```java
     /*
      * Copyright (c) 2024-present ypbin-starter authors.
      *
      * Licensed under the Apache License, Version 2.0 (the "License");
      * ...（完整 Apache-2.0 块，见 CacheUtils.java 头部）
      */
     package cn.ypbin.starter.xxx;
     /**
      * 缓存自动配置。
      *
      * @author wenbin
      * @since 2026-08-05
      */
     ```

9. **对外契约统一 `R<T>` + HTTP 200**（memory `ypbin-code-conventions` / `CONTRACT.md`）。starter 里凡产出 HTTP 响应的组件（web 层、异常处理器、拦截器）一律走 `R<T>`（`{code, message, data, success, timestamp}`），HTTP 状态码恒 200，靠 `R.code` 区分（GlobalErrorCode：200/400/401/403/404/409/429/500）。**禁止 `ResponseEntity.status(4xx/5xx)`** 或自定义 REST 语义状态码。改契约结构前先看 `CONTRACT.md`。

10. **代码与文档禁止出现参考项目品牌关键字**。`blade`/`continew` 等参考项目名不得出现在 starter 的类名、包名、注释、README、commit message 里（全部重构自研，见 memory `ypbin-starter`）。对外文档/注释描述能力即可，不引用来源项目。

## 建议（YELLOW — 应遵循，有正当理由可偏离并说明）

- 包结构按能力分层：`autoconfigure/`（装配）、`core/`（接口/抽象）、具体实现子包（如 `redis/`、`multilevel/`）、`util/`（静态门面）、`properties` 或就近 `autoconfigure`。参照 `ypbin-starter-cache` 目录。
- `@Bean` 装配日志用 `log.debug("[ypbin-starter] xxx configured.")`，统一 `[ypbin-starter]` 前缀，便于宿主排查装配情况。
- Service 面向接口设计（`CacheService` 接口 + `RedisCacheService` 实现），宿主可替换实现；Utils 门面只依赖接口。
- 复用容器已有 Bean 时用 `ObjectProvider<Xxx>` 做可选注入 + 兜底（参照 `CacheAutoConfiguration` 对 `ObjectMapper` 的处理），不强依赖可能不存在的 Bean。
- 版本号走 `${revision}`（root pom `1.1.0-SNAPSHOT`）+ flatten-maven-plugin，模块 pom 不写死版本；新模块继承父 pom、依赖版本进 `ypbin-starter-dependencies`/`-bom` 管理，不在子模块散声明版本。
- 单元测试 JUnit 5 + AssertJ（`assertThat`），装配测试用 `ApplicationContextRunner` 验证条件/覆盖行为；测试类同样带 `@author wenbin` + `@since`。参照 `ypbin-starter-cache` 的测试。
- 命名/注释密度向同模块既有代码看齐。commit message 用简洁中文，不带 AI 味、不提参考项目、不提 AI 生成。

## 开发模式：新建一个能力模块配方

以 `ypbin-starter-cache` 为最完整参照。一个 L1 能力模块通常包含：

```
ypbin-starter-xxx/
  pom.xml                                    # 继承父 pom，依赖走 dependencies/bom；L1 不引 spring-cloud
  src/main/java/cn/ypbin/starter/xxx/
    core/XxxService.java                     # 能力接口（面向接口）
    core/DefaultXxxService.java              # 默认实现
    autoconfigure/XxxAutoConfiguration.java  # 装配入口
    autoconfigure/XxxProperties.java         # @ConfigurationProperties(prefix="ypbin.xxx")
    util/XxxUtils.java                        # 可选：静态门面（非 Spring 场景用）
  src/main/resources/META-INF/spring/
    org.springframework.boot.autoconfigure.AutoConfiguration.imports   # 登记 AutoConfiguration
  src/test/java/...                          # JUnit5 + AssertJ + ApplicationContextRunner
```

**Properties**（参照 `MultiLevelCacheProperties`）：
```java
@ConfigurationProperties(prefix = XxxProperties.PREFIX)
public class XxxProperties {

    public static final String PREFIX = "ypbin.xxx";

    /** 是否启用，默认开启 */
    private boolean enabled = true;

    /** 超时时间，默认 5s */
    private Duration timeout = Duration.ofSeconds(5);

    // 标准 getter/setter
}
```

**AutoConfiguration**（参照 `CacheAutoConfiguration`）：
```java
@AutoConfiguration
@ConditionalOnClass(SomeThirdPartyType.class)   // classpath 有对应库才装配（铁律2）
@ConditionalOnProperty(prefix = "ypbin.xxx", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(XxxProperties.class)
public class XxxAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(XxxAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean            // 宿主可覆盖（铁律2）
    public XxxService xxxService(XxxProperties props) {
        log.debug("[ypbin-starter] xxxService configured.");
        return new DefaultXxxService(props);
    }
}
```

**登记 imports**（`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`）：
```
cn.ypbin.starter.xxx.autoconfigure.XxxAutoConfiguration
```

**静态门面**（可选，非 Spring 场景才需要，参照 `CacheUtils`）：
```java
public final class XxxUtils {

    private XxxUtils() {}

    private static volatile XxxService xxxService;

    private static XxxService getService() {
        if (xxxService == null) {
            synchronized (XxxUtils.class) {
                if (xxxService == null) {
                    xxxService = SpringUtils.getBean(XxxService.class);
                }
            }
        }
        return xxxService;
    }

    /** 薄壳转调，业务逻辑在 XxxService 里（铁律4） */
    public static String doSomething(String arg) {
        return getService().doSomething(arg);
    }
}
```
门面类 Javadoc 要提示：Spring 组件优先直接注入 `XxxService`，静态门面仅供非托管场景。

所有类补铁律8的 license 头 + `@author wenbin` + `@since <当天日期>`。

## 审计模式：合规检查清单

审查 starter Java / 装配资源时，逐条核对并输出「文件:行 → 违反第几条铁律 → 整改建议」。空表示通过。

| # | 检查点 | 快速定位 |
|---|--------|----------|
| R1 | 新增 AutoConfiguration 没登记到 `.imports`；或用了废弃的 `spring.factories` | 对比 `autoconfigure/*AutoConfiguration.java` 与 `META-INF/spring/...AutoConfiguration.imports`；搜 `spring.factories` |
| R2 | `@Bean` 缺 `@ConditionalOnMissingBean`（宿主无法覆盖）；依赖第三方类缺 `@ConditionalOnClass`；开关缺 `@ConditionalOnProperty` | 看每个 `@Bean` 方法与配置类头部注解 |
| R3 | 配置项非 `ypbin.*` 前缀；用散落 `@Value` 当模块配置入口；Properties 缺 `PREFIX` 常量或默认值 | 搜 `@ConfigurationProperties` / `@Value`，看 prefix 是否 `ypbin.` |
| R4 | 静态门面写了业务实现（非薄壳）；缺 volatile 双检；Javadoc 未提示优先注入 Service | 看 `util/*Utils.java` 的 `getService()` 与方法体 |
| R5 | L1 模块 pom 引入了 `spring-cloud-*`；或跨层反向依赖 | 看 L1 模块 `pom.xml` 的 `<dependency>` |
| R6 | 内联全限定类名 | 搜正文里的 `cn.ypbin.` / `org.springframework.` / `com.` 包路径前缀（import/package 行除外） |
| R7 | 静默降级：空 catch、失败静默 return、吞异常回退默认值（区分条件装配，那不算） | 搜 `catch` 空块；确认容错处有日志 |
| R8 | 类缺 `@author wenbin`/`@since`，或误用 `@date`/写版本号；license 头缺失或用错 admin 的年份文案 | 看每个类的顶部 license 块与类级 Javadoc |
| R9 | 用了 `ResponseEntity`/自定义 4xx/5xx，没走 `R<T>` | 搜 `ResponseEntity` / `HttpStatus.` |
| R10 | 代码/注释/文档出现 `blade`/`continew` 等参考项目品牌词 | 搜 `blade` / `continew`（含 README、注释） |
| R11 | 可选依赖类型裸露在 `@Bean` 方法签名（参数/返回类型），仅靠方法级 `@ConditionalOnClass` 兜底 → 无该依赖时内省崩（PITFALL 4） | 对 pom 里 `<optional>true</optional>` 的库，搜其类型是否直接出现在 `@Bean` 签名；确认所在类有**类级** `@ConditionalOnClass` 或改用 `ObjectProvider<T>` 包装 |

审计命令示例（用 Grep 工具，内置 ripgrep，跨平台）：

```bash
# R6：正文内联 FQN（排除 import/package 行后人工确认）
rg -n "\b(cn\.ypbin|org\.springframework|com\.baomidou)\.[a-z]" --glob '*.java'
# R2：@Bean 是否都带 @ConditionalOnMissingBean（人工核对上下文）
rg -n "@Bean" --glob '*AutoConfiguration.java' -A 2
# R9：违规状态码
rg -n "ResponseEntity|HttpStatus\." --glob '*.java'
# R10：品牌关键字
rg -ni "blade|continew" --glob '*.java' --glob '*.md'
```

> **本机 Git-bash 踩坑**：`grep -P`（PCRE，含中文字符类）在 Windows Git-bash 报 `-P supports only unibyte and UTF-8 locales`，跑不了。查中文/复杂模式一律用 **Grep 工具**（内置 ripgrep），不要用带 `-P` 的 shell grep。

## 常见踩坑（PITFALLS — 框架库特有，均来自真实修复，动手前先自检）

这些坑在本项目 git 历史里反复出现，改装配/跨模块桥接时优先对照：

1. **`@ConditionalOnBean` 对自动配置顺序敏感 → 端点/Bean 静默不生成**。A 配置以 `@ConditionalOnBean(BFromOther)` 为条件，但 A 先于「注册 B 的配置」评估时，B 还没进容器 → 条件不满足 → A 的 Bean 悄悄不生成（现象如 `No mapping` / 功能失效，无报错，最难查）。
   - **修复**：在「提供依赖方」的配置上加 `@AutoConfigureBefore(消费方配置.class)`（或消费方加 `@AutoConfigureAfter`），保证依赖先注册。参照 `SecuritySseAutoConfiguration` 加 `@AutoConfigureBefore(SseAutoConfiguration.class)`、`SecurityLogClientAutoConfiguration` 同款。
   - `@ConditionalOnMissingBean`（铁律2）同样顺序敏感——它只看「当前已评估到的」Bean，务必让默认实现的配置排在业务覆盖之后评估的位置，否则覆盖失效。桥接/审计填充类的注册竞态在历史里修过多次（`SecurityAuditorAutoConfiguration` 等）。

2. **跨模块桥接：修复放在「依赖方」，不制造反向依赖**。security 要给 messaging/log 补 resolver 时，排序注解与桥接 Bean 放在 **security 侧**（它本就依赖对方），不要让 messaging/log 反过来依赖 security，破坏分层方向（铁律5）。选择在哪侧加 `@AutoConfigureBefore` 时，永远往「已有依赖的那个方向」放。

3. **无请求上下文的线程取当前用户会抛异常**。异步任务（`@Async`）、定时任务、启动初始化等线程没有 Sa-Token/请求上下文，直接调 `LoginHelper.getUserId()` 之类会抛异常。
   - **正确做法**：用显式的「安全取用」变体（no-context 时返回空而非抛），如审计字段填充在无上下文时安全跳过。参照 `LoginHelper`/`UserContext` 的 safe 访问 + `SafeAccessNoContextTest`。
   - 注意与铁律7的边界：这是**已知且预期**的无上下文场景走独立的显式 safe 方法，不是在正常路径里 `catch` 吞异常假装成功。别把普通调用也无脑套 try-catch 兜底。

4. **可选依赖类型出现在 `@Bean` 方法签名 → 无该依赖时启动崩（`NoClassDefFoundError`）**。这是**地基级**坑：`@Bean` 方法的参数/返回类型直接写可选依赖类型（如 `StringRedisTemplate`），只靠**方法级** `@ConditionalOnClass(StringRedisTemplate.class)` 兜底 **无效**——`@ConditionalOnClass` 只阻止 Bean 注册，阻止不了 Spring 对配置类做**方法内省**（处理配置类时用 `getDeclaredMethods` 加载所有 `@Bean` 方法的参/返回类型）。classpath 缺该库时内省阶段就抛 `NoClassDefFoundError`，条件生效太晚，整个应用起不来。任何轻量消费端（只引 web、无 Redis/无 MySQL）传递引入该模块即中招。
   - **判据**：可选依赖（pom 里 `<optional>true</optional>`）的类型，是否**直接**出现在某 `@Bean` 方法签名里，而该方法所在类**没有类级** `@ConditionalOnClass` 守住这个类型。
   - **两种正确写法**：
     1. **类级隔离**：把所有引用该可选类型的 `@Bean` 收拢进一个嵌套 `@Configuration`，类上打 **类级** `@ConditionalOnClass(可选类型.class)`——类级条件在内省该类方法**之前**评估，缺依赖时整类跳过、外层不碰该类型。用 `@Import(嵌套类.class)` 引入（**被 import 的配置先于外层自身 `@Bean` 注册**，故 Redis 实现能先注册、内存兜底的 `@ConditionalOnMissingBean` 正确退让——**不要依赖嵌套类声明顺序，那不可靠**）。参照 `ToolsAutoConfiguration.RedisStoreConfiguration`。
     2. **`ObjectProvider<T>` 包装**：`@Bean` 参数写 `ObjectProvider<StringRedisTemplate>` 而非裸 `StringRedisTemplate`——原始参数类型是 `ObjectProvider`，可选类型只是被擦除的泛型参，内省不加载它。适合"存在则用、否则兜底"的单 Bean 选择。参照 `SecurityAutoConfiguration#passwordAttemptStore`、`SseAutoConfiguration#sseTicketStore`。
   - **若整个配置类本就该"有该依赖才生效"**（如多级缓存 = L1 Caffeine + L2 Redis，缺 Redis 无意义），直接把该类型并入**类级** `@ConditionalOnClass({A.class, B.class})` 即可，最简洁。参照 `MultiLevelCacheAutoConfiguration` 同时守 `Caffeine` + `StringRedisTemplate`。
   - **验证必须覆盖两种场景**：用 `ApplicationContextRunner` + `FilteredClassLoader(可选类型.class)` 复现"无依赖"（断言 `hasNotFailed()` + 装配兜底/不装配），再提供 mock Bean 验"有依赖"（断言装配的是分布式实现而非兜底）。参照 `ToolsAutoConfigurationTest`。

5. **发布 Maven Central：parent-less POM 要自带元数据 + GPG 签名**。`ypbin-starter-bom`（无 parent）必须自带 `url/licenses/scm/developers/organization`；`ypbin-starter-dependencies` 作为子模块 parent 也要补这些字段（子模块靠继承拿到，缺了会被 Central 拒收）。根聚合 pom 与 bom 的 release profile 各自要挂 `maven-gpg-plugin`，否则这两个 parent-less 的 `.pom` 上传后缺 `.asc` 签名，校验失败。参照 commit `e0cffe5`（1.0.0 据此成功发布）。

6. **JDK 17 项目别用 JDK 19+ 的 API**。本项目编译目标 JDK 17，但本机 `.jdks` 下还有 21、IDEA 自带 JBR 25，容易顺手写超前 API 导致「IDEA 里不飘红、命令行 17 编译不过」。已踩：`Locale.of(String, String)` 是 JDK 19+ 才有，17 下 `找不到符号`，改用 `new Locale("zh", "CN")`。其它常见 17 缺失：`Stream.toList()` 有（16+）可用，但 `Math.clamp`、`String` 的部分方法、虚拟线程等是 21+ 的，勿用。写完务必用 **JDK 17** 命令行编译一遍验证（见「验证」段），不能只靠 IDEA。

7. **spotless 强制格式，不达标直接 build fail**。项目挂了 `spotless-maven-plugin` 的 `check`（google-java-format 系），格式不符时 `mvn test` 在编译前就红（报 `format violations`）。硬性两点：**static import 必须置于所有普通 import 之前**、**未使用的 import 必须删除**（写测试常见：import 了接口但只用 lambda，就成了未用 import）。
   - **修复**：提交/验证前先跑 `mvn -pl <模块> spotless:apply` 自动格式化，再 `test`。别手动对齐格式，交给 apply。

8. **AspectJ 复合切入点 `|| @within(注解)` 可能抛异常致切面静默失效**。`@Around("@within(A) || @within(B)")` 这类**复合**切入点，AspectJ 对部分注解（实测 Spring 的 `@Controller`）匹配时抛 `IllegalArgumentException: Type referred to is not an annotation type`，整个切入点匹配失败 → 目标类不被 AOP 代理 → 切面静默不生效（无报错、日志空，极难查）。`@RestController` 自带 `@Controller` 元注解，无需并列两个 `@within`；需覆盖多注解时先确认每个分支独立可匹配，或用 `execution` 类切入点。
   - **验证切面必须用真实 Spring Boot**（`@SpringBootTest` + MockMvc 完整启动）：`ApplicationContextRunner` 无法复现 AOP 代理时序——即便 `ReflectiveAspectJAdvisorFactory` 能解析出 advisor、pointcut 手动匹配为 true，容器里 `findCandidateAdvisors()` 也有 advisor 但 **controller 不被代理**（AspectJ 匹配时抛异常）。真实集成测试能立刻暴露。参照 `AccessLogAspect`（commit `f734ac2`）、`AccessLogAspectRealIntegrationTest`。

## 验证

本机命令行 PATH 无 java/mvn，但可直接调用 IDEA 内置 Maven + `.jdks` 下的独立 JDK 跑构建（memory `build-env`，已验证可用）。starter 是发布库，改动后：

1. 命令行编译/跑测试（git-bash，**务必用 JDK 17**，别用 JBR 的 25 或 .jdks 里的 21）：
   ```bash
   export JAVA_HOME="/c/Users/Administrator/.jdks/azul-17.0.18"
   MVN="/c/Program Files/JetBrains/IntelliJ IDEA 2026.1/plugins/maven/lib/maven3/bin/mvn.cmd"
   cd /c/Users/Administrator/IdeaProjects/starter/ypbin-starter
   "$MVN" -pl <模块>[,<模块>...] -am spotless:apply   # 先修格式（PITFALL 6）
   "$MVN" -pl <模块>[,<模块>...] -am test              # 再编译+测试
   ```
   `-am`（also-make）会带上依赖模块一起编译，改了被依赖的 L1 模块时必须加。
2. 涉及装配逻辑的改动，补/跑对应模块的 `ApplicationContextRunner` 测试，验证条件生效与可覆盖性。
3. 新增能力模块记得同步 `.imports` 登记、父 pom `<module>` 声明、依赖进 dependencies/bom。
4. 测试模块若报找不到 JUnit/AssertJ，检查该模块 pom 是否缺 `spring-boot-starter-test`（test scope）——它一并带来 JUnit5 + AssertJ + `MockHttpServletRequest`。

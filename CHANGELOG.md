# 更新日志

本项目遵循[语义化版本](https://semver.org/lang/zh-CN/)：`主版本.次版本.修订号`。
- 主版本：不兼容的 API 变更
- 次版本：向后兼容的功能新增
- 修订号：向后兼容的问题修复

格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)。

## [未发布]

### 新增

- **埋点模块 `ypbin-starter-tracking`（契约与骨架）**：面向「用户怎么用、卡在哪一步、哪个页面慢」的行为事件采集内核，
  与审计日志（`log`）、监控指标是三套不同语义的能力——埋点**允许丢弃**、主体**可匿名**、用途是理解用户而非追责或报警。
  - **事件目录是唯一事实源**（`docs/tracking-events.json`）：由 `tools/export-tracking-events.mjs` 生成 Java 常量
    `TrackingEventCodes` 与运行时资源 `META-INF/ypbin/tracking-events.json`；CI 与 `tools/preflight.sh`
    均增设「埋点事件目录未漂移」门禁（事实源改了而生成物未更新即失败）。
  - **默认关闭**：`ypbin.tracking.enabled` 与 `ypbin.tracking.ingest-enabled` 默认均为 `false`
    ——采集端点是一个匿名可写入口，不作为默认值；微服务下多服务共用本模块时也不会把端点散布到每个服务。
  - **不静默降级**：宿主未覆盖 `TrackEventSink` 时装配期打印一次 WARN 并仅打印到应用日志，
    而不是让「配了埋点却没有数据」悄悄发生。
  - 本版本仅交付契约与骨架（事件目录、生成器、`TrackEvent` / `TrackEventSink`、装配与全部配置项）；
    采集端点、有界队列与批量落库链路随后续版本提供。

## [3.1.0] - 2026-09-14

### ⚠️ 破坏性变更

- **`SpringUtils` 上下文未就绪时的失败方式变更**（core）：`getEventPublisher()` 原先在应用上下文尚未就绪时
  **返回 `null`**（调用方需自查），`getBean(...)`/`getEnvironment()` 则直接抛无信息的 `NullPointerException`；
  现统一改为 `requireApplicationContext()` 断言——仍然失败（不放行、不返回 null），但抛
  `IllegalStateException` 并说明原因与替代做法（改用构造器注入）。
  - **迁移**：若此前依赖 `getEventPublisher() == null` 判空，请改为在容器就绪后调用，或直接注入
    `ApplicationEventPublisher`。
- **`R` 的可空契约显式化**（core）：`R#getData()`/`R#getMessage()` 标注 `@Nullable`（`R.ok()` 等工厂本就传
  `null`，无参构造也允许为空）。对启用 JSpecify 静态检查的宿主是编译期契约变化，运行期无影响。
- **`TreeUtils`/`RequestIdUtils` 参数容忍 `null` 的语义显式化**（core）：`build`/`buildAndFilter`/`flatten`/`findNode`
  的列表参数与 `RequestIdUtils#sanitize` 的候选值标注 `@Nullable`（实现本就按空/null 处理），使注解与实现一致。

### 新增

- **性能基线模块 `ypbin-starter-benchmarks`**（不发布）：JMH 微基准覆盖三类热路径——树组装
  （`TreeUtils.build`）、链路 ID 校验与生成（`RequestIdUtils`）、缓存值序列化
  （`RedisJsonSerializerFactory` 写路径含不可变集合规范化、读路径多态还原）。
  刻意**不做 CI 挂钟门禁**（共享 runner 必然抖动、只会带来假失败）：基准只量化，可精确断言的复杂度与
  正确性由单元测试兜底。树组装基准同时测「父在前」「父在后」两种形态——**只测一种形态会掩盖退化**。
  配套：分层规则把 `cn.ypbin.starter.benchmarks..` 列入「可横跨各层」的开发工具。
- **空值语义静态检查（NullAway，试点 core 模块）**：新增可选 `nullaway` profile，
  `mvn -Pnullaway -pl ypbin-starter-core compile` 把「未标注即非空」（`@NullMarked`）变成编译期错误；
  `ypbin-starter-core` 根包加 `package-info.java` 标注 `@NullMarked`，CI 增加对应门禁步骤。
  试点上线即报出 15 处「代码确实可空但未标注」，其中 **4 处为真实潜在 NPE**。
  逐模块推广步骤与工具链坑（必须 `fork`、必须 `--should-stop=ifError=FLOW`、`-Xplugin` 需单行、
  `annotationProcessorPaths` 是覆盖而非追加、本工具链下 `JSpecifyMode` 不可用）见站点「项目脚手架 → 空值语义」。
  说明：**只加注解不加校验是危险的**——注解一旦与实际可空性不符就是「注解说谎」，比不标注更容易误导
  IDE 与静态分析，故注解与检查器必须同时落地。

### 变更

- **依赖与工具链升级（含两处大版本迁移）**：
  - **Testcontainers 1.20.4 → 2.0.5**：2.0 是有 API 变更的大版本，迁移点为三处——
    模块改名（`org.testcontainers:mysql` → `testcontainers-mysql`、
    `junit-jupiter` → `testcontainers-junit-jupiter`）、包迁移
    （`org.testcontainers.containers.MySQLContainer` → `org.testcontainers.mysql.MySQLContainer`）、
    以及 `MySQLContainer` **不再是泛型**（`MySQLContainer<?> x = new MySQLContainer<>(...)` 需去掉类型参数）。
    已在真机容器下跑通全部集成测试（MySQL/Redis/Nacos/Sentinel 均由 2.0 正常拉起）。
  - **NullAway 0.11.3 → 0.14.1**：新版收紧两类检查，全仓仅多出 4 处并已修复——
    ① **数组类型的 JSpecify 注解位置**（`@Nullable byte[]` 注解的是**元素类型**，数组本身可空须写
    `byte @Nullable []`，方法返回同理 `String @Nullable []`）；② 可空实参传入非空形参
    （`cancel(registry.remove(jobId))`，方法体本就判空，形参改为可空即可）。
  - **ArchUnit 1.4.1 → 1.5.0**（测试期依赖，35 项架构约束测试全绿）。
  - 其余：Bouncy Castle、commons-io 2.22.0、maven-shade、maven-enforcer、
    `actions/setup-node` v7、`github/codeql-action` v4、`actions/setup-java` v5。
  - ⚠️ **`error_prone_core` 暂不升级**：2.50.0 在本项目的编译参数下**插件初始化即抛异常**
    （`ErrorProneJavacPlugin.init` → `BaseErrorProneJavaCompiler.checkAddTypeAnnotationsToSymbol`），
    此时日志中的「0 违规」只是分析未运行的假象，需单独调研新版 Error Prone 的调用方式后再升级。
- **Dependabot 分组补强**（防「同族依赖版本错配」）：新增 `analysis` 组（Error Prone + NullAway + JSpecify
  必须同批升级，二者存在版本耦合）；前端仓库补齐运行时生态分组（`@tiptap/*`、Vue 运行时与编译器）——
  此前这类包不在任何分组内会各开一个 PR，曾出现「只升 `@tiptap/starter-kit` 而 core 停留在旧版本，
  导致类型增强失效」的问题。
- **发布前置门禁脚本 `tools/preflight.sh`**：`-Prelease` 会让承载非发布模块的 `dev-only` profile 失效，
  架构约束测试因此不进入发布反应堆（这是为了让未签名产物不混进 Central 上传包），副作用是
  **发布构建本身不再跑铁律门禁**。新增脚本一次跑全**五道**门禁（全量构建含 35 项架构测试、
  NullAway 空值语义、依赖版本收敛、集成测试、配置元数据漂移），且 Docker 不可用时**显式失败**而非静默跳过 IT，`RELEASING.md` 第 3 步已改为调用它。
- **测试基座新增 Nacos 容器支持**（`ypbin-starter-test`）：`ContainerSupport.nacosServerAddress()`
  统一「外部地址优先 → 容器回退 → 条件跳过」，新增 `@EnabledIfNacosAvailable`。
  容器模式细节全部收敛进基座：与部署对齐的镜像版本、**8848/9848 必须绑定到相隔 1000 的连续宿主端口**
  （客户端固定按「服务端口 + 1000」连 gRPC，随机端口会导致 `Client not connected, current status:STARTING`）、
  Nacos 3 镜像强制的鉴权三件套、以及用「监听端口」而非 Nacos 3 已移除的 v2 健康路径做就绪探测。
- **跨服务 Feign 调用在 CI 真正被覆盖**（cloud-core）：`FeignCrossServiceIT` 由「必须显式提供
  `-Dypbin.it.nacos-addr` 否则跳过」改为容器化（`@EnabledIfNacosAvailable` + `@DynamicPropertySource`），
  本机与 CI 均真跑；顺带清理其内联全限定名。
- **NacosDiscoveryIT 去重**（cloud-nacos）：删除内联的容器/端口/鉴权逻辑，改用共享基座。
- **Controller 极薄落地**（extension-crud）：`CrudController` 的 4 个私有反射方法下沉至新增的
  `crud.support.CrudReflectSupport`，主类 364 → 289 行且不再含私有方法。
- **架构门禁 20 → 35 项**（`ypbin-starter-architecture-tests`）：
  - 新增 5 条：`@Transactional` 只能标注在 public 成员；每个 `@ConfigurationProperties` 前缀必须有配置元数据；
    禁裸 `java.util.Date`；Controller 单文件 ≤400 行且不得含私有方法；实体 `equals/hashCode` 必须且仅基于主键 id；
    集合返回类型的方法不得返回 `null`；
  - 新增 3 项一致性/健壮性门禁：模块发布清单一致性（见修复）、源码剥离后大括号必须平衡、文本块剥离正确性。
- **集合返回 null 消除**（cloud-gateway）：`NacosRouteInitializer#parseRoutes` 原以 `null` 表达
  「配置解析失败 → 保留现有路由」，与「集合不得返回 null」冲突；改用 `Optional<List<RouteDefinition>>`
  表达同一语义（调用方仍为「空即保留现有路由」，防止误清全量路由的安全性语义不变）。
- **恢复「发布后推进开发版本」的版本卫生**（构建配置 / 工具链）：按 `RELEASING.md` 第 6 步，正式版发布后应把根 pom
  的 `<revision>` 推进到下一迭代快照。3.0.0 发布时漏了这一步，master 便在**已发布坐标**下继续累积行为改动——
  本机 `~/.m2` 的 `3.0.0` 与中央仓库的 `3.0.0` 同名不同内容，宿主（ypbin-admin）在自己机器上编译时解析到的
  是「从未发布」的代码。现 `<revision>` 为 `3.1.0-SNAPSHOT`；同时把**项目生成器与 `revision` 解耦**：
  写入生成项目的是 CHANGELOG 里的「最新已发布版本」（解析不到才回退 `revision` 并告警），保证生成的骨架
  依赖已发布坐标。本地旧的 `3.0.0` 产物已删除并改从中央仓库解析（已核验重新解析到的 `3.0.0` 与 tag 内容一致）。
  `RELEASING.md` 第 6 步补上了「这一步不能省」的原因说明。

### 修复

- **空值语义静态检查（NullAway）覆盖全部 33 个含主源码模块**：本轮补齐最后 3 个模块
  `storage`(31) / `log`(40) / `security`(53)，至此所有模块均在 CI 门禁下做空值检查，
  累计修复 300+ 处。三类典型修法：框架/构建器填充的字段（配置绑定、模型 setter、Jackson 反射实例化）
  按类标注 `@SuppressWarnings("NullAway.Init")` 并写明原因；真实可空契约补 `@Nullable`
  （接口/实现/getter 同步，避免只改一处）；懒初始化静态持有器统一改「可空字段 + 局部变量双重检查」。
  另有若干**契约修正**：`JobDefinition` 触发方式缺失时显式抛错、`LicenseCheckAspect` 拿不到授权内容
  时显式失败（不静默跳过联机回验）、`SensitiveType` 策略缺失时显式抛错、
  联机校验签名字段缺失时回落空串上报（服务端验签必然失败，fail-closed）。
  踩坑记录：Java 类型注解不能写在限定类型前——`@Nullable A.B` 会被判为注解外侧限定符而编译报错，
  须写成 `A.@Nullable B`。

### 修复

- **日志注入（log injection）加固**：新增 `cn.ypbin.starter.core.util.LogSanitizer`——把用户可控值
  （URL、请求头、查询参数、文件名、AccessKey 等）写入日志前统一替换换行/制表/控制字符并限制长度，
  避免攻击者用换行在日志里**伪造日志行**（污染审计与告警，例如伪造一条「登录成功」）。
  已应用到 16 处上报点（`GlobalExceptionHandler`、`AccessLogAspect`、`IdentityHeaderFilter`、
  `RepeatableReadRequestWrapper`、`SignChecker`），并附单元测试覆盖 CRLF/控制字符/null/超长截断。
  admin 侧因仍依赖已发布的 starter 3.0.0（不含本类），在 `ypbin-common` 放置同语义实现并注明
  「升级到 starter 3.1.0 后删除本类、改用 starter 版本」，避免两处实现漂移。
- **空值语义静态检查再推广 16 个模块**（累计 **21 个模块**）：api-crypto / api-doc / async / captcha /
  cloud-loadbalancer / cloud-nacos / cloud-observability / cloud-sentinel / excel / extension-crud /
  extension-datapermission / i18n / sensitive-words / social / test / xxljob。本轮修出 **59 处**
  （累计 113 处）。推广已工具化：`node tools/rollout-nullaway.mjs <模块…> | --all-pending`
  自动完成「推导包根 → 生成 `@NullMarked` package-info → 声明 `nullaway.packages` 与 jspecify 依赖」
  （幂等），CI 按属性自动发现参与模块，**新增模块无需改 CI**。
  其中值得单列的三处**契约修正**（不只是补注解）：
  - `R.ok(T data)` → `R.ok(@Nullable T data)`：同类工厂 `R.ok()` 本就直接把 `null` 传给构造器，
    参数声明为非空与实现不符；
  - `BaseService#page(PageQuery, Wrapper)` 与 `SocialRequestRegistry#remove`：null 分别是
    「无条件查询」与「此前未注册」的既有语义，接口同步标注 `@Nullable`；
  - `CrudController#toEntity`：原「入参为 null 就静默返回 null」改为**显式抛错**（入参由
    `@RequestBody` 保证非空，为空即编程错误）——静默返回 null 正是铁律禁止的静默降级；
    `toResp` 保留「查无记录透传 null」的语义并显式标注。
  另有三类框架语义按类标注 `@SuppressWarnings("NullAway.Init")`（`@ConfigurationProperties`
  构造后绑定字段），以及 Spring Cloud `ServiceInstance#getMetadata` 可空 → 收敛为「空即空表」。
- **依赖版本收敛缺陷（2 处真实分叉，由新增的 enforcer `dependencyConvergence` 门禁发现）**：
  Maven 对「同深度、不同版本」的传递依赖按**声明顺序**择一，既不确定也无提示，宿主可能拿到与库
  构建时不同的版本。实测发现并修复：
  - `commons-io`：POI 5.5.1 / poi-ooxml 5.5.1 要 **2.21.0**，而 commons-csv 1.14.1 与
    commons-compress 1.28.0 要 **2.20.0** → 统一钉到 2.21.0（POI 构建时所用版本）；
  - victools `jsonschema-generator`/`-module-jackson`/`-module-swagger-2`：Spring AI 2.0
    （spring-ai-model）要 **5.0.0**，而 spring-ai-openai → openai-java-core 4.39.1 要 **4.38.0**
    → 统一钉到 5.0.0（Spring AI 自身期望的版本）。
  两处均钉在 `ypbin-starter-dependencies` 的 `dependencyManagement`，并新增可执行门禁
  `mvn -Pdep-convergence validate`（CI 与 `tools/preflight.sh` 均已接入）。
  变异验证：撤掉任一钉版本 → 门禁立即红并打印冲突路径；还原 → 全 40 模块通过。
- **CI 的 NullAway 门禁此前是「假绿」（空转）**：Error Prone 只在 javac 真正执行时生效，而 CI 在该步骤
  之前已把全部类编译好（`clean test` → `install -DskipTests`），于是 `compile` 直接输出
  「Nothing to compile - all classes are up to date」并成功返回——检查一次都没跑。已改为 `clean compile`
  强制重编译，并加**空转自检**：输出里一旦出现「Nothing to compile」就以失败退出，避免今后有人误删
  `clean` 又悄悄变回假绿。已用变异验证：注入一个必然违规的探针 → 门禁红并打印 NullAway 错误；
  撤掉探针 → 门禁绿且日志显示确实编译了 71 个源文件。`tools/preflight.sh` 同步修复。
- **空值语义静态检查推广到 4 个模块**（cache / data / web / cloud-core，共 39 处）：
  配置收敛为父 pom 的一个可复用 `nullaway` profile——参与模块只需在自己的 `<properties>` 里覆盖
  `nullaway.packages` 并放一个 `@NullMarked` 的 `package-info.java`，CI 步骤按此自动发现参与模块。
  修出的问题分三类：①**真实可空却标注非空**（`CacheService#get`/`getOrLoad` 未命中返回 null、
  `XssCleaner#clean`/`FieldEncryptor#encrypt` 的「null 进 null 出」、MyBatis 的 `getNullableResult`、
  Spring Data `RedisSerializer` 的可空契约等，统一补 `@Nullable` 让注解与实现一致）；
  ②**框架填充字段**（`BaseEntity` 的 `id`/审计字段、`DataProperties.Encrypt#key`）由 MyBatis-Plus 填充或
  Spring Boot 构造后绑定，不经构造器初始化，按字段/类标注 `@SuppressWarnings("NullAway.Init")` 并写明原因；
  ③**静态持有器**（`CacheUtils`/`RedisUtils`/`FieldEncryptorHolder`）改为「可空字段 + 局部变量双重检查」，
  既通过检查，又让原有的判空逻辑被静态校验。
- **`TreeUtils.build` 实际是 O(n²)**（core，由独立代码审查发现——此前用基准得出的「线性」结论是错的）：
  根判定 `nodes.stream().noneMatch(...)` 在逐节点循环里线性扫描父节点，最坏形态（父节点排在列表末尾）
  `getId()` 调用量达 ≈1.0·n²（n=4000 时约 1600 万次，已用调用计数独立复现）。改为预建 ID 集合一次，
  实测降到约 2n（n=16000 亦 <6n），Javadoc 的 O(n) 声明这才成立。
  **教训**：当时基准用 `rootId * 1_000_000 + child` 造节点 ID，子 ID 与根 ID 撞号导致扫描提前命中，
  把最坏形态掩盖成了线性——**用来自证复杂度的手段本身也会骗人**。现基准覆盖两种形态，并新增
  **确定性调用计数守卫**（`TreeUtilsTest#buildShouldBeLinearInNodeCount`，按调用次数而非挂钟断言）。
- **架构门禁自身的边界缺陷**（architecture-tests，同样由审查发现）：
  - 源码剥离器不支持文本块：内容含奇数个引号会把**文本块之后的代码整段吞掉**，使所有消费剥离文本的规则
    静默失明（现正确跳过文本块，并新增「剥离后大括号必须平衡」断言把静默失明变成显式失败）；
  - 「集合返回 null」规则：漏判「注解与签名同行」「参数含注解数组 `@Foo({1,2})`」「raw 集合类型」
    （均已修，`Optional<集合>` 的口径也已写进文案）；**已知边界**：方法体内嵌套类型（匿名类/局部类）里的
    `return null` 仍会被计入外层方法——源码扫描不区分嵌套类型归属，已在规则文案中显式声明；
  - Controller 私有方法规则同样漏判「注解与签名同行」（已修）；
  - 实体等值规则把合法的 `@EqualsAndHashCode(of = "id")` 误判为违规（已放行）；
  - 禁裸 `Date` 规则被 `import java.util.*;` 绕过（已覆盖通配导入下的裸 `Date` 记号）；
  - 以上边界均已加入规则有效性自检样例。
- **非发布模块缺一致性门禁**（architecture-tests 新增 `ModulePublishingTest`）：此前只靠约定「非发布模块放进
  `dev-only` profile」，新增模块若写进顶层 `<modules>` 仍会静默混进 Central 上传包。现由测试强制：
  凡 `pom` 声明 `maven.deploy.skip`/`gpg.skip` 的模块，不得出现在顶层 `<modules>`，且必须由 `dev-only`
  profile 承载（已用变异验证：把模块挪回顶层即构建失败）。
- **发布包混入非发布模块**（构建配置）：`ypbin-starter-architecture-tests` 仅设 `maven.deploy.skip=true`，
  但 Central 发布插件以 `extensions=true` 接管 deploy 生命周期、不读取该属性，导致其空 jar 被一并打进上传包；
  而该模块 `gpg.skip=true`（无 `.asc` 签名）且无 sources/javadoc，会让整个 deployment 校验失败。
  修复：release profile 的 `excludeArtifacts` 修正为**只用 artifactId**（插件实现是
  `excludeArtifacts.contains(artifact.getArtifactId())`），并把非发布模块统一改由根 pom 的
  `activeByDefault` profile（`dev-only`）承载——`-Prelease` 会使其失效，模块不进入发布反应堆
  （`-Psbom` 显式带回以保证 SBOM 完整；注意 `-Prelease`/`-Prelease,it` 因此不会执行架构门禁，
  发布前的铁律检查由 CI 与 tag 流程保证）。
- **CRUD 反射辅助跨包访问失效**（extension-crud）：4 个私有反射方法下沉到 `crud.support.CrudReflectSupport` 后，
  与原实现同包时可访问的**包级私有** DTO/实体变为 `IllegalAccessException`；已在 `instantiate` 与 `writeId`
  中对不可访问的构造器/setter 调用 `setAccessible(true)`，行为既恢复兼容又比原来更宽。
- **上下文未就绪时的无信息 NPE**（core，由 NullAway 发现）：`SpringUtils` 的 `getBean(Class)`、
  `getBean(String, Class)`、`getEventPublisher()`、`getEnvironment()` 原先直接解引用尚未赋值的
  `applicationContext`；改为统一的 `requireApplicationContext()` 断言（见上方破坏性变更）。
- **空值契约显式化**（core）：`SpringUtils#getApplicationContext`/`getProperty`、`TreeUtils#findNode`
  （未找到为 null）、`RequestIdUtils#sanitize`（不合法为 null）、`R#message`/`R#data`（允许为空）等
  原本只在 Javadoc 写明的可空语义，补上 `@Nullable` 变成机器可校验的契约。

## [3.0.0] - 2026-09-13

**重大版本：Jackson 3 全面对齐、Spring Boot 4.1 / Spring Framework 7 新特性落地、脚手架工程化能力，
以及租户 fail-closed 等安全加固。升级前请先读下方「破坏性变更与迁移」，并参考站点
`guide/starter/migration-2x-to-3x`。**

### ⚠️ 破坏性变更与迁移

- **租户隔离改为 fail-closed**（extension-tenant）：新增 `ypbin.tenant.fail-on-missing-tenant`，**默认 `true`**。既无显式绑定也无 `TenantProvider` 返回值时，查询不再静默查空（旧实现返回 `NullValue` 会拼出 `tenant_id = NULL`，查询恒空、写入 NULL 租户后无法再查出），而是抛业务异常（业务码 409）并提示改用 `@TenantIgnore`。
  - **迁移**：登录、匿名分享、定时任务等本无租户上下文的路径需显式 `@TenantIgnore` 或 `TenantContext.executeIgnore`；出现「缺少租户上下文」说明该路径漏声明，应补声明而非把开关关掉。确实需要「无租户即跨全租户查询」的场景可显式置为 `false`（不推荐）。
- **网关鉴权放行范围收窄**（cloud-gateway）：默认 `exclude-paths` 不再包含 `/actuator/**`，改为仅 `/actuator/health`、`/actuator/health/**`、`/actuator/info` 与 API 文档路径。
  - **迁移**：依赖经网关访问其它 actuator 端点的宿主需显式声明（建议仅放行 health/info）。
- **Feign fallback 不再回传底层异常文案**（cloud-core）：`RFeignFallbackFactory` 改为返回稳定文案（默认「远程服务暂不可用，请稍后重试」）或调用方显式传入的文案，避免泄露目标主机/端口/类名等内部细节；完整堆栈仅落服务端日志。
  - **迁移**：需要特定提示的调用方改用 `fail(cause, "自定义文案")`。
- **Sa-Token 会话序列化切至 Jackson 3**（dependencies）：`sa-token-redis-jackson`（绑定 Jackson 2）替换为 `sa-token-redis-template` + `sa-token-jackson3`。auth 与 gateway 必须同时升级；Redis 存量会话可能无法反序列化，升级后用户需重新登录。

### 变更
- **Jackson 2 运行时全面移除**（json/cache/cloud-core/cloud-sentinel/log/license）：缓存值序列化改用 Spring Data Redis 4 的 `GenericJacksonJsonRedisSerializer`（Jackson 3），Feign 错误解码、限流拒绝响应统一改用 `tools.jackson`；`ypbin-starter-json` 移除 Jackson 2 定制器与 `spring-boot-jackson2`/`jackson-databind:2`/`jackson-datatype-jsr310` 依赖，合并为单一 Jackson 3 `JsonMapperBuilderCustomizer`。
  - 说明：Jackson 3 官方**保留** `com.fasterxml.jackson.annotation` 注解包（依赖注释 *Annotations remain at Jackson 2.x group id*），`@JsonIgnore`/`@JsonInclude`/`@JacksonAnnotationsInside` 等 import 无需改动。注意 Jackson 3 的 `JacksonException` 继承 `RuntimeException`（Jackson 2 为受检 `IOException`），自定义序列化代码的 catch 子句需调整。
- **Feign 超时默认注入**（cloud-core）：无条件注入 `connect-timeout=5s`、`read-timeout=10s`（即使关闭熔断也生效），并把 resilience4j TimeLimiter 由 10s 调整为 15s，确保读取超时先于熔断取消触发、不再留下悬挂连接。
- **联机校验改为声明式外部 API 客户端**（license）：`HttpRemoteVerifyProvider` 由手写 JDK `HttpClient` 迁移为 `@HttpExchange` 接口 + `RestClient` + `HttpServiceProxyFactory`；签名参数、超时（连接/读取）与三桶裁决语义完全不变，原有 17 项用例全绿并新增 3 项请求参数断言（签名四件套齐全、空 `fingerprint` 必须省略）。**选型约定**：服务间调用用 Feign，外部第三方 API 用 `@HttpExchange`。
- **集合字面量统一**：20 处 `Collections.emptyXxx/singletonXxx` 改为 `List.of/Map.of/Set.of`；架构约束测试新增防回归规则（含正则有效性自检）。
- **废弃 API 全量清零**（全仓，`-Xlint:deprecation` 主源码零告警）：
  - **`EnvironmentPostProcessor` 包迁移**：Spring Boot 4.1 起 `org.springframework.boot.env.EnvironmentPostProcessor` 废弃待移除，迁移至 `org.springframework.boot.EnvironmentPostProcessor`（方法签名不变）。⚠️ **接口与 `META-INF/spring.factories` 注册键必须同时改**——Boot 4.1 仍能兼容旧键，只改接口不改键会「编译通过但默认值静默失效」。已加双重门禁：源码级注册校验（`SourceConventionTest`）+ 用 `SpringFactoriesLoader` 直接加载的运行时可见性测试（`RegistrationDiscoveryTest`）。
  - **空值注解迁移到 JSpecify**：Spring Framework 7 废弃 `org.springframework.lang.NonNull`/`Nullable`（包级已改用 `@NullMarked`），自研 API 的语义注解改用 `org.jspecify.annotations.*`（`jspecify` 版本由 `spring-boot-dependencies` 管理）；继承框架接口的冗余 `@NonNull` 直接删除。
  - **`ThreadLocalAccessor.reset()`**（context-propagation 1.1 废弃）改为覆写无参 `setValue()`——默认实现即委托 `reset()`，语义等价且不再触发废弃告警。
  - **`RestClient.Builder.messageConverters(Consumer<List>)`** 改为 `configureMessageConverters(Consumer<ClientBuilder>)`。注意**不能**顺手换成 `withJsonConverter()`：它用 `MediaType#equalsTypeAndSubtype` 校验，宽容媒体类型（`*/*`）会被直接判为非法参数，故仍需走列表配置。
  - **测试基座**：`LettuceConnectionFactory#setPassword(String)`（Spring Data Redis 4.1 废弃）改为经 `RedisStandaloneConfiguration` + `RedisPassword` 注入；JSON 相关测试统一改用 Jackson 3 的 `JacksonJsonHttpMessageConverter`；MD5 旧算法的兼容性覆盖用例显式 `@SuppressWarnings("deprecation")` 并注明意图。
  - **消费方示范**（admin）：MyBatis-Plus 3.5.17 起 `BaseMapper.selectBatchIds` 废弃（退化为委托 `selectByIds` 的 default 方法），3 处调用统一改为 `selectByIds`。
- **集成测试统一按容器模式验证**（全仓）：`mvn -Pit verify` 现可在一台有 Docker 的机器上真跑全部 IT（Redis/Nacos 容器 + Sentinel 真启动 Web），无需外部中间件；`FeignCrossServiceIT` 仍要求显式提供 `-Dypbin.it.nacos-addr`，未提供则跳过。

### 安全
- **身份头透传来源校验**（cloud-core）：新增 `ypbin.cloud.feign.trusted-source-token`（默认空=不校验，启动告警）与 `identity-headers`；启用后不可信来源的身份头不再二次透传，防止直连服务伪造身份后经 Feign 放大越权。
- **链路 ID 防日志注入**（core/gateway/observability）：新增 `RequestIdUtils`，对客户端 `X-Request-Id` 校验长度（≤128）与可见 ASCII，含 CRLF/控制字符/ANSI 转义或超长时丢弃并重新生成。
- **灰度版本白名单**（cloud-loadbalancer）：新增 `ypbin.cloud.loadbalancer.allowed-versions`，未在白名单内的请求头版本值一律忽略，防止把流量导向未加固实例。
- **License 快查时钟回拨检测**（license）：断言路径 5s 节流快查补上时钟回拨检测，回拨超容差即锁定不可用（原实现仅全量 `evaluate()` 检测）。

### 新增
- **项目生成器与四档预设**（`tools/ypbin-init.mjs`）：`api-only`（纯 REST API）/ `monolith`（单体：Web+数据+缓存+安全）/ `microservice`（契约 + 实现双模块）/ `worker`（非 Web 任务进程）。生成即含可运行启动类、示例接口/任务、单元测试、README、`.gitignore`，版本取自 starter 根 pom 与 Spring Boot 基线；四个预设均实测「生成后 `mvn test` 通过」。
- **供应链合规**：新增 `-Psbom` profile 生成 CycloneDX SBOM（聚合 483 组件，specVersion 1.6），CI 归档为构建产物；四仓统一加入 `.github/dependabot.yml`（Maven/npm + GitHub Actions，按 Spring 族与测试族分组）。
- **网关身份头签名显式化**：`ypbin.gateway.auth.trusted-source-token/trusted-source-header` 由网关在签发身份头时同时写出标记；cloud-core 新增 `ypbin.cloud.feign.require-trusted-source`（默认 false 仅告警；置 true 时未配置密钥直接拒绝启动），标记头已加入默认透传白名单以保证二次 RPC 不丢身份。admin 的 gateway/system/ai Nacos 配置与 `install.sh`（自动生成 `GATEWAY_SIGN_TOKEN`）已接入并默认启用。
- **测试基座 `ypbin-starter-test`**：提供 `ContainerSupport`（外部实例优先 → Docker 容器回退 → 条件跳过）、`RedisIntegrationTestSupport`、`MySqlIntegrationTestSupport`，以及 `@EnabledIfRedisAvailable` / `@EnabledIfMySqlAvailable` 条件注解。同一套集成测试在「本机已有中间件」「本机有 Docker」「两者都没有」三种环境下都能合理工作（跳过而非假失败）；宿主以 `test` 作用域引入。
- **集成测试统一门禁**：surefire 默认排除 `**/*IT.java`，新增全局 `-Pit` profile 由 failsafe 执行 IT；CI 增加独立的「集成测试（Testcontainers）」job（GitHub runner 自带 Docker，真实拉起 Redis/MySQL）。
- **架构约束测试模块 `ypbin-starter-architecture-tests`**（不发布）：用 ArchUnit 把编码铁律变为构建失败——分层依赖（L1 不得依赖 L2/L3、L2 不得依赖 L3）、`@Bean` 覆盖语义（白名单外必须有 `@ConditionalOnMissingBean`）、`@Transactional` 显式 rollbackFor、禁字段注入、禁 `printStackTrace`/`System.out`；字节码不可见的三条（禁内联全限定类名、Lombok `@Data` 边界、`@AutoConfiguration` 注册）改由源码扫描校验。含**规则有效性自检**（合成违规类反向验证），避免规则写错却永远通过。
- **方法级弹性能力**（core）：新增 `ypbin.resilience.enabled`（默认 true）启用 Spring Framework 7 内建的 `@Retryable` / `@ConcurrencyLimit`（`org.springframework.resilience.annotation`），**无需再引入 Spring Retry 或 Resilience4j** 即可对任意 Bean 方法声明重试与并发限制；注解驱动，未使用注解则零开销。
- **API 版本管理**（web）：新增 `ypbin.web.api-version.*`（默认关闭），支持 `@GetMapping(value = "/user", version = "1.0")` 与请求头/查询参数/路径段三种解析方式、缺省版本与支持版本清单。
- **`InetAddressFilter` SSRF 防护**：starter 侧无用户 URL 抓取路径（`DocumentLoader` 只处理字节流），存量手写地址校验由消费方（ypbin-admin AI 知识库导入）改用 Spring Boot 4.1 的 `InetAddressFilter.externalAddresses()`，覆盖含 CGNAT 在内的特殊用途网段。**注意不可对出站客户端全局套用**，否则会切断 `lb://` 内网服务调用。

### 性能
- **向量库落盘消除 O(N²)**（ai）：抽出 `PersistCoordinator` 统一落盘——并发合并（单飞 + 脏标记循环复查，保证最后一次变更必落盘）+ 原子替换（先写 `.tmp` 再 `ATOMIC_MOVE`，避免半个 JSON 导致下次启动加载失败）+ 可选防抖 `ypbin.ai.rag.persist-debounce-ms`（默认 0 写透，设为正值可把顺序 N 次变更合并为约 1 次；正常关闭强制 flush）。新增 `PersistCoordinatorTest`（并发不丢数据、防抖合并、原子替换无残留、失败清理）。

### 修复
- **缓存值序列化不可变集合失败**（cache）：JDK 不可变集合（`List.of()`/`Map.of()`/`Set.of()`/`Stream.toList()`）为 final 类型，`As.PROPERTY` 形态无法写入多态类型标识，序列化器会静默丢弃类型信息，读回时抛 `SerializationException`（缺少类型 id）；可变集合（`ArrayList`/`HashMap`）不受影响。新增 `ImmutableCollectionNormalizer` 在写入前把集合规范化为 `ArrayList`/`LinkedHashMap`/`LinkedHashSet`（语义等价）。本项目自身大量使用 `List.of()`/`toList()`，该问题会在读缓存时必现，属必修项。
  - 该缺陷由真实 Redis 往返集成测试发现（`RedisJacksonJsonRoundTripIT`，默认跳过、设 `YPBIN_TEST_REDIS_PASSWORD` 后运行）；同时锁定了 POJO、不可变 List/Set/Map、`toList()`、空集合与嵌套集合各形态。
- **AI 可选依赖装配**（ai）：`JdbcMemoryConfiguration` 补类级 `@ConditionalOnClass(JdbcChatMemoryRepository.class)`，避免开启 `type=jdbc` 但未引依赖时以 `NoClassDefFoundError` 崩溃；`simpleVectorStore` 的 `AiEmbeddingConfigResolver` 改 `ObjectProvider` 并在缺失时给出可操作错误。
- **CRUD 主键写入快速失败**（extension-crud）：`CrudController.setEntityId` 原先在找不到 `setId` 时静默跳过，可能导致无主键或以请求体中被篡改的主键执行 `updateById`；改为 fail-fast，并按参数类型兼容匹配（`setId(Long)` 也可接收 `Serializable` 主键）。
- **网关 Swagger 聚合阻塞上限**（cloud-gateway）：`block()` 加 10s 上限，路由表未就绪时不再无限阻塞启动。
- **业务异常解包**（web）：全局异常处理器沿 cause 链解包（上限 10 层），修复 MyBatis 等框架包装 `BusinessException` 后（如租户 fail-closed 异常）被降级为泛化「系统内部错误」500、丢失可操作提示的问题。
- **内联全限定类名**（async）：`AsyncUtils.schedule` 内联 `java.time.Instant` 改为顶部 import（由新增的源码规范测试发现，全仓仅此一处）。
- **模块依赖管理缺口**（dependencies）：`ypbin-starter-xxljob` 未登记在父 pom 的 dependencyManagement 中，导致内部模块引用时缺版本；已补齐。
- **License 指纹缓存**（license）：`MachineFingerprint.current()` 增加进程内缓存，避免每次 `@LicenseCheck(online=true)` 都枚举网卡与解析主机名。
- **集成测试门禁失效与两处 IT 陈旧**（cloud-nacos/cloud-sentinel）：
  - `cloud-nacos`、`cloud-sentinel` 残留模块级 `it` profile，把 `*IT.java` 交给 **surefire** 执行（还顺带重跑 `*Test.java`），绕过了根 pom 统一的「surefire 排除 IT / `-Pit` 交 failsafe」门禁。已删除模块级 profile 与重复的 compiler/surefire 覆盖，所需测试依赖改为常驻 `test` 作用域。
  - `NacosDiscoveryIT` 容器模式必然失败：镜像硬编码 `nacos-server:v2.4.3` 与客户端 3.1.1 大版本不符，就绪探测路径在 Nacos 3 已不存在，且 Nacos 客户端固定按「服务端口 + 1000」连 gRPC，而 Testcontainers 默认把 8848/9848 映射成互不相干的随机端口（表现为 `Client not connected, current status:STARTING`）。改为取 `ContainerSupport.NACOS_IMAGE`（与部署同为 v3.2.4）、按 `forListeningPorts` 等待、把 8848/9848 绑定到一对相隔 1000 的连续空闲宿主端口，并补齐 Nacos 3 镜像强制的鉴权三件套（`NACOS_AUTH_TOKEN`/`NACOS_AUTH_IDENTITY_KEY`/`VALUE`，随机生成、`NACOS_AUTH_ENABLE=false` 故无需口令）。
  - `SentinelFlowIT` 无法编译：`TestRestTemplate` 已随 Spring Boot 4 移除。改用 `@LocalServerPort` + `RestClient`，并关闭默认状态码错误映射，使被限流返回 200 或 429 都能取到响应体断言；同时修掉 `@org.springframework.context.annotation.Bean` 内联全限定名。

## [2.2.3] - 2026-09-09

**独立复审整改**（2.2.2 发布后由三仓交叉复审产出，详见提交记录）。

### 修复
- **可重复读超限响应契约化**（web）：请求体超过缓存上限（413 语义）在 Filter 层直接写回统一 `R` 结构（HTTP 200 + `code=413`）——此前抛异常发生在进入 MVC 层前、`@RestControllerAdvice` 捕获不到，宿主会退化成容器 500。
- **`max-body-bytes` 装配期校验**（web）：取值限定 `(0, 64MB]`，非法配置启动即失败，杜绝 `Long` 边界溢出导致请求体被静默读空的退化路径。
- **`@PlatformAccess` 双形态取用户**（security）：改经 `UserContext`（微服务身份头优先、Sa-Token 会话回退），修复身份头默认关闭后单体形态平台资源被误拒的问题；配套装配回归测试锁定身份头默认不装配的安全语义。
- **Nacos 路由应用超时**（gateway）：路由 delete-all→save-all 应用带 10s 超时上限，防路由存储挂起时无限阻塞；失败日志如实描述"可能部分应用/清空"，不再误导性声明仍在生效。
- **多级缓存过期语义**（cache）：`expire()` 在 L2 无该键时不广播仅清理本地残留，避免无效广播与 L1 抖动。

## [2.2.2] - 2026-09-09

**全仓体检安全加固与健壮性修复**（基于 ypbin 四仓 2026-09-08 审计结论，详见 ypbin 母仓 `AUDIT-2026-09-08.md`）。

### 安全
- **身份头信任默认关闭**（security）：`ypbin.security.identity.enabled` 由默认开启改显式开启（`matchIfMissing=false`），未位于可信网关之后的宿主默认不信任外部 `X-User-Id/X-Roles` 头；装配状态有启动日志。⚠️ 微服务宿主需显式配置开启。
- **审计人双形态**（security）：`SecurityAuditorAutoConfiguration` 委托 `UserContext`（身份头优先、Sa-Token 会话回退），修复微服务形态下审计字段恒空的缺陷。
- **`@PlatformAccess` 默认拒绝**（security）：`PlatformUserChecker` 默认实现改为拒绝（fail-closed），未登录直接拒绝，杜绝"看似受保护实则放行"。
- **密码错误锁定升级**（security）：失败计数叠加账号全局维度（防轮换 IP 绕过），账号维锁定时长更长；`getLockStatus` 账号维优先反映。
- **登录回验失败回收会话**（security）：回验异常时先注销刚建立的 token 再抛，杜绝幽灵登录态。
- **身份头解析容错**（security）：畸形 `Long` 头按无有效身份处理，不中断请求。

### 健壮性
- **可重复读请求体上限**（web）：新增 `ypbin.web.repeatable-read.max-body-bytes`（默认 10MB），超限拒绝读取（413 语义），防无界内存占用；补覆盖率测试。
- **方法级参数校验异常**（web）：补齐 `ConstraintViolationException`/`HandlerMethodValidationException` 处理，参数错误不再误报系统异常。
- **网关路由保护**（gateway）：Nacos 路由配置为合法空列表 `[]` 时保留现网不清空；`applyRoutes` 串行化防并发交错。
- **任务防双跑**（job）：任务级内存互斥，慢执行跨触发间隔不再单节点双跑（多节点防重仍由分布式 per-slice 锁承担）。
- **Feign 默认超时/熔断**（cloud-core）：注入 resilience4j 默认配置（TimeLimiter 10s、滑动窗口 20、失败率 50% 等），替换库内建 1s 硬超时，业务可逐项覆盖。
- **License 过期即时性**（license）：断言路径 5s 节流本地时钟过期快查，不依赖业务手工调度定期任务。
- **多级缓存过期语义**（cache）：`expire()` 与 set/delete 一致失效本地 L1 并广播；失效广播改长度前缀定界（key 可含任意字符）。
- **幂等失败即释放**（tools）：业务方法异常路径删除占位键允许立即重试；`IdempotentStore` 增默认 `release()` 扩展点。
- **IP 限流防伪造**（tools）：新增 `ypbin.tools.rate-limit.trust-forwarded`（默认 false 取真实对端地址），反代部署需显式开启。
- **AES 密钥校验前移**（tools/data）：字符串密钥长度 16/24/32 字节入口/装配期校验（fail-fast）。
- **XSS 过滤边界说明**（web）：明确黑名单删除式清洗局限与 JSON 请求体不在本过滤器范围，防注释误导。

### 其它
- 签名算法 MD5 标废弃（引导 HMAC-SHA256）；`@ApiEncrypt` 失败路径响应格式契约化说明；SSE 配置元数据默认值与代码一致；`JobDefinition.timeoutSeconds` 语义注释修正。

## [2.2.1] - 2026-09-07

**微服务 SSE 修复**：SSE 订阅/换票端点用户解析兼容网关身份头形态——此前仅认 Sa-Token 会话，微服务下游（关闭本地会话、身份在 `X-User-Id` 头）调用换票端点误报"登录状态已过期"。

### 修复
- **SSE 用户解析双形态**（security）：`SecuritySseUserIdResolver` 改为 `IdentityContext`（网关身份头，微服务下游）优先、`LoginHelper.getUserIdSafely()`（Sa-Token 会话，单体/网关侧）兜底——ypbin-admin 微服务版的站内信实时推送可正常取票订阅。

## [2.2.0] - 2026-09-05

**任务调度中心化**：新增 XXL-JOB 执行器接入壳（ypbin-starter-xxljob），业务侧定时任务由自研轻量调度迁移至 XXL-JOB 分布式调度中心统一管理（admin 的 main/boot 已随迁）。

### 新增
- **`ypbin-starter-xxljob`**：XXL-JOB v3.4.2 执行器自动装配——`XxlJobSpringExecutor` + `ypbin.xxl-job.*` 配置绑定（admin-addresses/appname/port/accessToken/logPath 等），业务方法标注 `@XxlJob("handler")` 即注册为可调度任务。默认 `enabled=false`，业务侧显式开启；配置缺失（admin 地址/执行器名）启动即抛错暴露，禁静默降级。xxl-job-core 3.4.2 无 javax 依赖，兼容 Boot 4.1 / JDK 21。

## [2.1.1] - 2026-09-04

**微服务修复版本**：网关/云模块时间序列化对齐 Jackson 3、Feign 统一响应解析、WebFlux 网关装配修复，并修复 Release 自动发布流水线（tag 触发时 detached HEAD 无法 push）。

### 新增
- **`FeignResponses`**（cloud-core）：Feign 调用统一解析 `R<T>` 响应——`dataOrThrow(resp, msg)` 校验远程业务成功、失败抛 `BusinessException`（禁止静默降级），`dataOrDefault` 兜底可选值。

### 修复
- **网关错误响应时间戳序列化错误**（cloud-gateway）：Jackson 2 → Jackson 3（`tools.jackson` ObjectMapper），修复错误响应 `timestamp` 序列化为数组/ISO 而非 `yyyy-MM-dd HH:mm:ss`。
- **Sa-Token 共享会话 JSON 反序列化失败**（security）：注册 `LoginUser` 到 Sa-Token JSON 反序列化白名单（`META-INF/satoken/sa-json-type.list`）。
- **WebFlux 网关装配误载**（security）：`SecurityAutoConfiguration`/`IdentityAutoConfiguration`/`PlatformAccessAutoConfiguration` 增加 Servlet Web 条件，WebFlux（Spring Cloud Gateway）环境下不再错误装配。
- **Spring Cloud 兼容性检查误报**（cloud）：禁用 compatibility-verifier 对官方支持 Boot 4.1.x 的误报（显式断言跳过）。

### 工程
- Release 流水线修复：tag 触发的 checkout 处于 detached HEAD，`git push` 改用显式 refspec `git push origin HEAD:master`（此前 v2.1.0 因该缺陷 Release 未建成，latest 卡在 v2.0.0）。
- CHANGELOG 模块数口径修正 36 → 35（v1.4.0 起实为 35 个模块）。

## [2.1.0] - 2026-09-01

**微服务增强版本**：新增微服务身份头上下文、平台访问控制、声明式缓存失效与永久缓存支持（配合 ypbin-admin 微服务版使用）。

### 新增
- **`IdentityContext`/`IdentityHeaderFilter`/`IdentityHeaders`**（security）：微服务身份头模式——网关校验 token 后签发 `X-User-Id` 等可信头，下游服务经过滤器构建当前用户；开关 `ypbin.security.identity.enabled`。
- **`@PlatformAccess`/`PlatformUserChecker`/`PlatformAccessAspect`**（security）：平台用户访问守卫（注解 + SPI 判定 + AOP 切面）；开关 `ypbin.security.platform.enabled`。
- **`@CacheEvict`**（cache）：声明式缓存失效注解 + AOP 切面——写操作方法标注后成功后自动删缓存键（SpEL 表达式，支持事务提交后执行）。
- **`CacheService.getOrLoad` 支持 `ttl=null` 永久缓存**：配合主动失效（`@CacheEvict`/手动 delete）实现「数据未变更永远命中缓存」。
- **`UserContext` 门面化**：自适应 IdentityContext（微服务身份头）优先、Sa-Token 会话回退（单体），业务代码无需感知部署形态。
- **`FeignProperties` 默认透传身份头**：二次 RPC 自动透传 `X-User-Id`/`X-Tenant-Id`/`X-Roles` 等，下游识别调用者身份。

### 修复
- 微服务身份头模式下 `UserContext.getTenantId()` 必崩/租户击穿（门面化根治）。
- `@CacheEvict` 事务提交前删缓存的并发脏读竞态（改 `afterCommit` 执行）。

## [2.0.0] - 2026-08-31

**破坏性变更版本**：删除控制器基类 `BaseController`，公开 API 与继承结构发生不兼容调整，详见下方「迁移指南」。共 35 个模块。

### 破坏性变更（迁移指南）
- **`BaseController` 已删除**：原 38 个 protected 辅助方法按职责迁移至静态工具/既有 API，业务控制器一律改为普通 `@RestController`：
  - 请求上下文（`request()/path()/method()/header()/param()/ip()/file()/files()`）→ `cn.ypbin.starter.web.util.WebRequestUtils`（静态方法同名调用）
  - 当前用户（`isLogin()/userId()/username()/tenantId()`）→ `cn.ypbin.starter.security.core.UserContext`（静态方法）
  - 响应包装（`ok()/data()/success()/fail()/status()`）→ `cn.ypbin.starter.core.model.R` 静态工厂（`R.ok()/R.fail()`）
- **`CrudController` 不再继承 `BaseController`**：继承 `CrudController` 的标准 CRUD 控制器不受影响（其内部已改直接用 `R.ok()`）；但若子类曾直接调用基类辅助方法，需按上表迁移
- **`GlobalExceptionHandler` 参数校验入口合并**：`MethodArgumentNotValidException` 与 `BindException` 两 handler 合并为 `BindException` 单入口（前者是其子类），行为不变
- **`GlobalErrorCode` 新增 `METHOD_NOT_ALLOWED(405)`**：405 由裸数字改为枚举常量

### 新增
- **`EntityStatus` 枚举**（`ypbin-starter-data`）：`ENABLED(1)/DISABLED(0)`，`BaseEntity.status` 默认值改引枚举，业务侧禁止裸写 `0/1`
- **`WebRequestUtils` 静态工具**（`ypbin-starter-web`）：HTTP 请求上下文读取的统一入口，替代原基类辅助方法
- **deploy 环境变量化**：`deploy/docker-compose.yml` 弱口令改为 `${VAR:?}` 强制从 `.env` 注入（新增 `deploy/.env.example`），sentinel Dockerfile 移除内嵌默认密码

### 修复与优化
- 双参数校验 handler 冗余消除；405 魔法数字清零
- 控制器层彻底组合优于继承，业务代码不再被迫继承"工具箱"基类

## [1.4.1] - 2026-08-29

修复与微调，共 35 个模块。

### 修复
- **构建兼容**：显式指定 `maven-compiler-plugin 3.13.0`，修复老 Maven（3.8.x）默认 3.1 不支持 `release` 属性导致的编译失败
- **README 与文档**：快速开始 BOM 版本同步至 1.4.1，README 升级为专业大厂风格排版

## [1.4.0] - 2026-08-28

全线升级至 Spring Boot 4.1.0 + JDK 21 基线，新增企业级 AI 对话与 RAG 模块，全面迁移至 Apache Fesod 2.0.2 孵化器新架构并加固多项组件。共 35 个模块。

### 核心升级
- **基线升级**：全面升级至 **Spring Boot 4.1.0** + **JDK 21**，引入虚拟线程与现代 Java 语言特性
- **AI 对话与 RAG 模块**（`ypbin-starter-ai`）：基于 Spring AI 2.0 的配置驱动动态多模型运行时。支持多模型动态切换（`AiModelConfigResolver`）、多轮会话记忆（内存 / JDBC 持久化）、流式 SSE（`Flux<String>`）与 RAG 检索增强（`AiRagService`、`DocumentLoader`、`LazySimpleVectorStore`）
- **Excel 引擎全面升级**（`ypbin-starter-excel`）：FastExcel 迁移至官方新坐标 **Apache Fesod 2.0.2-incubating**（`org.apache.fesod:fesod-sheet`），修复底层 SSRF 漏洞（CVE-2026-49328），写操作默认装配 `LongestMatchColumnWidthStyleStrategy` 自适应列宽，新增 `exportTemplate()` 纯表头模板导出
- **`@SensitiveWordFilter` 注解驱动过滤**（`ypbin-starter-sensitive-words`）：新增 `@SensitiveWordFilter` 双目标注解（FIELD + METHOD）与 AOP 切面，自动遍历入参替换命中敏感词
- **`@Idempotent` 幂等防重提交组件**（`ypbin-starter-tools`）：支持分布式 Redis 与本地内存双引擎，支持参数表达式与 Token 防重
- **依赖与安全升级**：Sa-Token 升级至 `1.46.0`，Bouncy Castle 升级至 `1.85.2`

### 修复与优化
- **登录拦截器误伤异步错误分发**（`ypbin-starter-security`）：非 REQUEST 分发直接放行，避免 SSE 错误分发时上下文缺失引发异常
- **代码规范治理**：全量消除所有内联 FQCN 引用，移除子模块冗余依赖声明，统一由根 POM / BOM 治理
- **单元测试与 CI**：覆盖 AI、Excel、SensitiveWords 等核心模块，35 模块全量构建通过

## [1.3.0] - 2026-08-14

增强定时任务、验证码、第三方登录与 License 联机校验稳定性，并补充多个模块的单测覆盖。共 34 个模块。

### 增强
- **定时任务 Cron 前置校验**（`ypbin-starter-job`）：新增 `CronService` 接口与 `SpringCronService` 实现（基于 Spring `CronExpression`），`JobManager.register()` 时对 cron 触发的任务先校验表达式语法，非法即拒绝并给出明确错误，不再等到真正触发才暴露；并提供 `nextExecutionTimes` 预览后续触发时间点
- **License 联机授权失败策略**（`ypbin-starter-license`）：新增 `RemoteFailurePolicy`（`FAIL_CLOSED` / `FAIL_OPEN_WITH_WARNING`），`HttpRemoteVerifyProvider` 依据策略裁决网络异常/超时/非 200 与明确拒绝三类结果，可配置下更从容应对被调方短暂不可用
- **第三方登录动态注册**（`ypbin-starter-social`）：新增 `SocialRequestRegistry` + `DefaultSocialRequestRegistry`（线程安全），宿主可在运行时动态注册/停用平台请求，不再需要重启服务调整第三方登录配置
- **验证码资源自愈**（`ypbin-starter-captcha`）：新增 `CaptchaResourceReloader` 接口，`CaptchaService.generate` 捕获资源数据丢失异常后自动 reload 默认资源并重试，解决远程 Redis 重启未持久化导致的验证码 500
- **缓存多级/Redis 完善**（`ypbin-starter-cache`）：多级缓存与 Redis 缓存实现完善（含超时等待兜底）并补测试

### 工程
- 补充 cache / job / tenant / crud（分页参数校验）/ datapermission / sign / license / social 等模块单元测试
- README 补充官网文档链接；补充 Apache-2.0 LICENSE 与许可证文件；`.claude` 开发目录移出版本管理

## [1.2.0] - 2026-08-07

新增日志字段掩码与 License 联机校验加固，修复访问日志切面失效、SSE 长连接超时刷屏、多个可选 Redis 依赖装配隐患。共 34 个模块。

### 新增能力
- **`@LogMask` 字段掩码**（`ypbin-starter-log`）：`LogMaskModule` 注册进 Jackson，标注字段序列化进访问日志/操作日志时自动替换为掩码，避免明文密码等敏感字段落盘
- **访问日志切面改造**：`AccessLogInterceptor` 改为 `AccessLogAspect`（AOP 环绕通知），Request/Response 分块打印，新增 `===Handler===` 打印当前处理方法所属类名与方法名，便于按调用定位日志
- **License 联机校验加固**：`HttpRemoteVerifyProvider` 引入缓存窗口（避免高并发下每次方法调用同步发起 HTTP 校验）、single-flight（同一 licenseId 并发校验合并为一次请求）、失败退避重试，网络异常/超时/非 200 与服务端明确拒绝三种结果分桶裁决，仅明确拒绝阻断，其余放行并告警
- **验证码多背景图**：`ypbin.captcha.background-resources` 支持配置多张自定义背景，随机取用，默认回退加载内置背景
- **SSE 心跳保活**：长连接默认不超时（`timeout=0`），新增 `heartbeat-interval-seconds`（默认 30s）定期发送保活帧，中间代理不再误判空闲断连

### 修复
- **访问日志切面完全不生效**：切入点 `@within(RestController) || @within(Controller)` 组合触发 AspectJ 「Type referred to is not an annotation type」异常，导致整个切入点匹配失败、controller 未被 AOP 代理；改为只保留 `@within(RestController)` 分支
- **SSE 长连接约 5 分钟必断且刷屏噪音**：Tomcat 异步超时从建连起总计时，默认 300s 到点即掐；全局异常处理器把 `AsyncRequestTimeoutException` 当未知系统异常记 ERROR 全栈，并试图向已中断的 event-stream 响应写入 JSON 体二次报错；补充专属异常处理与心跳保活
- **可选 Redis 依赖类级 `@ConditionalOnClass` 挡不住 Bean 缺失**：security/sign/tools/messaging 四个模块的 Redis 存储嵌套配置类只判断 `StringRedisTemplate` 是否在 classpath，未判断容器内是否真有该 Bean；消费端传递引入 spring-data-redis 却未配置连接时，嵌套配置仍展开、`UnsatisfiedDependencyException` 崩溃，统一补充 `@ConditionalOnBean(StringRedisTemplate.class)`
- **验证码默认资源不自动加载**：`init-default-resource` 关闭或仅加载模板字体时背景图仍为空，访问 `/captcha` 500；新增 `CaptchaResourceInitializer` 幂等补齐

### 工程
- 移除 License v1 授权串校验遗留代码（v2 压缩格式已稳定，不再需要兼容分支）
- 启动横幅收敛：data/messaging 模块默认关闭第三方组件启动横幅打印，业务方仍可覆盖开启
- `docs/MODULES.md` 补充联机校验缓存窗口语义、验证码多背景配置说明

## [1.1.0] - 2026-08-06

新增 License 商业授权能力与 SSE 安全加固，并修复多个由真实消费端实测暴露的地基级问题。共 34 个模块。

### 新增能力
- **License 商业授权模块**（`ypbin-starter-license`）：机器指纹绑定 + 使用期限 + SM2 签发验签 + `@LicenseCheck` 注解式模块/参数级授权 + 登录回验（`LoginVerifyProvider` 扩展点）+ 联机校验（`RemoteVerifyProvider`），覆盖离线授权到在线鉴权的商业授权防护链路
- **SSE 一次性订阅票据**：`EventSource` 原生不能带 `Authorization` 头，新增「先换票再订阅」——带令牌换短时一次性票据（`SseTicketStore` 内存/Redis 双实现，原子消费防重放），再凭票据订阅；订阅端点 `ticket` 参数与登录态两种鉴权方式共存
- **安全扩展点**：`SseUserIdResolver`（SSE 订阅用户解析）、`SecurityExcludePathProvider`（全局登录拦截放行路径贡献）

### 安全修复
- **SSE 内置订阅端点越权**（严重）：原来仅凭 URL 上的 `userId` 建立长连接、无任何鉴权，任何人拿到他人 userId 即可订阅其推送；现改为由服务端登录态解析当前用户，前端传参不再被信任
- **SSE 端点未注册**：自动配置顺序缺陷导致订阅/换票端点静默不生成（No mapping），修复排序约束
- **SSE 订阅被全局登录拦截拦死**：订阅端点靠 ticket 自证身份却撞上自身登录拦截，现自动放行订阅路径（换票路径仍保留拦截）

### 修复
- **tools 无 Redis 环境启动崩溃**：`@Bean` 方法签名直接引用可选依赖类型 `StringRedisTemplate`，方法级 `@ConditionalOnClass` 拦不住配置类内省，无 Redis 时 `NoClassDefFoundError` 启动即崩；Redis 存储收拢到类级条件嵌套配置
- **MultiLevelCache 同类隐患**：有 Caffeine 无 Redis 时内省崩溃，类级 `@ConditionalOnClass` 并入 `StringRedisTemplate`
- 补充 api-crypto / data-permission / i18n / sensitive-words 等模块测试覆盖

### 工程
- 新增 GitHub Actions CI（push/PR 自动编译、代码风格校验与测试）
- README 首页重写（徽章、设计取舍章、发布坐标修正），模块文档拆至 `docs/MODULES.md`

[1.4.0]: https://github.com/wenbin-wb/ypbin-starter/releases/tag/v1.4.0
[1.3.0]: https://github.com/wenbin-wb/ypbin-starter/releases/tag/v1.3.0
[1.2.0]: https://github.com/wenbin-wb/ypbin-starter/releases/tag/v1.2.0
[1.1.0]: https://github.com/wenbin-wb/ypbin-starter/releases/tag/v1.1.0

> 已发布至 Maven Central（`cn.ypbin`）。发布过程中修复了无 parent 的三个聚合 POM
> （根聚合、`dependencies`、`bom`）缺少 `url/licenses/scm/developers` 元数据与 GPG 签名的问题，
> 元数据统一下沉到 `dependencies`（供子模块继承）与 `bom`（自带）。

首个正式版本。基于 Spring Boot 3.5 的开箱即用基础能力 starter 集合，覆盖单体与微服务，共 33 个模块。

### 基础能力
- 统一响应 `R`、异常体系、树形工具、上下文透传（core）
- Jackson 统一序列化、`@Sensitive` 脱敏、`@DictText` 字典翻译、`@RefText` 引用翻译（json）
- 全局异常、CORS、XSS、可重复读请求（web）
- MyBatis-Plus 增强、`BaseEntity`、字段加密、雪花 ID（data）
- Redis 缓存 + 三重防护 + 多级缓存（cache）
- Sa-Token 封装、登录客户端策略、密码策略（复杂度/错误锁定/有效期）、在线用户（security）
- 文件存储（本地/S3，配置可动态化）、操作日志（IP 归属地/UA 解析扩展点）、常用工具（限流/幂等/锁）

### 扩展能力
- 多租户、通用 CRUD（含权限前缀自动鉴权）、数据权限
- Excel、行为验证码、邮件（配置可动态化）、短信（sms4j）、WebSocket/SSE/MQTT、敏感词、国际化、接口加解密、接口签名、第三方登录、异步线程池、定时任务

### 微服务
- Feign 增强、Nacos、版本灰度负载均衡、网关、可观测性、流量防护

[1.0.0]: https://github.com/wenbin-wb/ypbin-starter/releases/tag/v1.0.0

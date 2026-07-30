# ypbin-starter 建设方案与进度跟踪

> 一套自研的 Spring Boot 基础能力 starter 集合。参考 `blade-tool`（微服务全家桶思路）
> 与 `continew-starter`（单体 Web 能力集、模块化理念），**代码全部按最优实践重构**，
> 非照抄。本文档是贯穿项目始终的方案 + 进度看板。

## 1. 定位与设计原则

- **分层**：L1 基础层（单体/微服务共用，**不依赖 Spring Cloud**）+ L2 扩展层 + L3 微服务层。
- **模块粗粒度**：一能力一模块；模块内用策略接口预留扩展点，先粗后细。
- **约定优于配置**：默认自动装配 + 统一 `ypbin.*` 配置前缀；开箱即用。
- **可覆盖可开关**：`@ConditionalOnMissingBean`（业务方可覆盖）+ `@ConditionalOnProperty`（按需启停）。
- **按需引入**：BOM 统一版本，业务方只依赖需要的模块。

## 2. 技术栈（基线）

| 项 | 选型 |
|---|---|
| JDK | 17 |
| Spring Boot | 3.5.x |
| 认证 | Sa-Token |
| ORM | MyBatis-Plus（起步单实现） |
| 缓存 | Redis（起步单实现，预留 CacheService 接口） |
| API 文档 | SpringDoc OpenAPI |
| 工具库 | Hutool（按需） |
| 版本治理 | `${revision}` + flatten-maven-plugin + BOM |
| 自动配置 | `AutoConfiguration.imports`（新机制） |
| 代码风格 | spotless + 统一 license 头 |

## 3. 坐标与命名约定

- groupId：`cn.ypbin.starter`
- 根包：`cn.ypbin.starter`
- 版本：`1.0.0-SNAPSHOT`（`${revision}` 管理）
- 模块 artifactId 前缀：`ypbin-starter-`
- 配置前缀：`ypbin.<模块>.<子项>`，集中在 core 的 `PropertiesConstants`
- 类名：**不强制品牌前缀**，保持中性命名（如 `R`、`GlobalExceptionHandler`），仅在易冲突处酌情加 `Ypbin`

## 4. 模块蓝图

```
ypbin-starter/                        聚合 POM
├── ypbin-starter-dependencies        BOM + 所有模块 parent（管三方库版本、插件）
├── ypbin-starter-bom                 对外 BOM（只列自身模块坐标）
│
├── L1 基础层
│   ├── ypbin-starter-core            常量/异常/统一响应R/BaseEnum/工具/SpringUtils
│   ├── ypbin-starter-json            Jackson 统一配置
│   ├── ypbin-starter-web             MVC/CORS/全局异常/优雅响应/XSS
│   ├── ypbin-starter-data            MyBatis-Plus 增强（BaseEntity/分页/自动填充/Query）
│   ├── ypbin-starter-cache           Redis 缓存（预留 CacheService 策略接口）
│   ├── ypbin-starter-security        Sa-Token 封装（LoginHelper/权限数据源/放行）
│   ├── ypbin-starter-log             操作日志 AOP 采集
│   ├── ypbin-starter-api-doc         SpringDoc/OpenAPI
│   ├── ypbin-starter-storage         文件存储（策略模式：本地/OSS）
│   └── ypbin-starter-tools           验证码/限流/幂等/加解密（起步合并，涨了再拆）
│
├── L2 扩展层（可选）
│   ├── ypbin-starter-extension-crud            通用 CRUD 基类
│   ├── ypbin-starter-extension-tenant          多租户（MP 行级隔离）
│   └── ypbin-starter-extension-datapermission  数据权限
│
└── L3 微服务层（仅微服务工程引）
    ├── ypbin-starter-cloud-launch    启动器 + SPI 扩展点（LauncherService）
    ├── ypbin-starter-cloud-core      Feign 增强/请求头透传/熔断
    └── ypbin-starter-cloud-gateway   网关通用能力
```

## 5. 进度看板

状态图例：⬜ 待办 / 🟡 进行中 / ✅ 完成 / ⏸️ 阻塞

### 里程碑 M0 — 地基（骨架可编译）
- ✅ 聚合 POM（`ypbin-starter/pom.xml`，`${revision}`、模块列表、build 插件）
- ✅ `ypbin-starter-dependencies`（dependencyManagement + pluginManagement + 版本属性）
- ✅ `ypbin-starter-bom`（对外 BOM）
- ✅ `ypbin-starter-core`（常量/异常/统一响应/BaseEnum/SpringUtils/CoreAutoConfiguration）
- ✅ 环境验证：`mvn -DskipTests clean install` **BUILD SUCCESS**（9 模块全通过，产物已装入本地仓库）
  - 工具链：IntelliJ 内置 JBR(Java 25) + 内置 Maven3，编译 target Java 17
  - 修复记录：core 补 slf4j-api；json 补 spring-web；`matchIfAbsent`→`matchIfMissing`；MyBatis-Plus 3.5.9→3.5.16 并显式引入 mybatis-plus-jsqlparser

### 里程碑 M1 — Web 最小可用
- ✅ `ypbin-starter-json`（Jackson 配置：JavaTime、大数字转字符串、未知字段容错）
- ✅ `ypbin-starter-web`（全局异常处理、CORS 可选装配）
- ✅ 各模块 AutoConfiguration + Properties + imports 文件
- ⬜ 冒烟：建一个 demo 应用引入 web，能返回统一 R 结构（留待编译验证阶段）
- 备注：XSS 过滤下沉到 security 模块或后续 tools，M1 暂不做

### 里程碑 M2 — 数据与认证
- ✅ `ypbin-starter-data`（MyBatis-Plus：BaseEntity 审计字段、分页拦截器、自动填充、AuditorProvider 扩展点）
- ✅ `ypbin-starter-security`（Sa-Token：LoginHelper、PermissionProvider 扩展点 + StpInterface 适配、审计桥接）
- ✅ `ypbin-starter-cache`（Redis + CacheService 策略接口 + JSON 序列化 RedisTemplate）

**M2 亮点：模块解耦设计**
- data 模块定义 `AuditorProvider` 扩展点（默认返回空），不依赖 security；
  security 模块用 `@ConditionalOnClass` 桥接，登录后自动填充真实操作人。
- security 定义 `PermissionProvider` 扩展点，业务方无需直接依赖 Sa-Token API 即可提供权限数据。

### 代码审核修复（2026-07-30，经字节码核实后确认真实存在）
- ✅ **Bug 修复** DefaultMetaObjectHandler：更新填充改用 `setFieldValByName` 强制覆盖，
  修复 strict 模式下字段已有值导致更新时间/更新人不刷新的问题。
- ✅ **Bug 修复** CacheAutoConfiguration：Redis 值序列化器复用容器 ObjectMapper 的 `copy()`
  并独立开启 default typing，既继承 JSON 规则又不污染 MVC 共享实例。
- ✅ **优化** JacksonAutoConfiguration：改用 `serializerByType/deserializerByType`，不再 new
  JavaTimeModule 塞 Long、不再用 `builder.modules()` 覆盖模块体系（避免废掉 Boot 自动注册的模块）。
- ✅ **统一** GlobalExceptionHandler：所有异常统一返回 HTTP 200，由 R.code 区分（去掉 4xx/5xx ResponseEntity）。
- ✅ **约定** 全部类补 `@author wenbin` + `@since 2026-07-30`（日期入 since，不用 @date/版本号），后续新增类同此约定。
- 补依赖：cache 模块显式引入 jackson-databind（data-redis 中为 optional 不传递）。
- 审核第 4 项（HTTP 状态码）非 Bug 属设计取舍；BladeView 为字段分级视图机制，可后续排入扩展层。

### 里程碑 M3 — 常用能力
- ✅ `ypbin-starter-api-doc`（SpringDoc：OpenAPI 元信息可配置，标题/版本/联系人/license）
- ✅ `ypbin-starter-storage`（本地 + S3 兼容对象存储；多源 List 配置 + platform 路由；
  统一 FileProcessor 责任链；registrar 贡献者模式；可选 FileRecorder/分片）
  - 相比参考项目改进：砍掉 continew 的装饰器管理器/事件机制/ThreadLocal/胖 FileRecorder；
    S3 单策略覆盖阿里云/腾讯/MinIO/七牛，不像 blade 每家一个 Template。
- ✅ `ypbin-starter-log`（操作日志：@Log 注解 AOP + Include 采集粒度 + LogDao 扩展持久化）
  - 相比参考项目改进：不学 continew 拆 core/aop/interceptor 三个子模块（过度切分），
    单模块 + 单 AOP 实现；LogUserProvider 扩展点与 security 解耦；默认不采集请求/响应体防敏感信息落库。
  - 代码审核修复（3 项，经确认属实）：
    1. 请求体采集：改从 AOP 入参序列化（getParameterMap 拿不到 @RequestBody JSON），过滤不可序列化特殊参数。
    2. 异步化：切面只发 LogEvent，@Async @EventListener 异步落库，写日志移出业务请求线程（防 DB 抖动拖垮接口）。
    3. 注解查找：改用 AnnotatedElementUtils.findMergedAnnotation，避免 JDK 动态代理下 @Log 注解丢失。
- ✅ `ypbin-starter-tools`（限流 @RateLimit + AOP + 可插拔 RateLimiterStore；AES-GCM 加解密工具）
  - 限流默认本地内存 store，可实现 Redis 版覆盖；加解密用 AES-GCM 认证加密（非老旧 ECB/CBC）。
  - 幂等/验证码留待后续（涉及额外三方库，按需再加）。

**M3 全部完成，13 模块 BUILD SUCCESS。**

### 里程碑 M4 — 扩展层
- ✅ `ypbin-starter-extension-crud`（BaseController/BaseService/BaseServiceImpl + PageQuery/PageResult）
  - BaseService 继承 MyBatis-Plus IService 只加 page()，避免重复声明导致泛型擦除签名冲突。
- ✅ `ypbin-starter-extension-tenant`（MyBatis-Plus 行级隔离；TenantProvider 扩展点与其他模块解耦）
  - 架构改进：data 模块引入 `InnerInterceptorProvider` 扩展点，各模块按 order 贡献内部拦截器，
    data 收集排序装配。解决"多租户必须先于分页"的顺序问题，且 tenant/datapermission/分页可共存。
- ✅ `ypbin-starter-extension-datapermission`（MyBatis-Plus 行级数据范围；DataScopeHandler 扩展点返回 SQL 片段）
  - 安全取舍：不提供默认 DataScopeHandler，@ConditionalOnBean 未提供规则时整个能力不装配，
    杜绝"默认放行/默认全拦截"两种不安全猜测。复用 InnerInterceptorProvider（order 200，多租户后分页前）。

**M4 扩展层全部完成，16 模块 BUILD SUCCESS。**

### 里程碑 M5 — 微服务层（按需）
- ⬜ `ypbin-starter-cloud-launch`（+ LauncherService SPI）
- ⬜ `ypbin-starter-cloud-core`（Feign/熔断/请求头透传）
- ⬜ `ypbin-starter-cloud-gateway`

### 里程碑 M6 — 工程化
- ✅ spotless + license 头统一（内联 Apache-2.0 头 + import 顺序 + 去多余空白 + 去未用 import；
  verify 阶段 check 强制校验；apply 一键格式化。license 头用 content 内联避免跨模块路径问题）
- ✅ 根 README（特性/技术栈/快速开始/模块总览表/13 个模块逐一的配置与用法示例/构建发布说明）
- ✅ 发布配置（release profile：source/javadoc/gpg，默认不激活，`mvn deploy -Prelease` 触发；
  gpg 本地未缓存，仅发布时需要，不影响日常构建）
- ✅ .gitignore

### 里程碑 M7 — 安全与数据能力增强（对比参考项目补齐缺口）✅ 全部完成
批次一（Web 安全）：
- ✅ XSS 过滤（web：XssFilter + XssHttpServletRequestWrapper + XssCleaner；可配开关与排除路径；默认关闭）
- ✅ 幂等 @Idempotent（tools：注解 + AOP + IdempotentStore；内存/Redis-Lua 两实现；支持 SpEL 键）

批次二（数据安全）：
- ✅ 数据脱敏 @Sensitive（json：字段注解 + ContextualSerializer；PHONE/ID_CARD/BANK_CARD/EMAIL/CHINESE_NAME/CUSTOM 等）
- ✅ 雪花 ID 生成器（data：IdGenerator，复用 MyBatis-Plus IdWorker）

批次三（可选增强）：
- ✅ 字段加密（data：@TableField typeHandler=EncryptTypeHandler；FieldEncryptor 扩展点 + AES-GCM 默认实现；配 ypbin.data.encrypt.key 启用）
- ✅ 树形结构工具 TreeUtils（core：TreeNode 契约 + O(n) 列表转树 + 过滤）

抽取共享：SpelKeyResolver（tools，限流/幂等共用 SpEL 键解析）。

### 里程碑 M8 — 补齐参考项目特色能力（用户确认全做）✅ 全部完成
- ✅ Excel 导入导出（ypbin-starter-excel，FastExcel：ExcelUtils 读/写/HTTP导出，注解驱动）
- ✅ 验证码（ypbin-starter-captcha，easy-captcha：CaptchaService 生成+一次性校验，可插拔 CaptchaStore）
- ✅ 邮件（ypbin-starter-messaging，Spring Mail：MailService 文本/HTML/附件，ConditionalOnBean 装配）
- ✅ 国密 SM2/SM4（ypbin-starter-tools crypto，BouncyCastle：Sm4Utils 对称 + Sm2Utils 非对称+密钥对生成）
- 全量 19 模块 BUILD SUCCESS，30 单测全绿（新增 SM2/SM4 往返 3 个）。

### 里程碑 M9 — 依赖升级 + 验证码升级
- ✅ 依赖升到稳定最新：Spring Boot 3.5.16、sa-token 1.45.0、mybatis-plus 3.5.17、
  hutool 5.8.47、bouncycastle 1.85、springdoc 2.8.17
- ✅ 验证码换成行为验证码 tianai-captcha 1.5.5（滑块/旋转/点选/拼接 + 轨迹校验），
  替换只支持图形的 easy-captcha；captcha 模块改为薄封装 tianai 的 ImageCaptchaApplication
- 决策：不升 Spring Boot 4.x（大版本迁移，sa-token/mp 兼容性未验证，风险高）——用户拍板留 3.5 最新补丁
- 踩坑修复：mybatis-plus 3.5.17 破坏性重构，IService/ServiceImpl 从 extension.service 包
  迁到 spring.service 包（新 artifact mybatis-plus-spring），改 crud 模块两处 import 修复
- 全量 19 模块 BUILD SUCCESS，30 单测全绿

依赖策略：允许联网从中央仓库下载（FastExcel / easy-captcha / BouncyCastle 等）。
仍不做：短信多厂商、分布式事务、灰度、代码生成（依赖重或属微服务/独立工程）。

### 里程碑 M10 — 对齐参考项目剩余能力（用户逐项确认）
新模块：
- ✅ ypbin-starter-sensitive-words（Hutool DFA 敏感词，可插拔词库）
- ✅ ypbin-starter-i18n（Spring MessageSource 国际化，静态 I18nUtil + 参数/头 Locale 解析）
- ✅ ypbin-starter-api-crypto（@ApiEncrypt 接口加解密，RequestBody/ResponseBodyAdvice + 默认 AES）
- ✅ ypbin-starter-social（JustAuth 第三方登录，AuthRequestProvider 扩展点按平台注册）
并入现有模块：
- ✅ security：PasswordEncoderUtil（BCrypt）
- ✅ messaging：WebSocket（STOMP）+ MQTT（Paho 轻封装）
- ✅ log：AccessLogInterceptor 全量访问日志（与 @Log 注解版互补）
决策：验证码换 tianai-captcha（行为验证）；接口加解密用 Advice 而非 Filter（更地道）；
MQTT 用 Paho 直连而非 spring-integration（更轻）；负载均衡/灰度归微服务层 M5 不做。
全量 24 模块 BUILD SUCCESS，61 测试全绿。

### 单元测试（M6 补充）
- ✅ 引入 junit-jupiter + assertj（test scope）；27 个核心逻辑单测全绿
  （TreeUtils / SensitiveType / XssCleaner / AesFieldEncryptor / 限流并发 / 幂等并发）
- ✅ .gitattributes 统一行尾为 LF

## 6. 防侵权基线

- 不复制任一参考项目的源码；类/方法自行设计命名与实现。
- 不沿用 `Blade`/`ContiNew` 品牌前缀、不复用其包名。
- License 采用自选协议（默认 Apache-2.0，待用户确认）。
- 借鉴的是「架构模式与通用做法」，非具体代码文本。

## 7. 已确认决策

- ✅ groupId / 根包：`cn.ypbin.starter`
- ✅ 开源协议：Apache-2.0
- ✅ 本轮范围：一路做到 **M2**（M0 地基 + M1 Web + M2 数据/认证/缓存）
- ⏳ 微服务层 L3：暂缓（M5，按需）
- ⏳ 构建验证：命令行无 java/mvn，编译验证走 IDEA（或后续提供 JDK/Maven 路径）

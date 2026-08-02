# ypbin-starter 建设方案与进度跟踪

> 一套自研的 Spring Boot 基础能力 starter 集合，借鉴业界常见的微服务全家桶思路
> 与单体 Web 能力模块化理念，**代码全部按最优实践重构**。
> 本文档是贯穿项目始终的方案 + 进度看板。

## 0. 当前状态总览

- **模块**：33 个（L1 基础 10 + L2 扩展 3 + L3 微服务 6 + 应用聚合 2 + 依赖/BOM 2 + 其余能力模块含 async、job）
- **构建**：全量 33 模块 `clean install` BUILD SUCCESS，默认单测/装配测试全绿；`mvn test` 已触发 spotless 校验
- **里程碑**：M0~M10 全部完成；M5.1~M5.13 Cloud 补强完成；M6 工程化收尾完成
- **微服务真机验证**：网关全链路、Nacos 注册发现/配置、Feign 跨服务、Sentinel 限流均已通过真运行时/公网服务器验证（4 个 IT/E2E 沉淀仓库，`-Pit` 可复现）
- **技术基线**：JDK 17 · Spring Boot 3.5.16 · Spring Cloud 2025.0.3 · spring-cloud-alibaba 2025.0.0.0
- **后续可选**：CI 接入 `-Pit`、v1.0.0 发布、示例工程

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
├── ypbin-starter-dependencies        所有模块 parent（管三方库版本、插件、本项目模块 dependencyManagement）
├── ypbin-starter-bom                 对外 BOM（只列业务方可依赖的自身模块坐标）
│
├── L1 基础层
│   ├── ypbin-starter-core            常量/异常/统一响应R/BaseEnum/工具/SpringUtils/上下文透传/树形工具
│   ├── ypbin-starter-json            Jackson 统一配置（时间/Long 转字符串/脱敏）
│   ├── ypbin-starter-web             MVC/CORS/全局异常/优雅响应/XSS/可重复读请求
│   ├── ypbin-starter-data            MyBatis-Plus 增强（审计/分页/拦截器编排/字段加密/雪花 ID）
│   ├── ypbin-starter-cache           Redis 缓存（CacheService/CacheUtils/RedisUtils）
│   ├── ypbin-starter-security        Sa-Token 封装（LoginHelper/权限数据源/密码编码器）
│   ├── ypbin-starter-log             操作日志 AOP + 全量访问日志
│   ├── ypbin-starter-api-doc         SpringDoc/OpenAPI 增强
│   ├── ypbin-starter-storage         文件存储（策略模式：本地/S3兼容OSS）
│   └── ypbin-starter-tools           限流/幂等/签名辅助/AES/SM2/SM4/请求工具
│
├── 常用能力模块（按需引入）
│   ├── ypbin-starter-excel           FastExcel 导入导出
│   ├── ypbin-starter-captcha         tianai 行为验证码
│   ├── ypbin-starter-messaging       Mail/WebSocket/MQTT 发布订阅
│   ├── ypbin-starter-sensitive-words 敏感词检测/替换
│   ├── ypbin-starter-i18n            国际化 MessageSource + Locale 解析
│   ├── ypbin-starter-api-crypto      @ApiEncrypt 接口加解密
│   ├── ypbin-starter-social          JustAuth 第三方登录
│   └── ypbin-starter-sign            @ApiSign 接口签名/防重放
│
├── L2 扩展层（可选）
│   ├── ypbin-starter-extension-crud            通用 CRUD 基类
│   ├── ypbin-starter-extension-tenant          多租户（MP 行级隔离）
│   └── ypbin-starter-extension-datapermission  数据权限
│
└── L3 微服务层（仅微服务工程引）
    ├── ypbin-starter-cloud-core          Feign 增强/请求头透传/R 错误解码/熔断兜底
    ├── ypbin-starter-cloud-nacos         Nacos 注册发现/配置中心/LoadBalancer 依赖聚合/ConfigData 启动兜底
    ├── ypbin-starter-cloud-loadbalancer  版本灰度负载均衡
    ├── ypbin-starter-cloud-observability RequestId↔MDC + Tracing 门面
    ├── ypbin-starter-cloud-sentinel      Sentinel Web/Gateway 限流统一 R 响应
    └── ypbin-starter-cloud-gateway       网关 CORS/异常/鉴权/文档聚合/动态路由

应用聚合（纯依赖聚合，起步一键引入）
├── ypbin-starter-app-web                单体 Web 常用能力聚合
└── ypbin-starter-app-cloud              app-web + 微服务常用能力聚合
```

> Nacos ConfigData 导入与启动兜底已归并到 `ypbin-starter-cloud-nacos`，测试与被测能力保持同模块。
> 单体与微服务共用 L1、对外契约一致（见 `CONTRACT.md`），前端可复用同一套调用逻辑。

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
- 审核第 4 项（HTTP 状态码）非 Bug 属设计取舍；字段分级视图机制可后续排入扩展层。

### 里程碑 M3 — 常用能力
- ✅ `ypbin-starter-api-doc`（SpringDoc：OpenAPI 元信息可配置，标题/版本/联系人/license）
- ✅ `ypbin-starter-storage`（本地 + S3 兼容对象存储；多源 List 配置 + platform 路由；
  统一 FileProcessor 责任链；registrar 贡献者模式；可选 FileRecorder/分片）
  - 相比常见实现改进：砍掉装饰器管理器/事件机制/ThreadLocal/胖 FileRecorder；
    S3 单策略覆盖阿里云/腾讯/MinIO/七牛，不按云厂商重复拆多个 Template。
- ✅ `ypbin-starter-log`（操作日志：@Log 注解 AOP + Include 采集粒度 + LogDao 扩展持久化）
  - 相比常见实现改进：不拆 core/aop/interceptor 三个子模块（过度切分），
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
- ✅ `ypbin-starter-extension-crud`（BaseController/CrudController/BaseService/BaseServiceImpl + PageQuery/PageResult）
  - BaseController 只做轻量辅助；标准 CRUD 路由由 CrudController 承担，避免一个基类过重。
  - BaseService 继承 MyBatis-Plus IService 只加 page()，避免重复声明导致泛型擦除签名冲突。
- ✅ `ypbin-starter-extension-tenant`（MyBatis-Plus 行级隔离；TenantProvider 扩展点与其他模块解耦）
  - 架构改进：data 模块引入 `InnerInterceptorProvider` 扩展点，各模块按 order 贡献内部拦截器，
    data 收集排序装配。解决"多租户必须先于分页"的顺序问题，且 tenant/datapermission/分页可共存。
- ✅ `ypbin-starter-extension-datapermission`（MyBatis-Plus 行级数据范围；DataScopeHandler 扩展点返回 SQL 片段）
  - 安全取舍：不提供默认 DataScopeHandler，@ConditionalOnBean 未提供规则时整个能力不装配，
    杜绝"默认放行/默认全拦截"两种不安全猜测。复用 InnerInterceptorProvider（order 200，多租户后分页前）。

**M4 扩展层全部完成，16 模块 BUILD SUCCESS。**

### 里程碑 M5 — 微服务层（Nacos，已启动）
版本锁定（硬绑定，不可追最新）：
- Spring Boot 3.5.16 → Spring Cloud **2025.0.3** → spring-cloud-alibaba **2025.0.0.0**（Nacos）
- 注意：Spring Cloud 2025.1.x / alibaba 2025.1.0.0 是给 Boot 4.x 的，不能用。

#### M5 最终模块与能力（6 个 cloud 模块）

| 模块 | 能力 | 真机验证 |
|---|---|---|
| `cloud-core` | OpenFeign 头透传（白名单/大小写不敏感/Servlet 条件）+ `RResponseErrorDecoder`（下游 R 错误转 `FeignRemoteException`）+ Resilience4j 熔断默认开启 + `RFeignFallbackFactory` | ✅ FeignCrossServiceIT |
| `cloud-nacos` | 注册发现 + 配置中心 + LoadBalancer 依赖聚合 + Nacos ConfigData 启动兜底 | ✅ NacosDiscoveryIT + 单元测试 |
| `cloud-loadbalancer` | 版本灰度路由（请求头 + metadata 匹配、优先 IP、权重随机、可配置回退、Nacos metadata 自动写入） | 装配测试 |
| `cloud-gateway` | CORS / 全局异常统一 R / RequestId / 身份头清洗 / 可选鉴权 / Swagger 聚合 / Nacos 动态路由 | ✅ GatewayE2ETest |
| `cloud-observability` | X-Request-Id ↔ MDC 关联（核心零重依赖）+ Micrometer Tracing 门面（OTLP 可选） | 单元测试 |
| `cloud-sentinel` | Sentinel 限流（被调方保护，与 Resilience4j 双轨）+ 被拒统一 R 响应（429）+ Nacos 规则数据源 | ✅ SentinelFlowIT |

设计要点：`cloud-core` 的 Resilience4j 管「调用方容错」，`cloud-sentinel` 管「被调方限流保护」，互补不替换；身份头默认不透传，由可信网关清洗签发。

#### M5.1~M5.13 补强历程（要点留档）

| 批次 | 内容 |
|---|---|
| M5.1 | Feign 头大小写去重、Servlet 条件收紧、默认不透传身份头；Gateway CORS/异常/身份头清洗/可选鉴权 |
| M5.2 | 新增 cloud-loadbalancer 版本灰度 |
| M5.3 | Feign `RResponseErrorDecoder` + CircuitBreaker 默认开启 + `RFeignFallbackFactory` |
| M5.4 | API 文档增强（安全头/GroupedOpenApi/@ApiOrder/prod 关闭）+ Gateway Swagger 聚合 |
| M5.5 | Gateway Nacos 动态路由 + 热刷新 + 错误保护 |
| M5.6 | Gateway 测试补齐 13 个；修复 `HttpStatus.resolve()` 返回 null 兼容问题；README 补 Cloud 文档 |
| M5.7 | 自动配置装配测试（13 个）+ 配置元数据提示 + 新增 cloud-observability + deploy 自测环境 |
| M5.8 | 新增 cloud-sentinel + 统一 R 限流响应；核实 sentinel 1.8.9 webmvc_v6x 变体 |
| M5.9 | GatewayE2ETest 真运行时 E2E；**挖出并修复真 bug**：Gateway 4.3.0 路由前缀改为 `spring.cloud.gateway.server.webflux.routes` |
| M5.10 | NacosDiscoveryIT（Testcontainers 双模式）；**公网服务器真机验证注册发现 + 配置** |
| M5.11 | FeignCrossServiceIT；**公网真机验证** 注册→发现→负载均衡→Feign 调通 + 头透传 + R 错误解码 |
| M5.12 | SentinelFlowIT；真运行时验证限流被拒统一 R 响应（边界 1 收口） |
| M5.13 | 将 Nacos 启动兜底归并到 cloud-nacos：默认 profile、dev/test/prod 互斥校验、Nacos ConfigData 三层导入（公共/环境/应用环境）、import-check、Nacos 日志、Actuator info、Bean 覆盖开关等默认值兜底；采用标准 EnvironmentPostProcessor，不要求业务改用自定义启动类，且所有默认值均不覆盖业务显式配置 |

**边界验证结论**：网关全链路、Nacos 注册发现/配置、Feign 跨服务全链路、Sentinel 限流响应均已脱离「未验证」——分别以本机真运行时 E2E 或公网服务器真机验证覆盖，4 个 IT/E2E（`GatewayE2ETest` / `NacosDiscoveryIT` / `FeignCrossServiceIT` / `SentinelFlowIT`）沉淀在仓库、`-Pit` 可复现、默认构建不受影响。唯一未自动化：Sentinel 规则从 Nacos datasource 热更新（属 Sentinel 自身能力、非本项目职责，Dashboard 已在公网就绪，按 `deploy/README.md` 3.7 手动验证）。

### 能力对照结论（23 模块逐一核实）
通用基础层多数能力已覆盖常见微服务 starter 方案，但 Cloud 维度需单独看待：本项目已补齐灰度负载均衡、Feign/Sentinel/Fallback/Header 透传、单服务 OpenAPI 增强、独立 Gateway starter 与动态路由等能力。

ypbin 已有 11 项更轻量或更完整的能力——限流 @RateLimit、幂等 @Idempotent、接口签名 @ApiSign、
敏感词、数据脱敏 @Sensitive、行为验证码、密码编码器、WebSocket、MQTT、国密 SM2/SM4、异步上下文透传。

（原“仍建议补齐 Cloud 企业级治理”4 项——灰度 LB / Feign 容错 / API 文档增强 / Nacos 动态路由——均已在 M5.2~M5.5 完成，见上方 M5 能力表与补强历程。）

### 里程碑 M6 — 工程化
- ✅ spotless + license 头统一（内联 Apache-2.0 头 + import 顺序 + 去多余空白 + 去未用 import；
  verify 阶段 check 强制校验；apply 一键格式化。license 头用 content 内联避免跨模块路径问题）
- ✅ 根 README（特性/技术栈/快速开始/模块总览表/各模块配置与用法示例/构建发布说明；随模块扩展持续同步）
- ✅ 发布配置（release profile：source/javadoc/gpg，默认不激活，`mvn deploy -Prelease` 触发；
  gpg 本地未缓存，仅发布时需要，不影响日常构建）
- ✅ .gitignore

### M6 收尾补记（M5.x 大量新增模块后的工程化回归）
- ⚠️ 发现尾巴：M6 之后新增的模块/类（api-doc 增强、observability、sentinel、loadbalancer、gateway 等）从未跑过 spotless，
  且 spotless-check 绑在 verify 阶段、日常 `mvn test` 触发不到，导致 api-doc 有 1 处格式违规长期未暴露。
- ✅ 全量 `spotless:apply` 修复所有新代码格式；`mvn -DskipTests verify` 确认 spotless-check 零违规；Nacos 启动兜底归并后 29 模块 `mvn clean test` 再次确认 spotless-check 零违规。
- ✅ 回归 `clean test`：格式化仅调整排版、不改逻辑，全模块测试全绿。
- 备注：README「13 个模块」表述已随 M5.x/M5.13 扩展并最终收敛为 29 模块（含 6 个 cloud 模块），模块总览与使用文档均已同步更新。
- ✅ 根治流程隐患：spotless-check 执行阶段从 `verify` 前移到 `process-test-classes`，使日常 `mvn test`
  即触发格式校验（不再潜伏到 verify/发布才炸）；`mvn compile` 不受影响、不拖慢纯编译。已验证 test 阶段
  各模块先跑 spotless-check 再跑用例，全量 clean test 绿。

### BaseController 支持带鉴权 admin 场景（admin 反馈：三个控制器全自写、BaseController 用不上）
- ✅ 采纳 A+B+C。A 权限：端点方法保持 public 可覆盖，子类 @Override 加 @SaCheckPermission 后 super.xxx() 复用逻辑（不发明新机制、不绕开 Sa-Token 注解体系）。
- ✅ B 分页过滤：新增 `buildQueryWrapper(PageQuery)` 钩子（默认 null 无条件），BaseService/Impl 加 `page(query, wrapper)` 重载。
- ✅ C 写操作钩子：save/update/delete 各加 before/after 模板钩子（默认空实现），塞密码加密/查重/事务内分配角色等；需事务在子类覆盖端点上加 @Transactional。
- 未选「父类统一 StpUtil.checkPermission 入口」方案：会绕开注解体系、与 admin 既有 @SaCheckPermission 风格割裂。
- 文档写明定位：标准 CRUD + 可插拔鉴权/过滤/钩子用 BaseController；写逻辑极重且端点非标准的自写控制器。

### Token 续期（用户提问：Sa-Token 没有刷新 token 吗）
- 厘清：Sa-Token 是「续期」机制（不换 token，延长有效期），非 OAuth2 双令牌；后台管理用续期即可，不引 sa-token-oauth2。
- ✅ LoginHelper 补续期/超时方法：getTokenInfo/getTokenTimeout/getTokenActiveTimeout/renewTimeout/updateLastActiveToNow（薄封装 StpUtil，与现有风格一致）。
- ✅ README 说明 timeout/active-timeout/auto-renew 配置与「续期 vs OAuth2 refresh」区别；活跃用户开 auto-renew 自动续，一般无需手动。

### 全局登录拦截器（admin 建议：security 自动注册 SaInterceptor 消费 excludes）
- ✅ security 新增 `SaTokenWebConfigurer`（WebMvcConfigurer 注册 SaInterceptor 做全局登录校验），消费 `ypbin.security.includes/excludes`；`SecurityProperties` 补 interceptor/includes/excludeApiDoc 开关。
- ✅ 检测到 SpringDoc 时自动放行 Swagger/doc.html/v3/api-docs/webjars 等文档路径（Class.forName 探测，不硬依赖）。
- ✅ `@ConditionalOnWebApplication(SERVLET)` + `@ConditionalOnClass(SaInterceptor)` + `@ConditionalOnMissingBean` 守卫，spring-webmvc optional；业务方自定义 WebMvcConfigurer 或 `interceptor=false` 可覆盖/停用。admin 的自建 SaTokenConfigurer 可删。
- 核实：admin 建议的 TreeUtils「tools 里没有树形工具」是找错模块——core 早有 `tree/TreeUtils`（含 flatten/getDescendantIds/findNode），不重复做。

### 基类与当前用户增强（用户提问：BaseEntity 加 id/逻辑删除/租户，如何获取当前登录人）
- ✅ BaseEntity（去泛型，id 固定 Long）：主键 id（`@TableId(type=ASSIGN_ID)` 雪花 + `@JsonSerialize(ToStringSerializer)` 序列化为字符串防精度丢失）+ 审计字段 + 逻辑删除 `isDeleted`（列 `is_deleted`，`@TableLogic` 默认规则开箱生效）。data 补 jackson-databind optional 依赖。
- ✅ 租户实体基类 `TenantBaseEntity extends BaseEntity` 放 tenant 模块（不污染基础 BaseEntity）：多一个 `tenantId` 字段，隔离仍由行拦截器自动追加 SQL 条件。
- ✅ security 新增 `UserContext` 当前用户门面 + `LoginUser` 值对象：登录时 `setLoginUser` 存 Sa-Token 会话，`getLoginUser/getUserId/getUsername/getTenantId/getAttribute` 任意层读取；LoginHelper 仍只管 ID。
- 决策（用户定）：实体去泛型、id 锁定 Long（对齐 blade/continew，覆盖 99% 场景，最简洁）；逻辑删除列名 `is_deleted`；id 单独序列化为字符串；补完整 LoginUser 而非逐字段散取。

### 控制器基类分层 + 实体状态字段（用户：BaseController 太复杂、参考 blade/continew 取舍）
- ✅ 控制器拆两层：`BaseController` 轻量辅助基类（request/header/param/ip/file、当前用户、ok/data/success/fail/status，不声明路由，对齐 BladeController 定位）；`CrudController` 承担标准 CRUD 路由（get/list/page/save/update/delete）。
- ✅ BaseController 当前用户方法（userId/username/tenantId/isLogin）用反射弱耦合读取 security 的 UserContext，未引 security 或未登录返回空，不强依赖。extension-crud 补 servlet-api optional。
- ✅ BaseEntity 增 `status` 业务状态字段（默认 1 正常、0 禁用），与 `isDeleted` 逻辑删除分离不混用。
- 决策（用户定）：BaseController 做通用辅助而非重 CRUD 父类，大胆重构；标准且轻量用 CrudController，业务重/端点特殊继承 BaseController 自写。

### 登录客户端管理运行时（用户提问：客户端管理、token 有效期、登录限制）
- ✅ security 新增 client 包：`LoginClient`（clientId/secret/type/authTypes/timeout/activeTimeout/concurrent/share/maxLoginCount/replacedRange/overflowLogoutMode/enabled）+ `LoginClientProvider` 扩展点 + `DefaultLoginClientProvider`（读 ypbin.security.clients 配置）+ `DefaultLoginClientService`（校验存在/启用/密钥/authType，按客户端策略构建 SaLoginParameter 登录）+ `LoginClientHolder` 静态门面。
- ✅ LoginHelper 增 `login(userId, LoginClientRequest/clientId/authType/deviceId)` 按客户端策略登录；LoginUser/UserContext 增 clientId/clientType/authType。
- ✅ 边界：starter 只做运行时抽象 + 配置文件默认实现，不内置 sys_client 表和页面；admin 有客户端管理表时实现 LoginClientProvider 从 DB 接管即可。
- ✅ 操作日志跟进：LogRecord 增 clientId/clientType/authType，Include 增 CLIENT（入默认采集集），新增 `LogClientProvider` 扩展点（log 不依赖 security）；security 侧 `SecurityLogClientAutoConfiguration` 桥接从 UserContext 填充，模式对齐 SecurityAuditorAutoConfiguration。

### 应用签名能力增强：AK/SK + 过期 + 应用来源扩展（用户提问：应用管理 Access Key/Secret Key/到期时间）
- ✅ 厘清定位：这是「机器对机器的开放 API 签名」，非「人登录的客户端」；核心运行时 ypbin-starter-sign 早已有（SignChecker/SignClient/@ApiSign/nonce 防重放），只缺 AK/SK 命名、应用过期、DB 来源。
- ✅ 对外契约变更（用户定）：`appId/appSecret` 全量改名 `accessKey/secretKey`——SignChecker/SignClient/SignGenerator（含 MD5 拼接串 `&secretKey=`）/SignProperties.AppInfo/@ApiSign 文档/测试同步；四件套变为 accessKey+timestamp+nonce+sign。
- ✅ AppInfo/SignApp 增 `expireTime`（空=永不过期）+ `enabled`；SignChecker 校验应用禁用/过期。
- ✅ 新增 `SignAppProvider` 扩展点 + `DefaultSignAppProvider`（读 ypbin.sign.apps 配置），SignChecker 改为经 provider 按 accessKey 取应用；admin 有 sys_app 表时实现 provider 从 DB 加载（密钥加密存）即可覆盖。
- ✅ 边界：starter 只做签名运行时 + 配置默认实现，不内置 sys_app 表/页面/AK-SK 生成；生成/重置密钥、到期管理归 admin。SignCheckerTest 5（provider/禁用/过期/错误密钥/正常）。

### 密码安全策略：复杂度校验 + 错误锁定（用户提问：密码错误锁定阈值/锁定时长/密码有效期）
- ✅ 厘清分工：continew 把密码策略全放 admin（绑 sys_option 表），我们比它多下沉一层——能力+扩展点进 starter，配置项管理和登录编排留 admin。
- ✅ security 新增 password.policy 包：`PasswordPolicy`（minLength/maxLength/requireDigit/Letter/Uppercase/Lowercase/Symbol/allowContainUsername/errorLockCount/lockMinutes/expirationDays/expirationWarningDays/historyCount）+ `PasswordPolicyProvider` 扩展点 + `DefaultPasswordPolicyProvider`（读 ypbin.security.password）+ `PasswordValidator` 复杂度校验器 + `PasswordCheckResult`。
- ✅ security 新增 password.lock 包：`PasswordAttemptLimiter`（按 账号:IP 维度计数，达阈值抛 AccountLockedException）+ `PasswordAttemptStore` 扩展点，Redis（Lua 原子 INCR + 首次 EXPIRE）/ 内存两实现，锁定时长即计数键 TTL、到期自动解锁，参照 sign 的 NonceStore 双实现模式。
- ✅ SecurityProperties 复用 PasswordPolicy 作为 password 配置块；SecurityAutoConfiguration 装配 policyProvider/validator/expiration/attemptStore/attemptLimiter（均 ConditionalOnMissingBean，Redis 存在时用 Redis store）。security pom 补 spring-boot-starter-data-redis optional。
- ✅ 复盘补强 6 点（用户追问实时生效/解锁/遗漏）：①`unlock(identifier)` 按账号解锁全部维度（store 加 resetByPrefix，Redis 用 SCAN 非 KEYS）②`getLockStatus`/`isLocked` 锁定状态查询（LockStatus 记录）③账号标识小写归一，防大小写绕过 ④`recordFailure` 用递增返回值判定、达阈值本次即抛，消除读写分离竞态 ⑤新增 `PasswordExpiration` 密码有效期判定工具（isExpired/shouldWarn/remainingDays）⑥`PasswordValidator` 大小写要求已覆盖时不再冗余报"必须含字母"。策略每次实时读取，provider 走 DB 时后台改配置即时生效。
- ✅ 边界：starter 只做复杂度校验 + 错误锁定运行时 + 有效期判定 + 策略值；配置项落表/后台可改、密码有效期强制改密登录拦截编排、历史密码表归 admin。测试：PasswordValidatorTest 8 + PasswordAttemptLimiterTest 9 + PasswordExpirationTest 7，security 模块共 30 绿。

### 邮件配置动态化：从写死配置文件改为可后台配置（用户提问：邮件能不能像 continew 那样前端可配置）
- ✅ 厘清现状：原 MailAutoConfiguration 依赖 Spring 启动时按 spring.mail.* 构建的固定 JavaMailSender，改配置必须重启，前端改不了。
- ✅ messaging 新增 mail 配置能力：`MailConfig` 值对象（host/port/username/password/from/fromName/protocol/ssl/starttls/encoding/timeout + isConfigured/resolveFrom/fingerprint）+ `MailConfigProvider` 扩展点 + `DefaultMailConfigProvider`（绑 ypbin.mail.*）。
- ✅ MailService 改造：不再固定持有 sender，改为按 MailConfigProvider 动态构建 JavaMailSenderImpl，按配置指纹缓存、配置变化重建（缓存+刷新，非每次重建）；发件人取当前配置；新增 sendTest(to) 测试发送 + isConfigured()。
- ✅ MailAutoConfiguration 改为 @ConditionalOnClass(JavaMailSender)（引 starter-mail 即满足），装配 MailConfig(绑 ypbin.mail)/MailConfigProvider/MailService，均 ConditionalOnMissingBean。
- ✅ 边界（用户定 key 前缀 ypbin.mail、缓存+刷新、内置 test）：starter 给能力+扩展点+配置文件默认实现；SMTP 配置存表、后台页面、改完即时生效由 admin 实现 MailConfigProvider 从 DB 读接管。MailServiceTest 5，messaging 模块共 22 绿。

### 能力盘点后补齐四项（用户：全项目盘点缺什么，补短信/存储动态化/数据字典/在线用户）
- 盘点结论：ypbin 32 模块横比 continew-starter(22) 更全（多 cloud 全家桶/sign/social/sensitive-words/api-crypto）；缺的多为 admin 业务层。选定 starter 该补的 4 项通用运行时。
- ✅ 在线用户（security.online）：`OnlineUser` + `OnlineUserService`（基于 Sa-Token searchTokenValue 枚举，截 key 前缀取真实 token、过滤冻结、list/关键字过滤/按 userId/按 token 强制下线）+ `DefaultOnlineUserService` + `OnlineUserHelper`（登录时记录 IP/浏览器/OS 到 Token-Session 供列表展示）。表/页面归 admin。
- ✅ 存储动态化（storage.engine）：新增 `StorageConfigProvider` 扩展点 + `DefaultStorageConfigProvider`（读 ypbin.storage.*）+ `StorageStrategyRebuilder.rebuild()`；`StorageRouter` 加 `rebuild()` 原子全量刷新（retainAll+putAll，默认平台失效兜底）；两个 registrar 改从 provider 取配置。admin 存表实现 provider 后调 rebuild 即时增删源。StorageRouterTest 4。
- ✅ 数据字典（json.dict，放 json 与 @Sensitive 同款 ContextualSerializer 模式）：`DictItem`/`DictProvider` 扩展点/`DictCache`(本地缓存可刷新)/`@DictText`+`DictTextSerializer`(保留原字段原值、额外输出 xxxText 派生字段，严守 no-field-mapping)/`DictUtils` 静态门面。DictCache 仅当业务方提供 DictProvider 时装配。DictTextTest 4。
- ✅ 短信（messaging.sms）：选定用 sms4j 聚合框架（一库统一阿里云/腾讯云等十几家）。`SmsService`(send/sendByTemplate/sendByConfig/sendBatch/isConfigured) + `DefaultSmsService`(委托 SmsFactory.getSmsBlend) + `SmsUtils` 静态门面 + `SmsAutoConfiguration`(@ConditionalOnClass SmsFactory)。dependencies 纳管 sms4j 3.3.5，messaging 加 sms4j optional 依赖。关键决策：动态配置直接用 sms4j 原生 SmsReadConfig 钩子（admin 实现从 DB 读），不再包平行 provider，避免配置翻译。messaging 模块共 22 绿。
- 分层一致：四项均 starter 给运行时+扩展点+默认实现，表/页面/DB 配置源归 admin。同时清理 .m2 中 45 个 continew 风格残留细分模块坐标。

### 引用翻译 @RefText：存 ID 展示中文名（用户提问：创建人 id 展前端中文名，注意批量翻译效率/缓存）
- 定位：对标 crane4j/easy-trans 的数据翻译，但不引重框架——复用已有 @DictText 同款机制泛化一层。@DictText 翻固定字典枚举、@RefText 翻动态实体引用（用户/部门表）。
- ✅ json.ref 包：`@RefText`(value+suffix) + `RefTextProvider`(**强制批量**扩展点 getNames(ids)→Map，从根源避 N+1) + `RefTextCache`(TTL+容量上限+空值哨兵防穿透+惰性清理) + `RefTextManager`(translate 走缓存/preload 未命中按类型合并一次批量回源/refresh) + `RefTextUtils` 静态门面 + `RefTextSerializer`(保留原值+额外输出 xxxName，严守 no-field-mapping) + `RefTextResolver`(反射扫描列表/分页/嵌套对象图，按类型分组批量预热，IdentityHashMap 防环+限深+字段元数据按类缓存)。
- ✅ 效率核心：列表序列化前调 refTextResolver.preload(list)，把整表 N 行 M 类型压成最多 M 次批量查询，序列化时全命中缓存零回源。测试实证 100 行 3 个创建人仅查 1 次。
- ✅ JacksonProperties 加 ref-text 配置(ttl-seconds 默认 300/max-size 默认 1万)；仅当业务方提供 RefTextProvider 时装配 RefTextManager+RefTextResolver 并 bind RefTextUtils，未接入安全退化不输出名称字段。RefTextTest 7（缓存命中/批量一次/100行零N+1/空值哨兵/刷新/退化）。
- 边界：starter 给运行时+缓存+批量预热+扩展点；用户/部门等数据源由 admin 实现 RefTextProvider（一条 IN 查询）。
- ✅ 零工作量增强（用户：业务工作量越少越好、学习成本越低越好）：`RefTextResponseAdvice`(ResponseBodyAdvice) 在响应序列化前自动预加载，业务零调用零注解即享列表零 N+1；`@RefTextIgnore` 方法/类级排除，`ypbin.json.ref-text.auto-resolve=false` 全局关。性能守门：RefTextResolver 加类级 `containsRefText` 布尔缓存，不含 @RefText 的响应瞬间跳过零遍历。自动装配用内嵌 @ConditionalOnClass(ResponseBodyAdvice)+ServletWeb 隔离，spring-webmvc optional，非 web 环境不受影响。RefTextTest 增至 9（类级判定/无关对象零遍历）。

### CrudController 默认权限前缀（admin 反馈 footgun：继承端点漏 @Override 挂注解即越权）
- ✅ 加 `permissionPrefix()` 钩子（默认 null=不校验，向后兼容），覆盖返回前缀（如 "system:user"）后六端点自动按 `前缀:list/add/edit/delete` 校验（get/list/page→list、save→add、update→edit、delete→delete）。
- ✅ `checkPermission` 反射调 `StpUtil.checkPermission`（弱耦合，crud 不依赖 sa-token；无 sa-token 静默跳过），无权限异常向上抛交全局处理器转 403。可与 @Override+@SaCheckPermission 精细控制共存。CrudControllerPermissionTest 3。
- 安全默认：受保护资源覆盖 permissionPrefix 一次搞定，杜绝逐端点挂注解漏挂越权。

### 定时任务 ypbin-starter-job（用户：admin 要定时任务，决定在 starter 做而非 admin 自拼）
- 调研结论：blade 无开源实现，continew 用 SnailJob（要独立部署 server 进程，重型）。选轻量自研：Spring TaskScheduler + 自维护 ScheduledFuture 注册表（不用 ScheduledTaskRegistrar，动态增删 API 太弱）+ 已有分布式锁防重。
- ✅ 新增第 33 个模块 job（注册进根 pom/dependencies/bom，async 后、app-web 前）。核心：`JobHandler`+`@YpbinJob(name)` 执行体（按名路由）、`JobContext`、`JobDefinition`(id/name/executor/cron或fixedRate/args/timeout/concurrentGuard)、`JobManager`(register/unregister/triggerNow/改cron重建，TaskScheduler+ScheduledFuture 注册表)、`JobExecutionListener`(onStart/Success/Error/Skip 回调扩展点，admin 落 sys_job_log)、`JobProperties`。
- ✅ 多实例防重：执行入口抢分布式锁（锁键带触发时间片 withNano(0)，避免长任务持锁挡下次触发；ttl=超时+5 或默认 3600），只有抢到的节点执行、其余回调 onSkip。`JobLockFactory` 反射桥接 tools 的 LockService（tools optional），无 tools 退化单机无锁。
- ✅ 边界：starter 只做内存调度运行时+执行体路由+防重+回调，**不持久化**；sys_job/sys_job_log 表、CRUD、页面由 admin 实现，通过 JobManager 同步内存调度。JobManagerTest 5（固定间隔触发+回调/立即执行/防重跳过/执行器缺失 onError/重复注册替换）。

### Gemini/admin 反馈的 bug 修复批次（外部审查 + admin 实跑发现，逐条核验后修）
- ✅ RefTextResolver 剪枝误杀嵌套集合：`containsRefText` 用 field.getType() 拿到 List/Map 判叶子、不深入泛型元素，导致含嵌套列表的 DTO 被误剪枝、preload 收集不到 → N+1 复活。修：新增 genericContainsRefText 沿 getGenericType() 拆 List/Map/数组/通配符元素类型递归。RefTextTest 补嵌套集合回归。
- ✅ DictTextSerializer 整型字段崩溃：非 String（Integer/Long）字典字段走 fallback 写死 String.class → ClassCastException 500。修：改 JsonSerializer<Object>+defaultSerializeValue，fallback 回退 property.getType()。
- ✅ 密码锁定时长缩水：计数 key TTL 只首次失败设一次，晚到的达阈值失败触发锁定后 key 很快过期 → 实际锁定远短。修：lua+内存实现达阈值时用满额锁定时长刷新 TTL，increment 签名加 threshold/lockDuration。
- ✅ 签名参数注入：SignGenerator.canonicalize 不 URL 编码，value 里的 &/= 可伪造规范串。修：key/value 均 URLEncoder.encode。
- ✅ 签名嵌套对象验签波动：SignChecker 用共享 mapper 序列化嵌套对象、key 序不定。修：内部专用 mapper 副本开 ORDER_MAP_ENTRIES_BY_KEYS。
- ✅ 签名重放真空期：Math.abs 允许未来时间戳 + nonce 固定 TTL → 未来时间戳下 nonce 早于时间戳失效前过期，留重放窗口。修：nonce TTL 按 requestTime 动态算（覆盖到时间戳有效期末尾）+ 拒绝超 5s 时钟偏移的未来时间戳。SignCheckerTest 补未来时间戳/重放回归。
- ✅ 无上下文线程安全取用户抛异常：getUserIdSafely/isLogin/UserContext.getAttribute 裸调 StpUtil，异步/定时任务线程抛 SaTokenContextException → AuditorProvider 异步审计填充崩。修：三处 try-catch SaTokenException 降级返回空/false。SafeAccessNoContextTest 3。
- ✅ 操作日志 userId 恒空：SecurityLogClientAutoConfiguration 漏桥接 LogUserProvider。修：补 securityLogUserProvider（LoginHelper::getUserIdSafely，与 AuditorProvider 同源）。
- ✅ 操作日志 location 恒空：LogCollector 无 IP→归属地实现。修：加 IpLocationResolver 扩展点（默认返 null 不绑 IP 库重依赖，业务接 ip2region 实现），采集时填充、解析异常不中断。
- ✅ 操作日志 browser/os 相同：直接塞原始 UA。修：hutool UserAgentUtil.parse 分别提取浏览器名+版本 / OS 名；log pom 显式声明 hutool-all 依赖。
- ✅ 桥接与默认实现注册竞态：security 桥接 LogUserProvider/LogClientProvider 用裸 @ConditionalOnMissingBean、与 log 默认空实现跨配置类竞态，实测 log 默认先注册使桥接被跳过 → userId/clientType 恒空。修：SecurityLogClientAutoConfiguration 加 @AutoConfigureBefore(LogAutoConfiguration)；SecurityAuditorAutoConfiguration 同步从 @Primary 改 @AutoConfigureBefore(DataAutoConfiguration)+@ConditionalOnMissingBean 统一模式（既修竞态又保 admin 可覆盖）。原则：跨自动配置类用 @ConditionalOnMissingBean 覆盖默认 Bean 必须配加载顺序注解。

### 缓存增强：getOrLoad 三重保护 + 多级缓存（用户提问：多级缓存/防击穿架构）
- ✅ `CacheService.getOrLoad(key,type,loader,ttl)`：缓存旁路回源回填，内置防击穿（Redis 短锁单飞 + double-check + 等待超时兜底）、防穿透（空值哨兵短 TTL）、防雪崩（TTL 0~10% 随机扰动）。
- ✅ 多级缓存 `MultiLevelCacheService`：L1 Caffeine + L2 Redis，读回填 L1、写删失效 L1；Redis Pub/Sub 跨实例失效广播（带实例标识忽略自广播），覆盖默认 CacheService。
- ✅ `@ConditionalOnClass(Caffeine)` + `ypbin.cache.multi-level.enabled` 守卫，caffeine optional；单体可关广播、微服务多副本开广播。
- ✅ CacheUtils 补 getOrLoad 静态门面；配置元数据、README（三重保护 + 多级缓存用法与一致性边界）、MultiLevelCacheServiceTest 4。
- 明确边界：多级缓存适合读多写少、可容忍秒级不一致；强一致数据不开。分层保持 cache 不依赖 tools（防击穿用自身 Redis SETNX）。

### 新增 SSE + 统一推送门面（用户提问：长连接/实时推送，免前端长轮询）
- ✅ messaging 补 SSE：`SseEmitterManager`（按用户多端连接注册表，完成/超时/异常自动摘除，防内存泄漏）+ 内置订阅端点 `SseSubscribeController`。
- ✅ 统一推送门面 `PushService`（sendToUser/broadcast/isOnline/onlineCount）+ `DefaultPushService`，屏蔽 SSE/WebSocket 通道差异，覆盖「未读提醒/扫码登录状态/大屏刷新」三场景。
- ✅ `SseAutoConfiguration`：`@ConditionalOnClass(SseEmitter)` + Servlet Web + `ypbin.sse.enabled`；spring-webmvc/servlet-api 均 optional，缺失不影响其它能力。
- ✅ 配置元数据、README（含 EventSource 前端示例、多实例扇出说明）、SseEmitterManagerTest 4。
- 明确边界：默认单实例内存连接表，多副本跨实例扇出需上层配 Redis Pub/Sub/MQTT，业务方覆盖 PushService 接入。

### 新增分布式锁（用户建议：定时任务防重 @Scheduled + 分布式锁）
- ✅ 决策：不单建 job 模块，分布式锁作为通用能力放 tools（复用其 AOP/Redis optional/SpEL 基建），定时任务防重 = `@Scheduled` 叠加 `@DistributedLock`。
- ✅ `LockService` 抽象 + `RedisLockService`（SET NX EX 加锁 + Lua 校验持有者释放，不误删他人锁）+ `InMemoryLockService`（单机兜底，过期可重抢、释放校验持有者）。
- ✅ `@DistributedLock` 注解 + 切面：SpEL 键、ttl 防死锁、waitTime/retryInterval 等待重试、SKIP/EXCEPTION 失败策略、唯一 owner + finally 释放。
- ✅ Bean 级 `@ConditionalOnClass(StringRedisTemplate)` 守卫，缺 Redis 自动退内存锁；均可被业务方覆盖。
- ✅ 测试：InMemoryLockServiceTest 5（含 unlock 不存在 key、持有者校验、过期重抢）+ DistributedLockAspectTest 3（真实 AOP 织入：授予执行释放/拒绝跳过/拒绝抛异常）。

### 新增 async 异步模块（用户建议：补线程池/异步能力，并提供静态工具）
- ✅ 新增 `ypbin-starter-async`：统一线程池 `ypbinTaskExecutor` + 调度器 `ypbinTaskScheduler`，可配核心/最大/队列/存活/拒绝策略/优雅停机/虚拟线程。
- ✅ 接管 `@Async`：`AsyncAnnotationAutoConfiguration` 启用 `@EnableAsync`，默认执行器指向统一线程池；`@Async` void 异常统一记录（原会静默丢失）。
- ✅ 复用 core 的 `TaskDecorator`：线程池自动挂载上下文透传装饰器，租户/用户/MDC 传播到异步线程。
- ✅ 提供 `AsyncUtils` 静态门面：提交(run/supply)、编排(then/combine/withFallback)、批量并发(supplyAll/mapAll/runAll)、等待(allOf/anyOf/joinAll/join 超时)、调度(schedule/固定速率/固定延迟)，业务方无需注入执行器。
- ✅ 已并入 `app-web`（进而 `app-cloud` 传递获得）；装配测试 3 + AsyncUtils 单测 10；全量 32 模块 clean test 绿。

### 工具类场景全面扩全（用户要求：工具类要充分挖掘组件能力、覆盖高频场景）
起因：抽查发现 ExcelUtils 仅 3 个方法等，工具类普遍偏薄，业务方仍需自研对接底层组件。逐个扩全（保留分寸，不为凑数过度封装）：
- ✅ ExcelUtils：3 → 11 方法 + SheetData。读（同步/指定 sheet/表头行/大文件分批流式）、写（单/指定/多 sheet、含/排除列）、导出（单/多 sheet）。
- ✅ AesUtils：4 → 13 方法。字节级加解密、Base64 字符串加解密、随机密钥生成/Base64 编解码、PBKDF2 口令派生、随机盐。
- ✅ Sm4Utils：ECB/CBC/GCM 三模式 + Hex 编解码 + 密钥/IV 生成（原仅 ECB）。
- ✅ Sm2Utils：追加 SM3withSM2 签名/验签、公私钥 Base64 还原（原仅加解密+密钥对生成）。
- ✅ RequestUtils：2 → 11 方法。request/response/IP/UA/单头/全部头/单参/全部参/method/URI/isAjax。
- ✅ TreeUtils：追加 flatten/getDescendantIds/findNode（并给 TreeNode 接口补 getChildren——原能 set 不能 get 是设计缺口）。
- ✅ 新增 RedisUtils：Redis 全能力静态工具（key/string/hash/list/set/zset 约 40 方法），与「与实现无关」的 CacheService/CacheUtils 分工——通用走 CacheService，Redis 专属走 RedisUtils。
- 判断「已够用不硬扩」：I18nUtil（message 翻译已覆盖）、PasswordEncoderUtil（BCrypt 编码/校验/取器三法完整）、CacheUtils/CacheService（通用契约保持极简）。
- ✅ 新增单测：AesUtils 5 / Sm4Utils 4 / Sm2Utils 3 / TreeUtils +3；当时全量 29 模块 clean test 绿。

### Service/Provider 静态门面排查（用户要求：检查是否需为 Service/Provider 补静态工具）
逐一核实 20 个 Service/Provider/Helper/基类，按「框架实现、业务调用 + 依赖简单 + 非注入场景高频」筛选，不为对称性凑数：
- ✅ 新增 `SensitiveWordUtils`（委托 SensitiveWordService）：敏感词校验常在校验工具/DTO 自校验里静态调用。
- ✅ 新增 `MailUtils`（委托 MailService）：发邮件常在异步任务/工具方法里触发。
- ❌ 不加：9 个 Provider（ApiCrypto/Auditor/InnerInterceptor/GatewayAuth/LogUser/Permission/SensitiveWord/AuthRequest/Tenant）是给业务方实现的 SPI 扩展点，静态工具无意义。
- ❌ 不加：CaptchaService/SocialService（仅接口层用、必依赖请求上下文）、FileStorageService（多平台路由依赖复杂，静态门面会掩盖「选哪个平台」语义）、BaseService/BaseServiceImpl（继承基类）、RedisCacheService（已有 RedisUtils）。
- 已有：CacheService → CacheUtils（早前完成）。

### README 工具用法同步
- ✅ 将扩全/新增的工具用法补进 README：cache（RedisUtils 结构操作示例）、tools（AesUtils 密钥派生/字节级、SM4 三模式、SM2 签名验签）、excel（多 sheet/分批流式/列筛选）、messaging（MailUtils 静态门面）、sensitive-words（SensitiveWordUtils 静态门面）。
- ✅ 代码块闭合校验通过（142 处成对）。

### MQTT 订阅补全 + 单测（用户发现 MQTT 只有发布无接收）
- ⚠️ 发现缺口：messaging 模块 MQTT 原仅 `MqttPublisher`（发布），订阅甩给业务方自行拿 IMqttClient subscribe，违背「省去对接成本」初衷。
- ✅ 新增 `MqttSubscriber`：主题→回调订阅（含通配符）、指定 QoS、取消订阅；内部登记订阅，断线重连后经 `MqttCallbackExtended.connectComplete(reconnect)` 自动恢复（Paho 重连丢订阅是常见踩坑，透明处理）。
- ✅ MqttAutoConfiguration 装配 `MqttSubscriber` 并挂载重连回调。
- ✅ 补齐真正的业务消费入口：新增 `MqttMessageHandler` + `MqttMessageHandlerRegistrar`，业务方声明 Spring Bean 即可自动订阅并进入 `handle(topic, payload)` 回调；`MqttSubscriberTest` 已补实际消息到达分发断言。
- ✅ messaging 模块补 `spring-boot-starter-test`（原无测试）：MqttPublisherTest / MqttSubscriberTest / MqttMessageHandlerRegistrarTest，mock IMqttClient 验证发布参数、订阅注册、消息分发、重订阅、异常包装。
- ✅ README 补 MQTT 消费回调用法；Nacos 启动兜底归并后全量 29 模块 clean test 绿。

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
MQTT 用 Paho 直连而非 spring-integration（更轻）；负载均衡/灰度已重新归入 Cloud 后续增强，不在 M10 范围内。
全量 24 模块 BUILD SUCCESS，61 测试全绿。

### 单元测试（M6 补充）
- ✅ 引入 junit-jupiter + assertj（test scope）；27 个核心逻辑单测全绿
  （TreeUtils / SensitiveType / XssCleaner / AesFieldEncryptor / 限流并发 / 幂等并发）
- ✅ .gitattributes 统一行尾为 LF

## 6. 防侵权基线

- 不复制任一参考项目的源码；类/方法自行设计命名与实现。
- 不沿用任何参考项目的品牌前缀、不复用其包名。
- License 采用自选协议（默认 Apache-2.0，待用户确认）。
- 借鉴的是「架构模式与通用做法」，非具体代码文本。

## 7. 已确认决策

- ✅ groupId / 根包：`cn.ypbin.starter`
- ✅ 开源协议：Apache-2.0
- ✅ 范围已扩展到 M10 全部完成 + M5.1~M5.13 Cloud 全面补强 + M6 工程化收尾（spotless 前移根治）
- ✅ 微服务层 L3：Cloud 企业级治理（Gateway 横切 + 启动增强 + 灰度负载均衡 + Feign 容错 + API 文档增强/聚合 + Nacos 动态路由 + 可观测性 + Sentinel 流量防护）已完成；其中 Gateway/Nacos/Feign/Sentinel 已真机验证
- ✅ 构建验证：IntelliJ 内置 JBR + Maven3；全量 29 模块 `clean test` 绿；`-Pit` 集成测试连公网服务器/Testcontainers 真机验证
- ⏳ 后续可选：CI 接入 `-Pit`（GitHub Actions + Testcontainers）；v1.0.0 正式发布；示例工程

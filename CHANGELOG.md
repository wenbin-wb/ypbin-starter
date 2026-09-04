# 更新日志

本项目遵循[语义化版本](https://semver.org/lang/zh-CN/)：`主版本.次版本.修订号`。
- 主版本：不兼容的 API 变更
- 次版本：向后兼容的功能新增
- 修订号：向后兼容的问题修复

格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)。

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

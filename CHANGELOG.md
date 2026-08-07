# 更新日志

本项目遵循[语义化版本](https://semver.org/lang/zh-CN/)：`主版本.次版本.修订号`。
- 主版本：不兼容的 API 变更
- 次版本：向后兼容的功能新增
- 修订号：向后兼容的问题修复

格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)。

## [未发布]

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

[未发布]: https://github.com/wenbin-wb/ypbin-starter/compare/v1.2.0...HEAD
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

[未发布]: https://github.com/wenbin-wb/ypbin-starter/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/wenbin-wb/ypbin-starter/releases/tag/v1.0.0

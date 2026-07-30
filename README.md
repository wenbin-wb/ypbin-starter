# ypbin-starter

一套基于 Spring Boot 3.5 的开箱即用基础能力 starter 集合。参考业界成熟脚手架的架构思想，
按「约定优于配置、按需引入、可覆盖可扩展」的原则重构，面向企业级生产环境。

## 特性

- **分层架构**：基础层（core/json/web/data/cache/security 等）单体与微服务共用，扩展层（crud/tenant/datapermission）按需引入。
- **约定优于配置**：统一 `ypbin.*` 配置前缀，默认自动装配，零配置即可用。
- **可覆盖可扩展**：能力 Bean 全部 `@ConditionalOnMissingBean` 可覆盖，`@ConditionalOnProperty` 可开关；模块间通过扩展点接口解耦。
- **企业级细节**：多租户跨租户逃逸、异步上下文透传、分布式限流与幂等、数据权限门控、全局异常统一、审计字段自动填充、XSS 防护、字段加密、数据脱敏。
- **能力齐全**：Excel 导入导出、行为验证码、邮件、国密 SM2/SM4、雪花 ID、树形结构工具开箱即用。
- **版本治理**：`${revision}` + flatten 统一版本，对外提供 BOM 一键导入。
- **质量保障**：spotless 统一代码风格 + license 头，核心逻辑单元测试覆盖。

## 技术栈

| 项 | 版本 |
|---|---|
| JDK | 17 |
| Spring Boot | 3.5.16 |
| 认证 | Sa-Token 1.45.0 |
| ORM | MyBatis-Plus 3.5.17 |
| 缓存 | Redis（Spring Data Redis） |
| 对象存储 | AWS SDK v2（S3 兼容） |
| API 文档 | SpringDoc OpenAPI 2.8.17 |
| Excel | FastExcel 1.3.0 |
| 验证码 | tianai-captcha 1.5.5（滑块/旋转/点选/拼接） |
| 加解密 | AES-GCM / 国密 SM2·SM4（BouncyCastle 1.85） |
| 邮件 | Spring Mail |

## 快速开始

### 1. 引入 BOM 统一版本

在你的项目 `pom.xml` 的 `dependencyManagement` 中导入：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>cn.ypbin.starter</groupId>
            <artifactId>ypbin-starter-bom</artifactId>
            <version>1.0.0-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### 2. 按需引入模块（无需再写版本号）

```xml
<dependencies>
    <dependency>
        <groupId>cn.ypbin.starter</groupId>
        <artifactId>ypbin-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>cn.ypbin.starter</groupId>
        <artifactId>ypbin-starter-data</artifactId>
    </dependency>
</dependencies>
```

引入即自动装配，无需额外注解。

## 模块总览

| 模块 | artifactId | 职责 | 配置前缀 |
|---|---|---|---|
| 核心 | `ypbin-starter-core` | 统一响应 R、异常体系、通用枚举、SpringUtils、上下文透传、树形工具 | — |
| JSON | `ypbin-starter-json` | Jackson 统一序列化（时间格式、大数字转字符串）、`@Sensitive` 脱敏 | `ypbin.json` |
| Web | `ypbin-starter-web` | 全局异常处理、CORS、404 统一 JSON、XSS 过滤、可重复读请求 | `ypbin.web` |
| 数据 | `ypbin-starter-data` | MyBatis-Plus 增强、审计填充、拦截器编排、字段加密、雪花 ID | `ypbin.data` |
| 缓存 | `ypbin-starter-cache` | Redis 缓存（CacheService 策略接口） | `ypbin.cache` |
| 安全 | `ypbin-starter-security` | Sa-Token 封装（登录、权限数据源扩展点）、密码编码器 | `ypbin.security` |
| API 文档 | `ypbin-starter-api-doc` | SpringDoc OpenAPI 元信息配置 | `ypbin.api-doc` |
| 存储 | `ypbin-starter-storage` | 本地 + S3 兼容对象存储，多源路由 | `ypbin.storage` |
| 日志 | `ypbin-starter-log` | `@Log` 操作日志 AOP + 全量访问日志拦截器 | `ypbin.log` |
| 工具 | `ypbin-starter-tools` | 分布式限流 `@RateLimit`、幂等 `@Idempotent`、AES/国密加解密 | `ypbin.tools` |
| Excel | `ypbin-starter-excel` | 基于 FastExcel 的注解驱动导入导出 | — |
| 验证码 | `ypbin-starter-captcha` | 行为验证码（滑块/旋转/点选/拼接） | `ypbin.captcha` |
| 消息 | `ypbin-starter-messaging` | 邮件、WebSocket（STOMP）、MQTT（Paho） | `ypbin.websocket` / `ypbin.mqtt` |
| 敏感词 | `ypbin-starter-sensitive-words` | Hutool DFA 敏感词检测/替换，可插拔词库 | `ypbin.sensitive-words` |
| 国际化 | `ypbin-starter-i18n` | Spring MessageSource 多语言，参数/头解析 Locale | `ypbin.i18n` |
| 接口加解密 | `ypbin-starter-api-crypto` | `@ApiEncrypt` 请求解密/响应加密（Advice） | `ypbin.api-crypto` |
| 接口签名 | `ypbin-starter-sign` | `@ApiSign` 四件套验签、防重放、MD5/HMAC 可配 | `ypbin.sign` |
| 第三方登录 | `ypbin-starter-social` | JustAuth OAuth 登录，按平台可插拔 | `ypbin.social` |
| 多租户 | `ypbin-starter-extension-tenant` | 行级租户隔离、`@TenantIgnore` 跨租户逃逸 | `ypbin.tenant` |
| CRUD | `ypbin-starter-extension-crud` | 通用控制器/服务基类，防 Over-Posting | — |
| 数据权限 | `ypbin-starter-extension-datapermission` | 行级数据范围过滤、`@DataPermission` 门控 | `ypbin.data-permission` |

详细用法见 [各模块使用文档](#各模块使用文档)。

## 各模块使用文档

### core — 核心

所有模块的底座，通常由其它模块传递引入，无需单独声明。提供：

- `R<T>`：统一响应体。`R.ok(data)` / `R.fail(code, msg)`。
- `BaseException` / `BusinessException` / `GlobalErrorCode`：异常体系，业务异常抛 `BusinessException`。
- `BaseEnum<V>`：通用枚举契约（value + description）。
- `SpringUtils`：静态获取 Bean / 发布事件 / 读配置。
- `ContextPropagator` + `ContextAwareTaskDecorator`：异步上下文透传（见下）。

**异步上下文透传**：把主线程的租户、用户、MDC 等上下文带入 `@Async` 子线程。将 core 提供的
`TaskDecorator` 设置到你的线程池即可：

```java
@Bean
public ThreadPoolTaskExecutor taskExecutor(TaskDecorator contextAwareTaskDecorator) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setTaskDecorator(contextAwareTaskDecorator);
    executor.initialize();
    return executor;
}
```

各模块（如 tenant）自行注册 `ContextPropagator` Bean，无需你手动列举要透传的内容。

**树形结构工具** `TreeUtils`：菜单、部门、分类等实现 `TreeNode` 接口后，一行代码把扁平列表组装成树（O(n)）：

```java
public class MenuNode implements TreeNode<MenuNode, Long> {
    private Long id;
    private Long parentId;
    private List<MenuNode> children;
    // getId / getParentId / setChildren ...
}

List<MenuNode> tree = TreeUtils.build(flatList);        // 自动识别根节点
List<MenuNode> tree2 = TreeUtils.build(flatList, 0L);   // 指定根父 ID
```

### web — Web 层

引入即生效，无需注解：

- 全局异常处理：业务/校验/系统异常统一转 `R`，**所有异常返回 HTTP 200**，由 `R.code` 区分。
- 404 统一 JSON：访问不存在的接口返回 `R.fail(404, "接口不存在")`，而非默认 HTML 错误页（默认已开启 `throw-exception-if-no-handler-found`）。
- CORS：默认关闭，按需开启：

```yaml
ypbin:
  web:
    cors:
      enabled: true
      allowed-origin-patterns: ["https://*.example.com"]
    xss:
      enabled: true                 # XSS 过滤默认关闭，按需开启
      excludes: ["/webhook/**"]     # 放行路径（不做清洗）
```

XSS 过滤开启后自动清洗请求参数中的脚本注入（`<script>`、`javascript:`、`on事件` 等），转义而非删除正常内容。

**可重复读请求**：Servlet 请求体默认只能读一次。开启后以最高优先级包装请求，缓存 body 供签名校验、日志、Controller 等多方重复读取，解决"body 被上游读走后下游读空"。签名模块依赖它：

```yaml
ypbin:
  web:
    repeatable-read:
      enabled: true    # 启用接口签名时需一并开启
```

文件上传（multipart）不缓存，避免大文件占用内存。

### data — 数据访问

MyBatis-Plus 增强，引入即生效：

- 分页拦截器（默认单页上限 500 条，可配 `ypbin.data.max-limit`）。
- 实体继承 `BaseEntity` 即获得 `createUser/createTime/updateUser/updateTime` 审计字段，INSERT/UPDATE 自动填充。
- 操作人来源：实现 `AuditorProvider` 扩展点（引入 security 模块后自动对接登录用户）。
- 拦截器编排：多租户、数据权限等通过 `InnerInterceptorProvider` 按 order 贡献内部拦截器，顺序可控（租户/数据权限先于分页）。

```java
public class Article extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String title;
}
```

**雪花 ID** `IdGenerator`：主动获取分布式唯一 ID（提前生成主键、订单号等）：

```java
long id = IdGenerator.nextId();
String idStr = IdGenerator.nextIdStr();
```

**字段加密**：敏感字段存库自动加密、读库自动解密，对业务透明。配置密钥后在字段上挂 TypeHandler：

```yaml
ypbin:
  data:
    encrypt:
      key: 1234567890abcdef   # AES 密钥，16/24/32 字节
```

```java
@TableField(typeHandler = EncryptTypeHandler.class)
private String idCard;   // 入库密文，查询回来自动解密
```

默认 AES-GCM 实现；需要国密/KMS 时实现 `FieldEncryptor` 覆盖默认 Bean。

### json — 序列化

统一 Jackson 配置，引入即生效：

- `LocalDateTime/LocalDate/LocalTime` 统一格式（默认 `yyyy-MM-dd HH:mm:ss` 等）。
- Long/BigInteger/BigDecimal 序列化为字符串，规避前端 JS 大数精度丢失。
- 反序列化忽略未知字段。

```yaml
ypbin:
  json:
    date-time-format: yyyy-MM-dd HH:mm:ss
    write-big-number-as-string: true   # 默认 true
```

**数据脱敏** `@Sensitive`：响应字段序列化时自动打码，不改动库中原值：

```java
@Sensitive(SensitiveType.PHONE)
private String phone;      // 输出 138****8000

@Sensitive(SensitiveType.ID_CARD)
private String idCard;     // 输出 110101********1234

@Sensitive(value = SensitiveType.CUSTOM, prefixKeep = 2, suffixKeep = 2)
private String custom;     // 保留前 2 后 2
```

内置类型：`CHINESE_NAME` / `PHONE` / `ID_CARD` / `EMAIL` / `BANK_CARD` / `ADDRESS` / `ALL` / `CUSTOM`。

### cache — 缓存

基于 Redis 的 `CacheService` 统一缓存接口，值以 JSON 存储：

```java
@Autowired
private CacheService cacheService;

cacheService.set("user:1", user, Duration.ofMinutes(30));
User user = cacheService.get("user:1", User.class);
```

需要多级缓存/本地缓存时，实现 `CacheService` 覆盖默认 Bean 即可。

### security — 认证授权

基于 Sa-Token 封装：

- `LoginHelper`：`login(userId)` / `getUserId()` / `logout()`，统一以 `Long` 用户 ID 进出。
- 权限数据源：实现 `PermissionProvider` 提供用户的权限码与角色码，框架自动适配为 Sa-Token 的 `StpInterface`，无需直接依赖 Sa-Token API。

```java
@Component
public class MyPermissionProvider implements PermissionProvider {
    @Override
    public List<String> getPermissions(Object loginId, String loginType) {
        return permissionService.listByUserId(Long.valueOf(loginId.toString()));
    }
}
```

引入本模块后会自动对接 data 的审计字段（用当前登录用户填充 createUser/updateUser）。

**密码编码器** `PasswordEncoderUtil`：BCrypt 加密（自带随机盐），校验用 `matches` 而非比较密文：

```java
String hash = PasswordEncoderUtil.encode(rawPassword);
boolean ok = PasswordEncoderUtil.matches(rawPassword, hash);
```

### api-doc — API 文档

SpringDoc OpenAPI 开箱即用，配置文档元信息：

```yaml
ypbin:
  api-doc:
    title: 订单服务 API
    version: 1.0.0
    contact:
      name: wenbin
      email: dev@example.com
```

启动后访问 `/swagger-ui.html`。

### storage — 文件存储

本地 + S3 兼容对象存储（阿里云 OSS / 腾讯云 COS / MinIO / 七牛等），支持多存储源共存、按 platform 路由：

```yaml
ypbin:
  storage:
    default-platform: local-disk
    local:
      - platform: local-disk
        base-path: /data/files
        domain: https://cdn.example.com
    oss:
      - platform: aliyun
        endpoint: https://oss-cn-hangzhou.aliyuncs.com
        bucket: my-bucket
        access-key: ${OSS_AK}
        secret-key: ${OSS_SK}
```

```java
@Autowired
private FileStorageService fileStorageService;

FileInfo info = fileStorageService.upload(inputStream, "a.png")
    .platform("aliyun")       // 不指定则用默认平台
    .path("images/")
    .execute();
```

扩展点：`StorageStrategy`（新增存储后端）、`FileProcessor`（上传前校验/改名/生成路径责任链）、`FileRecorder`（记录文件元数据）。S3 上传对未知大小的流会落临时文件规避 OOM，直链自动 URL 编码。

### log — 操作日志

`@Log` 注解 AOP 采集，**异步持久化**不阻塞业务：

```java
@Log(value = "创建订单", module = "订单", includes = Include.REQUEST_BODY)
@PostMapping("/orders")
public R<Void> create(@RequestBody OrderReq req) { ... }
```

- `Include` 控制采集粒度：请求头/体/参数、响应体、IP、浏览器、OS。默认只采集请求参数 + IP（不采集请求/响应体，防敏感信息与大报文落库）。
- 请求体从 AOP 入参序列化（能拿到 `@RequestBody` 的 JSON），过滤文件流等不可序列化参数。
- 持久化：实现 `LogDao` 落库（默认仅打印到日志）；操作人来源实现 `LogUserProvider`。
- 写日志通过事件 + `@Async` 异步执行，DB 抖动不影响主接口。

**全量访问日志**（与 `@Log` 互补，无需注解，记录所有请求的 URI/方法/状态/耗时/IP）：

```yaml
ypbin:
  log:
    access:
      enabled: true
      exclude-path-patterns: ["/actuator/**", "/static/**"]
```

`@Log` 精准采集业务操作（可落库），访问日志是全量流水（打印到日志），按需选用或并用。

### tools — 常用工具

**分布式限流** `@RateLimit`（有 Redis 时自动用 Redis+Lua 原子限流，否则内存限流）：

```java
@RateLimit(key = "#userId", window = 60, count = 5, message = "操作过于频繁")
public void sendSms(Long userId) { ... }
```

- `key` 支持 SpEL，可按用户等业务维度限流；留空则用方法全限定名。
- `byIp = true`（默认）时把客户端 IP 纳入限流键。
- 分布式版基于 `StringRedisTemplate` + Lua 脚本，多节点共享窗口。

**幂等** `@Idempotent`（防重复提交，有 Redis 用 Redis+Lua，否则内存）：

```java
@Idempotent(key = "#req.orderNo", interval = 10, message = "请勿重复提交")
public void create(OrderReq req) { ... }
```

同一幂等键在 `interval` 秒内的重复调用被拒绝；`key` 支持 SpEL，留空则用「方法 + 参数指纹」。

**AES 加解密** `AesUtils`：AES-GCM 认证加密，随机 IV 前置：

```java
String cipher = AesUtils.encrypt("secret", key);   // key 长度 16/24/32
String plain = AesUtils.decrypt(cipher, key);
```

**国密 SM4/SM2**（基于 BouncyCastle，合规场景）：

```java
// SM4 对称（16 字节密钥）
String c = Sm4Utils.encrypt("secret", "1234567890abcdef");
String p = Sm4Utils.decrypt(c, "1234567890abcdef");

// SM2 非对称
Sm2Utils.KeyPairBase64 kp = Sm2Utils.generateKeyPair();
String cipher = Sm2Utils.encrypt("secret", kp.publicKey());
String plain  = Sm2Utils.decrypt(cipher, kp.privateKey());
```

### extension-crud — 通用 CRUD

基类库，消除增删改查样板。为防 Over-Posting（前端恶意提交越权字段），控制器严格区分请求/响应/实体三类模型：

```java
// 服务层：继承 BaseServiceImpl，自动拥有 CRUD + 分页
@Service
public class ArticleService extends BaseServiceImpl<ArticleMapper, Article> { }

// 控制器：泛型 <实体, 主键, 请求, 响应>，REQ/RESP 与实体默认 BeanUtils 转换
@RestController
@RequestMapping("/articles")
public class ArticleController extends BaseController<Article, Long, ArticleReq, ArticleResp> {
    private final ArticleService service;
    @Override protected BaseService<Article> getBaseService() { return service; }
}
```

`save/update` 收 `REQ`、查询返回 `RESP`，实体永不直接暴露。简单场景可将 REQ/RESP 直接指定为实体类型；需精细映射时覆盖 `toEntity` / `toResp`（接 MapStruct 等）。分页用 `PageQuery` / `PageResult`。

### extension-tenant — 多租户

MyBatis-Plus 行级租户隔离。默认关闭，需显式开启并提供租户来源：

```yaml
ypbin:
  tenant:
    enabled: true
    column: tenant_id
    ignore-tables: [sys_config, sys_dict]   # 这些表不隔离
```

```java
@Component
public class MyTenantProvider implements TenantProvider {
    @Override
    public Optional<Long> getCurrentTenantId() {
        return LoginHelper.getUserIdSafely();  // 示例：从上下文取租户
    }
}
```

**跨租户逃逸**：超管全局查询、后台定时任务全表扫描时，用 `@TenantIgnore` 或 `TenantContext`：

```java
@TenantIgnore
public List<Tenant> listAllTenants() { ... }

// 或编程式
TenantContext.runIgnore(() -> statisticsMapper.countAll());
```

忽略标记会随异步上下文透传到 `@Async` 子线程。

### extension-datapermission — 数据权限

行级数据范围过滤。**仅对 `@DataPermission` 标注的方法生效**，避免全局无差别拦截导致定时任务/登录校验等内部查询数据缺失。需提供数据范围规则：

```yaml
ypbin:
  data-permission:
    enabled: true
```

```java
// 提供数据范围 SQL 片段（依赖业务，无默认实现）
@Component
public class MyDataScopeHandler implements DataScopeHandler {
    @Override
    public String getDataScopeSql(String mappedStatementId, String tableName) {
        return "dept_id IN (" + currentUserDeptIds() + ")";
    }
}

// 只有标注的方法才触发数据范围过滤
@DataPermission
public List<Order> listByScope(OrderQuery q) { ... }
```

### excel — 导入导出

基于 FastExcel，注解驱动。实体字段用 `@ExcelProperty` 标注列名：

```java
public class UserExcel {
    @ExcelProperty("用户名")
    private String username;
    @ExcelProperty("年龄")
    private Integer age;
}

// 导入
List<UserExcel> list = ExcelUtils.read(inputStream, UserExcel.class);

// 导出到 HTTP 响应（浏览器下载，文件名自动 UTF-8 编码）
ExcelUtils.export(response, "用户列表", UserExcel.class, list);
```

### captcha — 行为验证码

基于 tianai-captcha，支持滑块、旋转、点选、拼接，带行为轨迹校验。验证码状态由其自带缓存
（本地/Redis 自动切换）管理，一次性有效：

```java
@Autowired
private CaptchaService captchaService;

// 生成（默认滑块，也可传 CaptchaTypeConstant.ROTATE 等）
ApiResponse<?> data = captchaService.generate();   // 返回 id + 图片，前端渲染

// 校验：前端回传采集到的行为轨迹
boolean ok = captchaService.verify(id, track);
```

图片资源、二次校验等通过 tianai 自身的配置项调整。

### messaging — 消息（邮件 / WebSocket / MQTT）

**邮件**：基于 Spring Mail，配置好 `spring.mail.*` 后自动装配 `MailService`：

```java
@Autowired
private MailService mailService;

mailService.sendText("to@example.com", "标题", "正文");
mailService.sendHtml("to@example.com", "标题", "<h1>HTML 正文</h1>");
mailService.sendWithAttachments("to@example.com", "标题", "正文", false, new File("report.xlsx"));
```

发件人默认取 `spring.mail.username`。

**WebSocket（STOMP 实时推送）**：需引入 `spring-boot-starter-websocket` 并开启：

```yaml
ypbin:
  websocket:
    enabled: true
    endpoint: /ws
    broker-prefix: /topic
    heartbeat-server: 10000   # 服务端心跳(ms)，保活并探测半开连接
```

业务方注入 `SimpMessagingTemplate` 向客户端广播。**可靠性说明**：内置 SimpleBroker 为内存代理，服务重启消息丢失、不保证送达；生产需可靠投递时，自定义 `WebSocketMessageBrokerConfigurer` 接入 RabbitMQ/ActiveMQ 的 STOMP relay。

**MQTT（Paho）**：需引入 `org.eclipse.paho.client.mqttv3` 并开启：

```yaml
ypbin:
  mqtt:
    enabled: true
    url: tcp://127.0.0.1:1883
    default-qos: 1
    automatic-reconnect: true       # 断线自动重连
    max-reconnect-delay: 30000      # 重连退避上限(ms)
    max-inflight: 10                # QoS1/2 最大在途消息
    persistence-dir: /data/mqtt     # 文件持久化，重启不丢 QoS1/2 未确认消息（留空则内存）
```

```java
@Autowired
private MqttPublisher mqttPublisher;

mqttPublisher.publish("device/1/cmd", payload);          // 默认 QoS
mqttPublisher.publish("device/1/cmd", payload, 2, false); // 指定 QoS/retained
```

订阅用 `mqttPublisher.getClient()` 拿到 Paho 客户端自行 subscribe。

### sensitive-words — 敏感词过滤

基于 Hutool DFA，检测/替换敏感词。词库来源：配置静态词库，或实现 `SensitiveWordProvider` 从库/远程加载：

```yaml
ypbin:
  sensitive-words:
    words: [敏感词1, 敏感词2]
    replacement: '*'
```

```java
@Autowired
private SensitiveWordService service;

boolean hit = service.contains(text);
String clean = service.filter(text, '*');   // 命中词替换为等长 *
List<String> hits = service.findAll(text);
service.reload(newWords);                    // 词库热更新
```

### i18n — 国际化

基于 Spring MessageSource（配 `spring.messages.basename` 指定资源文件）。按请求参数（`?lang=en_US`）或请求头（`Accept-Language`）解析语言：

```yaml
ypbin:
  i18n:
    param-name: lang
    default-locale: zh_CN
```

```java
// 静态调用，按当前请求 Locale 翻译；args 为占位参数
String msg = I18nUtil.message("user.not.found");
String msg2 = I18nUtil.message("greeting", userName);
```

### api-crypto — 接口加解密

`@ApiEncrypt` 标注的接口自动对请求体解密、响应体加密，对 Controller 透明。基于 Spring MVC 的 RequestBody/ResponseBodyAdvice：

```yaml
ypbin:
  api-crypto:
    key: 1234567890abcdef   # AES 密钥；配置后装配默认 AES 实现
```

```java
@ApiEncrypt                       // 请求体解密 + 响应体加密
@PostMapping("/secure")
public R<Data> secure(@RequestBody Req req) { ... }

@ApiEncrypt(requestDecrypt = false)   // 仅加密响应
@GetMapping("/only-resp")
public R<Data> onlyResp() { ... }
```

默认 AES-GCM；实现 `ApiCryptoProvider` 可换国密 SM4 / RSA。返回 `R` 时仅加密其 data，保留统一结构。

### sign — 接口签名

对外提供给第三方对接的接口做签名校验（`appId + timestamp + nonce + sign` 四件套），防篡改与重放。**需同时开启 web 的可重复读请求**（见 web 章节）：

```yaml
ypbin:
  sign:
    enabled: true
    mode: ANNOTATION          # ANNOTATION（仅 @ApiSign 接口）或 GLOBAL（全局，按 skip-path 排除）
    algorithm: HMAC_SHA256    # 或 MD5（兼容旧系统）
    timeout: 60               # 签名有效期(秒)
    replay-protect: true      # nonce 防重放（有 Redis 用 Redis，否则内存）
    apps:
      - app-id: app-001
        app-secret: your-secret
        app-name: 合作方A
  web:
    repeatable-read:
      enabled: true           # 签名校验需读 body，必须开启
```

```java
@ApiSign                          // 该接口要求验签
@PostMapping("/open/order")
public R<Void> createOrder(@RequestBody OrderReq req) { ... }
```

**第三方对接方**用 `SignClient` 生成签名（算法需与服务端一致）：

```java
Map<String, String> signed = SignClient.sign(bizParams, "app-001", "your-secret", SignAlgorithm.HMAC_SHA256);
// signed 含 appId/timestamp/nonce/sign + 业务参数，随请求发送
```

### social — 第三方登录

基于 JustAuth 的 OAuth 登录。各平台的 appId/secret/回调由业务方持有，故为每个平台实现 `AuthRequestProvider` 注册授权请求，`SocialService` 按平台调度：

```java
@Component
public class GithubAuthProvider implements AuthRequestProvider {
    @Override public String getSource() { return "github"; }
    @Override public AuthRequest getAuthRequest() {
        return new AuthGithubRequest(AuthConfig.builder()
            .clientId("...").clientSecret("...").redirectUri("...").build());
    }
}
```

```java
@Autowired
private SocialService socialService;

String url = socialService.authorizeUrl("github");        // 生成授权跳转地址
AuthUser user = socialService.login("github", callback);  // 回调换取用户信息
```

## 构建与发布

```bash
# 编译并安装到本地仓库（verify 阶段自动执行代码风格校验）
mvn clean install

# 一键格式化代码（统一 license 头、import 顺序、去除多余空白）
mvn com.diffplug.spotless:spotless-maven-plugin:apply -pl <功能模块列表>

# 发布到远程仓库（生成 source/javadoc 附件并 GPG 签名，需本地配置 gpg 密钥）
mvn clean deploy -Prelease
```

## 许可证

[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)

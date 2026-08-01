# ypbin-starter

一套基于 Spring Boot 3.5 的开箱即用基础能力 starter 集合，覆盖单体应用与微服务架构。
参考业界成熟脚手架的架构思想，按「约定优于配置、按需引入、可覆盖可扩展」的原则重构，面向企业级生产环境。

## 特性

- **分层架构**：基础层（core/json/web/data/cache/security 等）单体与微服务共用，扩展层（crud/tenant/datapermission）按需引入。
- **约定优于配置**：统一 `ypbin.*` 配置前缀，默认自动装配，零配置即可用。
- **可覆盖可扩展**：能力 Bean 全部 `@ConditionalOnMissingBean` 可覆盖，`@ConditionalOnProperty` 可开关；模块间通过扩展点接口解耦。
- **企业级细节**：多租户跨租户逃逸、异步上下文透传、分布式限流与幂等、数据权限门控、全局异常统一、审计字段自动填充、XSS 防护、字段加密、数据脱敏。
- **微服务就绪**：Feign 请求头透传与 R 错误解码、CircuitBreaker 默认开启、版本灰度负载均衡、Nacos 注册/配置/动态路由、Gateway 横切（CORS/异常/身份头清洗/鉴权/Swagger 聚合）。
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
| 邮件 | Spring Mail（配置可动态化） |
| 微服务 | Spring Cloud 2025.0.3 + Gateway / OpenFeign / LoadBalancer |
| 注册配置 | Nacos（spring-cloud-alibaba 2025.0.0.0） |
| 熔断降级 | Resilience4j |
| 网关 | Spring Cloud Gateway（WebFlux） |

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

    <!-- 微服务架构 -->
    <dependency>
        <groupId>cn.ypbin.starter</groupId>
        <artifactId>ypbin-starter-cloud-gateway</artifactId>
    </dependency>
    <dependency>
        <groupId>cn.ypbin.starter</groupId>
        <artifactId>ypbin-starter-cloud-nacos</artifactId>
    </dependency>
    <dependency>
        <groupId>cn.ypbin.starter</groupId>
        <artifactId>ypbin-starter-cloud-core</artifactId>
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
| 数据 | `ypbin-starter-data` | MyBatis-Plus 增强、`BaseEntity`（主键/审计/状态/逻辑删除）、拦截器编排、字段加密、雪花 ID | `ypbin.data` |
| 缓存 | `ypbin-starter-cache` | Redis 缓存 + `getOrLoad` 防击穿/穿透/雪崩 + 多级缓存（L1 Caffeine + L2 Redis，跨实例失效广播） | `ypbin.cache` |
| 安全 | `ypbin-starter-security` | Sa-Token 封装（全局登录拦截、当前用户门面、权限数据源扩展点）、登录客户端策略、密码编码器与密码策略（复杂度/错误锁定） | `ypbin.security` |
| API 文档 | `ypbin-starter-api-doc` | SpringDoc OpenAPI 元信息配置 | `ypbin.api-doc` |
| 存储 | `ypbin-starter-storage` | 本地 + S3 兼容对象存储，多源路由 | `ypbin.storage` |
| 日志 | `ypbin-starter-log` | `@Log` 操作日志 AOP + 全量访问日志拦截器 | `ypbin.log` |
| 工具 | `ypbin-starter-tools` | 分布式限流 `@RateLimit`、幂等 `@Idempotent`、分布式锁 `@DistributedLock`、AES/国密加解密 | `ypbin.tools` |
| 异步 | `ypbin-starter-async` | 统一线程池、`@Async` 接管、异步异常处理、上下文透传、`AsyncUtils` 静态工具 | `ypbin.async` |
| Excel | `ypbin-starter-excel` | 基于 FastExcel 的注解驱动导入导出 | — |
| 验证码 | `ypbin-starter-captcha` | 行为验证码（滑块/旋转/点选/拼接） | `ypbin.captcha` |
| 消息 | `ypbin-starter-messaging` | 邮件（SMTP 配置可动态化/后台配置）、WebSocket（STOMP）、SSE + 统一推送门面 `PushService`、MQTT（Paho） | `ypbin.mail` / `ypbin.websocket` / `ypbin.sse` / `ypbin.mqtt` |
| 敏感词 | `ypbin-starter-sensitive-words` | Hutool DFA 敏感词检测/替换，可插拔词库 | `ypbin.sensitive-words` |
| 国际化 | `ypbin-starter-i18n` | Spring MessageSource 多语言，参数/头解析 Locale | `ypbin.i18n` |
| 接口加解密 | `ypbin-starter-api-crypto` | `@ApiEncrypt` 请求解密/响应加密（Advice） | `ypbin.api-crypto` |
| 接口签名 | `ypbin-starter-sign` | `@ApiSign` 四件套验签（AK/SK）、防重放、应用启停/过期、MD5/HMAC 可配 | `ypbin.sign` |
| 第三方登录 | `ypbin-starter-social` | JustAuth OAuth 登录，按平台可插拔 | `ypbin.social` |
| 多租户 | `ypbin-starter-extension-tenant` | 行级租户隔离、`@TenantIgnore` 跨租户逃逸 | `ypbin.tenant` |
| CRUD | `ypbin-starter-extension-crud` | 通用控制器/服务基类，防 Over-Posting | — |
| 数据权限 | `ypbin-starter-extension-datapermission` | 行级数据范围过滤、`@DataPermission` 门控 | `ypbin.data-permission` |
| Feign | `ypbin-starter-cloud-core` | OpenFeign 请求头透传、错误解码、熔断兜底 | `ypbin.cloud.feign` |
| Nacos | `ypbin-starter-cloud-nacos` | Nacos 注册发现 + 配置中心 + LoadBalancer 聚合 + ConfigData 启动兜底 | `ypbin.cloud.nacos` |
| 负载均衡 | `ypbin-starter-cloud-loadbalancer` | 版本灰度路由、优先 IP、权重随机、Nacos metadata | `ypbin.cloud.loadbalancer` |
| 可观测性 | `ypbin-starter-cloud-observability` | X-Request-Id 与 MDC 关联、Micrometer Tracing 门面（OTLP 可选） | `ypbin.observability` |
| 流量防护 | `ypbin-starter-cloud-sentinel` | Sentinel Web/网关限流、被拒统一 R 响应、Nacos 规则热更新 | `ypbin.cloud.sentinel` |
| 网关 | `ypbin-starter-cloud-gateway` | Spring Cloud Gateway 横切（CORS/异常/鉴权/文档聚合/动态路由） | `ypbin.gateway` |
| 单体聚合 | `ypbin-starter-app-web` | 一次引入单体 Web 常用基础能力（web/json/data/cache/security/api-doc/log/tools） | — |
| 微服务聚合 | `ypbin-starter-app-cloud` | 在 app-web 基础上叠加微服务能力（cloud-core/nacos/loadbalancer/observability/sentinel） | — |

详细用法见 [各模块使用文档](#各模块使用文档)。

## 业务系统如何对接

业务系统（如后台管理）只做业务功能，系统级能力用本 starter 已有的、或实现其**扩展点接口**（Provider）即可，不必重造。核心扩展点：

| 扩展点 | 模块 | 是否必须 | 作用 |
|---|---|---|---|
| `PermissionProvider` | security | 必须 | 提供用户权限码/角色码，接通 Sa-Token 注解鉴权 |
| `TenantProvider` | extension-tenant | 启用多租户时必须 | 提供当前租户 ID |
| `DataScopeHandler` | extension-datapermission | 启用数据权限时必须 | 提供数据范围 SQL |
| `DictProvider` | json | 可选 | 字典数据源，配 `@DictText` 自动翻译 |
| `RefTextProvider` | json | 可选 | 引用翻译数据源（ID→名称），配 `@RefText` |
| `LoginClientProvider` / `PasswordPolicyProvider` | security | 可选 | 登录客户端 / 密码策略从数据库动态配置 |
| `MailConfigProvider` / `StorageConfigProvider` / `SignAppProvider` | messaging/storage/sign | 可选 | 邮件/存储/开放应用配置后台动态化 |
| `SensitiveWordProvider` / `AuthRequestProvider` / `GatewayAuthProvider` | sensitive-words/social/gateway | 可选 | 词库 / OAuth 平台 / 网关鉴权 |

除「必须」项外均有默认实现（多为读配置文件），想接数据库/后台配置时才覆盖。所有能力 Bean 均 `@ConditionalOnMissingBean`，定义同类型 Bean 即覆盖。

## 单体 vs 微服务

两套后端共用 L1 基础层，对外契约一致（详见 [CONTRACT.md](CONTRACT.md)），前端可复用同一套调用逻辑。

| | 单体应用 | 微服务应用 |
|---|---|---|
| 起步依赖 | `ypbin-starter-app-web` | 各业务服务引 `ypbin-starter-app-cloud` |
| 网关 | 无 | 独立部署，引 `ypbin-starter-cloud-gateway` |
| 鉴权 | 应用内 sa-token | 网关统一校验 + 内部身份头透传 |
| 对前端 | 契约一致 | 契约一致 |

聚合 starter 为纯依赖聚合，业务方仍可用 Maven `<exclusions>` 排除不需要的单个模块。网关不纳入 `app-cloud`，因为它是独立部署单元而非业务服务依赖。

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
- 实体继承 `BaseEntity` 即获得主键 `id`（Long）、`createUser/createTime/updateUser/updateTime` 审计字段、业务状态字段 `status`（默认 1 正常）与逻辑删除字段 `isDeleted`（列 `is_deleted`）。
- 主键雪花算法（`@TableId(type = ASSIGN_ID)`），并单独序列化为字符串防前端精度丢失；要全局改自增/UUID，配 `mybatis-plus.global-config.db-config.id-type`。
- 审计字段 INSERT/UPDATE 自动填充；操作人来源实现 `AuditorProvider` 扩展点（引入 security 后自动对接登录用户）。
- 业务状态：`status` 只表达启停等业务状态，默认 1 正常、0 禁用；逻辑删除仍由 `isDeleted` 表达，两者不要混用。
- 逻辑删除：`isDeleted` 带 `@TableLogic`，默认规则（0 未删/1 已删）开箱生效，删除转 UPDATE、查询自动过滤；不需要逻辑删除的表对应实体不继承 `BaseEntity` 或建表不加该列即可。
- 拦截器编排：多租户、数据权限等通过 `InnerInterceptorProvider` 按 order 贡献内部拦截器，顺序可控（租户/数据权限先于分页）。

```java
// 默认雪花 ID（Long），无需再声明 id 字段
public class Article extends BaseEntity {
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

**数据字典翻译** `@DictText`：实体存字典值（code），序列化时**保留原字段原值不变**、并**额外输出**一个展示文本字段（遵循全链路字段同名，不改名只增派生字段）。字典表与 CRUD 归 admin，实现 `DictProvider` 把数据源接进来，starter 负责缓存与翻译：

```java
@DictText("sys_user_status")
private String status;      // 输出 "status":"1","statusText":"正常"

@DictText(value = "gender", suffix = "Label")
private String gender;      // 输出 "gender":"1","genderLabel":"男"
```

```java
// admin 侧实现字典数据源（从 sys_dict_item 读）
@Component
public class DbDictProvider implements DictProvider {
    @Override
    public List<DictItem> getItems(String dictType) { /* 查字典表 */ }
}
```

`DictCache` 带缓存（字典维护后调 `DictUtils.refresh()` 即时生效）；`DictUtils.translate(type, value)` / `getItems(type)` 供任意层静态调用；未接入 `DictProvider` 时翻译安全退化为原值。

**引用翻译** `@RefText`：实体存引用 ID（如 createUser、deptId），序列化时**保留原字段原值**、并**额外输出**展示名称字段。适合"存 ID、展示中文名"场景。数据源（用户表、部门表）由 admin 实现 `RefTextProvider`，starter 负责缓存与批量：

```java
@RefText("user")
private Long createUser;    // 输出 "createUser":"123","createUserName":"张三"

@RefText(value = "dept", suffix = "Text")
private Long deptId;        // 输出 "deptId":"8","deptIdText":"研发部"
```

**扩展点强制批量**，从根源规避 N+1——`RefTextProvider` 一次传一组 ID、一次返回映射：

```java
@Component
public class UserRefTextProvider implements RefTextProvider {
    @Override public String type() { return "user"; }
    @Override public Map<Object, String> getNames(Collection<Object> ids) {
        // 一条 SQL：SELECT id, nickname FROM sys_user WHERE id IN (...)
    }
}
```

**列表零 N+1 全自动**：无需任何手动调用——响应体序列化前由切面自动扫描对象图、按类型批量预加载，序列化时全命中缓存。业务只管正常返回：

```java
@GetMapping
public R<List<OrderResp>> list() {
    return R.ok(orderService.list());   // 什么都不用做，createUserName 自动翻译且零 N+1
}
```

个别接口想跳过自动预加载（如超大导出），方法/类加 `@RefTextIgnore`；全局关闭设 `ypbin.json.ref-text.auto-resolve=false`。也可手动 `refTextResolver.preload(list)`（非必需）。

缓存带 TTL 与容量上限（配 `ypbin.json.ref-text.ttl-seconds` / `max-size`），重复 ID 不重查、不存在的 ID 走空值哨兵防穿透；不含 `@RefText` 的响应由类级缓存瞬间跳过、零遍历成本；数据变更后调 `RefTextUtils.refresh(type)` 即时生效。未接入 `RefTextProvider` 时不输出名称字段、安全退化。

> 与 `@DictText` 的区别：`@DictText` 翻固定枚举（字典表），`@RefText` 翻动态实体引用（用户/部门等表）；两者同款"保留原字段 + 额外派生字段"，都不改字段名。

### cache — 缓存

基于 Redis 的 `CacheService` 统一缓存接口，值以 JSON 存储：

```java
@Autowired
private CacheService cacheService;

cacheService.set("user:1", user, Duration.ofMinutes(30));
User user = cacheService.get("user:1", User.class);
```

非 Spring 托管场景（静态方法、工具类）无法注入时，用静态门面 `CacheUtils`（内部委托 `CacheService`）：

```java
CacheUtils.set("user:1", user, Duration.ofMinutes(30));
User user = CacheUtils.get("user:1", User.class);
long pv = CacheUtils.increment("page:pv", 1);
```

> Spring 组件仍应优先直接注入 `CacheService`，语义更清晰、更易测试；`CacheUtils` 仅用于拿不到注入的场景。

**缓存旁路 + 三重保护** `getOrLoad`：未命中自动回源并回填，内置防击穿/防穿透/防雪崩：

```java
User user = cacheService.getOrLoad("user:" + id, User.class,
    () -> userMapper.selectById(id),      // 回源函数
    Duration.ofMinutes(30));
```

- **防击穿**：回源时用 Redis 短锁单飞，热点 key 过期瞬间只有一个线程/节点回源，其余等待回填。
- **防穿透**：回源结果为 `null` 时缓存空值哨兵（短 TTL），不存在的 key 不会反复打到数据源。
- **防雪崩**：写入 TTL 叠加 0~10% 随机扰动，避免大量 key 同时过期。

**多级缓存（L1 Caffeine 本地 + L2 Redis）**：读多写少的热点数据，引入 Caffeine 依赖并开启后，`CacheService` 自动升级为多级缓存：

```yaml
ypbin:
  cache:
    multi-level:
      enabled: true
      local-max-size: 10000
      local-expire-seconds: 300
      invalidation-broadcast: true          # 多副本部署需开启
```

- 读先查 L1 本地（无 Redis 往返），未命中再查 L2 并回填 L1。
- 写/删更新 L2 并失效本地 L1，同时经 Redis Pub/Sub 广播，通知其它实例摘除各自 L1，保证多实例最终一致。
- 适合可容忍秒级不一致的读多写少场景；强一致数据不要开多级缓存。业务代码无需改动，仍用 `CacheService` 接口。

需要 Redis 专属数据结构（hash/list/set/zset）时，用 `RedisUtils` 静态工具（直接封装 `RedisTemplate` 全能力）：

```java
RedisUtils.setIfAbsent("lock:order:1", "1", Duration.ofSeconds(10));  // 分布式锁
RedisUtils.hSet("user:1", "name", "tom");                            // hash
RedisUtils.zAdd("rank", "player1", 99.5);                            // zset 排行榜
Set<Object> top10 = RedisUtils.zReverseRange("rank", 0, 9);
```

分工：与实现无关的通用缓存走 `CacheService`/`CacheUtils`；Redis 专属结构走 `RedisUtils`。

### security — 认证授权

基于 Sa-Token 封装：

- **全局登录拦截**：Servlet Web 环境下自动注册 `SaInterceptor` 做全局登录校验，无需自己写 `WebMvcConfigurer`。默认拦截 `/**`，放行 `ypbin.security.excludes`；检测到 api-doc 时自动放行 Swagger/`doc.html`/`v3/api-docs`/`webjars` 等文档路径。

```yaml
ypbin:
  security:
    interceptor: true            # 是否注册全局登录拦截器（默认开）
    includes: ["/**"]            # 拦截路径
    excludes: ["/login", "/captcha"]   # 放行路径（无需登录）
    exclude-api-doc: true        # 有 SpringDoc 时自动放行文档路径（默认开）
```

拦截器只校验「已登录」；细粒度权限/角色用方法上的 `@SaCheckPermission` 等注解。业务方提供自定义 `WebMvcConfigurer` 或设 `interceptor: false` 即可覆盖/停用。

- `LoginHelper`：`login(userId)` / `getUserId()` / `logout()`，统一以 `Long` 用户 ID 进出。
- `UserContext` + `LoginUser`：当前登录用户门面，登录时 `setLoginUser` 存会话，任意层 `getLoginUser`/`getUserId`/`getUsername`/`getTenantId`/`getClientId`/`getClientType`/`getAuthType` 读取。
- 登录客户端：`LoginClientProvider` 提供客户端配置，`LoginHelper.login(userId, LoginClientRequest)` 按客户端独立策略登录；starter 提供配置文件版默认实现，admin 有客户端管理表时实现 Provider 覆盖即可。

**登录客户端策略**：适合后台、App、小程序、开放 API 等不同入口配置不同 token 有效期、活跃超时、多端并发和登录方式。starter 只提供运行时抽象，不内置 admin 表和页面。

```yaml
ypbin:
  security:
    client-enabled: true
    default-client-id: web-admin
    clients:
      - client-id: web-admin
        client-type: WEB
        auth-types: [ACCOUNT, PHONE, EMAIL]
        timeout: 86400              # Token 固定有效期(秒)，为空使用 sa-token 全局配置
        active-timeout: 1800        # 活跃超时(秒)，为空使用 sa-token 全局配置
        concurrent: true            # 是否允许同账号多端同时登录
        share: false                # 多端登录是否共享同一个 token
        max-login-count: -1         # 同账号最大登录数量，-1 不限制
        replaced-range: ALL_DEVICE_TYPE
        replaced-login-exit-mode: OLD_DEVICE
        overflow-logout-mode: KICKOUT
        enabled: true
```

登录流程示例：

```java
LoginClientRequest clientReq = new LoginClientRequest(req.getClientId(), req.getAuthType());
clientReq.setClientSecret(req.getClientSecret());      // 浏览器端可不传；服务端/开放平台可启用
clientReq.setDeviceId(req.getDeviceId());              // App/多设备场景可传
LoginClient client = LoginHelper.login(userId, clientReq);

LoginUser user = new LoginUser(userId, username);
user.setClientId(client.getClientId());
user.setClientType(client.getClientType());
user.setAuthType(clientReq.getAuthType());
UserContext.setLoginUser(user);
```

admin 接数据库客户端管理时，只需实现：

```java
@Component
public class DbLoginClientProvider implements LoginClientProvider {
    @Override
    public Optional<LoginClient> findByClientId(String clientId) {
        // 从 sys_client 查询并转换为 LoginClient
    }
}
```

**Token 续期**：Sa-Token 是「续期」机制，不是 OAuth2 的 access+refresh 双令牌——不换 token，延长现有 token 有效期。两层超时：

```yaml
sa-token:
  timeout: 2592000          # 固定有效期(秒)，30 天，到点必过期
  active-timeout: 1800      # 活跃超时(秒)，30 分钟无操作则冻结
  auto-renew: true          # 活跃用户自动续期（开启后通常无需手动续）
```

开了 `auto-renew` 后活跃用户的 token 自动续期，一般不用手动调。需要显式控制或查剩余时长时用 `LoginHelper`：

```java
long timeout = LoginHelper.getTokenTimeout();          // 剩余有效期(秒)，-1 永不过期
long active = LoginHelper.getTokenActiveTimeout();     // 距被冻结剩余(秒)
LoginHelper.renewTimeout(3600);                        // 手动重设有效期
LoginHelper.updateLastActiveToNow();                   // 续活跃，避免被冻结
SaTokenInfo info = LoginHelper.getTokenInfo();         // token 完整信息
```

> 需要开放平台级 access+refresh 双令牌，才引 `sa-token-oauth2`；后台管理系统用上面的续期即可，不必上 OAuth2。
- 权限数据源：实现 `PermissionProvider` 提供用户的权限码与角色码，框架自动适配为 Sa-Token 的 `StpInterface`，无需直接依赖 Sa-Token API。

获取当前登录人信息：

```java
// 登录成功后写入完整用户信息到会话
LoginUser user = new LoginUser(userId, "tom");
user.setNickname("汤姆");
user.setTenantId(1001L);
user.setDeptId(8L);
user.setRoles(Set.of("admin"));
UserContext.setLoginUser(user);

// 之后任意层读取
Long userId = UserContext.getUserId();                 // 当前用户 ID（来自 Sa-Token 登录态）
Optional<LoginUser> current = UserContext.getLoginUser(); // 完整登录用户信息
Optional<String> name = UserContext.getUsername();     // 用户名
Optional<Long> tenant = UserContext.getTenantId();     // 租户 ID
Optional<String> clientId = UserContext.getClientId(); // 客户端 ID
UserContext.setAttribute("postId", 66L);               // 业务自有字段另存
Optional<Long> post = UserContext.getAttribute("postId", Long.class);
```

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

**密码安全策略**：复杂度校验 + 错误锁定的运行时能力。starter 提供能力与扩展点，不内置策略配置表；策略来源可用配置文件，也可由业务系统实现 `PasswordPolicyProvider` 从配置中心/数据库读取，支持后台可视化调整。

```yaml
ypbin:
  security:
    password:
      min-length: 8               # 最小长度
      max-length: 32              # 最大长度
      require-digit: true         # 必须含数字
      require-letter: true        # 必须含字母
      require-uppercase: false    # 必须含大写
      require-symbol: false       # 必须含特殊字符
      require-lowercase: false    # 必须含小写
      allow-contain-username: false  # 是否允许含用户名（含反序）
      error-lock-count: 5         # 登录错误锁定阈值，0=不锁定
      lock-minutes: 15            # 账号锁定时长(分钟)
      expiration-days: 0          # 密码有效期(天)，0=永不过期
      expiration-warning-days: 0  # 到期提醒天数，0=不提醒
      history-count: 0            # 历史密码不可重复次数，0=不校验
```

> 策略每次实时读取：配置文件方式改 yml 需重启生效；若 admin 实现 `PasswordPolicyProvider` 从数据库读，则后台改配置即时生效，无需重启。

复杂度校验用 `PasswordValidator`（改密/注册时）：

```java
PasswordCheckResult result = passwordValidator.check(rawPassword, username);
if (!result.passed()) {
    throw new BusinessException(result.message());
}
```

错误锁定用 `PasswordAttemptLimiter`（登录流程）。账号标识大小写归一，计数默认按 `账号:IP` 维度：

```java
limiter.checkLocked(username, ip);        // 登录前：已锁定抛 AccountLockedException
if (!PasswordEncoderUtil.matches(raw, hash)) {
    limiter.recordFailure(username, ip);  // 密码错误：计数 +1，本次即达阈值则直接抛锁定
    throw new BusinessException("用户名或密码错误");
}
limiter.reset(username, ip);              // 登录成功：清除计数
```

后台/前端可查锁定状态与解锁：

```java
LockStatus status = limiter.getLockStatus(username, ip); // 是否锁定/失败次数/剩余次数/剩余解锁秒数
limiter.unlock(username);                                // 管理员解锁：清除该账号全部维度（无需知道被哪些 IP 锁）
```

计数默认存 Redis（有 Redis 时自动用，多节点共享）、否则内存，锁定时长即计数键 TTL、到期自动解锁。

密码有效期用 `PasswordExpiration`（纯计算，"强制改密"的登录拦截编排由业务侧结合用户表的最后改密时间实现）：

```java
boolean expired = passwordExpiration.isExpired(user.getPwdResetTime());   // 是否已过期
boolean warn = passwordExpiration.shouldWarn(user.getPwdResetTime());     // 是否进入到期提醒窗口
long days = passwordExpiration.remainingDays(user.getPwdResetTime());     // 距过期剩余天数
```

历史密码不重复（`historyCount`）由 starter 提供策略值，历史密码表与比对由 admin 侧实现。

**在线用户**：基于 Sa-Token 会话枚举在线登录记录，支持查询与强制下线。表与页面归 admin，starter 提供运行时 `OnlineUserService`：

```java
@Autowired
private OnlineUserService onlineUserService;

List<OnlineUser> all = onlineUserService.list();          // 全部在线（一个 token 一条）
List<OnlineUser> hit = onlineUserService.list("张三");     // 按用户名/昵称过滤
onlineUserService.kickoutByToken(token);                  // 踢某个登录设备
onlineUserService.kickoutByUserId(userId);                // 踢某用户全部设备
```

用户名/昵称/租户/客户端等展示字段来自登录时写入会话的 `LoginUser`；IP/浏览器/操作系统/登录时间为可选，登录成功时用 `OnlineUserHelper.record(ip, browser, os)` 记录即可在列表展示：

```java
LoginHelper.login(userId, clientReq);
UserContext.setLoginUser(loginUser);
OnlineUserHelper.record(ip, browser, os);   // 可选：记录终端信息供在线列表展示
```

### api-doc — API 文档

SpringDoc OpenAPI 开箱即用，配置文档元信息：

```yaml
ypbin:
  api-doc:
    enabled: true
    disable-in-prod: true        # 生产环境关闭文档端点（默认 true，重要安全默认值）
    title: 订单服务 API
    description: 订单中心接口文档
    version: 1.0.0
    group-name: default
    default-group-enabled: true  # 是否创建默认分组
    order-enabled: true          # 是否启用 @ApiOrder 排序
    paths-to-match: ["/**"]      # 纳入文档的路径
    paths-to-exclude: ["/error", "/actuator/**"]
    packages-to-scan: []         # 限定扫描包（空=全部）
    packages-to-exclude: []
    security-headers: ["Authorization", "X-Request-Id", "X-Tenant-Id", "X-Version"]  # 全局请求头
    contact:
      name: wenbin
      email: dev@example.com
      url: https://example.com
    license:
      name: Apache-2.0
      url: https://www.apache.org/licenses/LICENSE-2.0
```

启动后访问 `/swagger-ui.html`。**生产安全**：`disable-in-prod` 默认 `true`，在 `prod` profile 下自动关闭 SpringDoc 端点，避免接口文档对外暴露。

**接口排序** `@ApiOrder`：控制 Controller / 方法在文档中的展示顺序（数值小的靠前），需 `order-enabled: true`：

```java
@ApiOrder(1)
@RestController
public class UserController { ... }
```

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

**后台动态配置**：存储源默认读 `ypbin.storage.*`，也可由业务方实现 `StorageConfigProvider` 从数据库读取，后台改完调 `StorageStrategyRebuilder.rebuild()` 即时生效（`StorageRouter` 原子刷新，新增/修改/删除的源立即路由，无需重启）：

```java
@Component
public class DbStorageConfigProvider implements StorageConfigProvider {
    @Override public List<LocalConfig> getLocalConfigs() { /* 查存储配置表 */ }
    @Override public List<OssConfig> getOssConfigs() { /* 查存储配置表 */ }
    @Override public String getDefaultPlatform() { /* 默认平台 */ }
}

// 后台保存存储配置后：
storageStrategyRebuilder.rebuild();   // 即时生效
```

### log — 操作日志

`@Log` 注解 AOP 采集，**异步持久化**不阻塞业务：

```java
@Log(value = "创建订单", module = "订单", includes = Include.REQUEST_BODY)
@PostMapping("/orders")
public R<Void> create(@RequestBody OrderReq req) { ... }
```

- `Include` 控制采集粒度：请求头/体/参数、响应体、IP、浏览器、OS、登录客户端信息。默认采集请求参数 + IP + 客户端信息（不采集请求/响应体，防敏感信息与大报文落库）。
- 请求体从 AOP 入参序列化（能拿到 `@RequestBody` 的 JSON），过滤文件流等不可序列化参数。
- 持久化：实现 `LogDao` 落库（默认仅打印到日志）；操作人来源实现 `LogUserProvider`。
- 登录客户端信息（`clientId/clientType/authType`）来源实现 `LogClientProvider`；引入 security 模块后自动对接登录会话，无需业务实现即可让操作日志记录“从哪个客户端、用什么方式登录”。
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

**分布式锁** `@DistributedLock`（有 Redis 时为跨节点分布式锁，否则单机内存锁）：

```java
@DistributedLock(key = "#orderId", ttl = 30)
public void handle(Long orderId) { ... }   // 抢不到锁默认跳过，返回 null
```

- 加锁用 `SET key owner NX EX ttl`，释放用 Lua 校验持有者后删除，只释放自己的锁。
- `ttl` 应大于方法最长执行时间，防止持有者宕机死锁。
- `waitTime > 0` 时按 `retryInterval` 毫秒重试等待；`failStrategy` 可选 `SKIP`（默认，静默跳过）或 `EXCEPTION`（抛 `LockAcquireException`，业务码 429）。
- 也可直接注入 `LockService` 手动 `tryLock/unlock`。

**定时任务防重**（多实例只让一个节点执行）：`@Scheduled` 方法叠加 `@DistributedLock` 即可，抢不到锁的节点自动跳过。

```java
@Scheduled(cron = "0 0 2 * * ?")
@DistributedLock(key = "job:daily-settle", ttl = 300)
public void dailySettle() { ... }
```

**AES 加解密** `AesUtils`：AES-GCM 认证加密，随机 IV 前置：

```java
byte[] key = AesUtils.generateKey(256);            // 随机密钥（128/192/256）
String cipher = AesUtils.encrypt("secret", key);   // 字符串 → Base64 密文
String plain  = AesUtils.decrypt(cipher, key);

byte[] ct = AesUtils.encryptBytes(data, key);      // 字节级加解密
byte[] pt = AesUtils.decryptBytes(ct, key);

String b64Key = AesUtils.generateKeyBase64(256);   // 密钥 Base64 存取
byte[] derived = AesUtils.deriveKey("口令", AesUtils.generateSalt(16), 256);  // PBKDF2 口令派生
```

**国密 SM4/SM2**（基于 BouncyCastle，合规场景）：

```java
// SM4 对称：ECB / CBC / GCM 三模式（推荐 GCM 认证加密）
byte[] k = Sm4Utils.generateKey();                 // 随机 16 字节密钥
String c = Sm4Utils.encrypt("secret", k);          // ECB + Base64（向后兼容）
byte[] gcm = Sm4Utils.encryptGcm(data, k);         // GCM，IV 前置
byte[] cbc = Sm4Utils.encryptCbc(data, k, Sm4Utils.generateIv(16));
String hex = Sm4Utils.encryptHex("secret", k);     // Hex 形态

// SM2 非对称：加解密 + 签名验签
Sm2Utils.KeyPairBase64 kp = Sm2Utils.generateKeyPair();
String cipher = Sm2Utils.encrypt("secret", kp.publicKey());
String plain  = Sm2Utils.decrypt(cipher, kp.privateKey());
String sign = Sm2Utils.sign("data", kp.privateKey());          // SM3withSM2 签名
boolean ok  = Sm2Utils.verify("data", sign, kp.publicKey());   // 验签
```

### extension-crud — 通用 CRUD

基类库，消除增删改查样板。控制器拆成两层，避免一个基类同时承担「通用辅助」和「标准 CRUD 路由」导致不灵活：

- `BaseController`：轻量辅助基类，封装 `request()`、`path()`、`method()`、`header()`、`param()`、`ip()`、`file()/files()`、`isLogin()`、`userId()`、`username()`、`tenantId()`、`ok()/data()/success()/fail()/status()`，不声明任何路由。复杂业务、非标准端点直接继承它。
- `CrudController`：标准 CRUD 抽象控制器，声明 `GET /{id}`、`GET /list`、`GET /` 分页、`POST /`、`PUT /{id}`、`DELETE /{id}`。适合接口形态稳定、业务逻辑较轻的实体。

```java
// 服务层：继承 BaseServiceImpl，自动拥有 CRUD + 分页
@Service
public class ArticleService extends BaseServiceImpl<ArticleMapper, Article> { }

// 标准 CRUD 控制器：泛型 <实体, 主键, 请求, 响应, 查询>
// REQ/RESP 与实体默认 BeanUtils 同名字段转换；无业务过滤时查询泛型直接用 PageQuery
@RestController
@RequestMapping("/articles")
public class ArticleController extends CrudController<Article, Long, ArticleReq, ArticleResp, PageQuery> {
    private final ArticleService service;
    @Override protected BaseService<Article> getBaseService() { return service; }
}
```

`save/update` 收 `REQ`、查询返回 `RESP`，实体永不直接暴露。简单场景可将 REQ/RESP 直接指定为实体类型；需精细控制时覆盖 `toEntity` / `toResp`（接 MapStruct 等）。分页用 `PageQuery` / `PageResult`，请求参数为 `page/pageSize`，响应数据为 `items/total/page/pageSize/pages`。

**复杂控制器**：直接继承 `BaseController`，手写端点，保留统一响应辅助：

```java
@RestController
@RequestMapping("/articles")
public class ArticleController extends BaseController {
    @GetMapping("/{id}/publish-info")
    public R<ArticlePublishInfo> publishInfo(@PathVariable Long id) {
        return ok(articleService.getPublishInfo(id));
    }
}
```

**操作级鉴权（推荐：一次声明全端点覆盖）**：覆盖 `permissionPrefix()` 返回权限前缀，六个端点自动按 `前缀:动作` 校验，杜绝「逐个端点挂注解漏挂导致越权」：

```java
public class ArticleController extends CrudController<Article, Long, ArticleReq, ArticleResp, PageQuery> {
    @Override
    protected String permissionPrefix() {
        return "system:article";   // get/list/page→:list，save→:add，update→:edit，delete→:delete
    }
}
```

> 安全默认：`permissionPrefix()` 默认返回 `null`（不校验，仅受全局登录拦截）；受保护资源务必覆盖它。依赖 Sa-Token，未引入时自动跳过。

**精细控制**：需要某端点单独权限码/逻辑时，仍可 `@Override` 端点挂 `@SaCheckPermission` 再 `super.xxx()`，与前缀机制共存：

```java
@Override
@SaCheckPermission("system:article:publish")
public R<Void> save(@RequestBody ArticleReq req) {
    return super.save(req);
}
```

**业务过滤分页**：查询泛型 `Q` 指定为携带过滤字段的 `PageQuery` 子类，覆盖 `buildQueryWrapper`：

```java
// 查询对象继承 PageQuery，加业务过滤字段
public class ArticleQuery extends PageQuery {
    private String title;
    // getter/setter
}

// 控制器第 5 个泛型指定为 ArticleQuery，Spring 会把 ?title=x 绑定进来
public class ArticleController extends CrudController<Article, Long, ArticleReq, ArticleResp, ArticleQuery> {
    @Override
    protected Wrapper<Article> buildQueryWrapper(ArticleQuery q) {
        return Wrappers.<Article>lambdaQuery()
            .like(StringUtils.hasText(q.getTitle()), Article::getTitle, q.getTitle());
    }
}
```

**写操作扩展**：覆盖 `beforeSave/afterSave/beforeUpdate/afterUpdate/beforeDelete/afterDelete` 模板钩子插入密码加密、查重、事务内分配角色等；需事务在覆盖的端点方法上加 `@Transactional`：

```java
@Override
protected void beforeSave(UserReq req, User entity) {
    entity.setPassword(PasswordEncoderUtil.encode(req.getPassword()));  // 密码加密
    if (service.exists(Wrappers.<User>lambdaQuery().eq(User::getUsername, req.getUsername()))) {
        throw new BusinessException("用户名已存在");
    }
}
```

> 定位：标准且轻量的资源用 `CrudController`；业务规则多、端点形态特殊、鉴权编排复杂的资源继承 `BaseController` 自写。

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

**租户实体基类** `TenantBaseEntity`：需要租户字段的实体继承它（而非 `BaseEntity`），即在主键/审计/逻辑删除之外多一个 `tenantId` 字段：

```java
public class Order extends TenantBaseEntity {
    private String orderNo;
}
```

租户隔离由行级拦截器自动在 SQL 追加 `tenant_id` 条件，`tenantId` 字段供实体层读写；不需要租户的实体继承 `BaseEntity` 即可，避免基础表被迫带 `tenant_id` 列。

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

### async — 异步与线程池

引入后自动装配统一线程池 `ypbinTaskExecutor` 与调度器 `ypbinTaskScheduler`，并接管 `@Async`（默认执行器指向统一线程池）。线程池自动挂载 core 的上下文透传装饰器，租户/用户/MDC 会传播到异步线程。

```yaml
ypbin:
  async:
    enabled: true
    enable-annotation: true       # 接管 @Async
    virtual-threads: false        # JDK 21+ 可开虚拟线程
    core-size: 8
    max-size: 32
    queue-capacity: 1000
    keep-alive-seconds: 60
    thread-name-prefix: ypbin-async-
    rejection-policy: caller-runs # caller-runs/abort/discard/discard-oldest
    await-termination: true       # 优雅停机
    await-termination-seconds: 30
    scheduler-pool-size: 2
```

注解式：

```java
@Async
public void sendMail(String to) { ... }              // 走统一线程池

@Async("otherExecutor")
public void special() { ... }                        // 指定其它执行器
```

`@Async` 返回 void 的方法异常会被统一记录（方法名/入参/堆栈），不再静默丢失。

静态工具 `AsyncUtils`（无需注入执行器，业务方直接调用）：

```java
// 提交
AsyncUtils.run(() -> doSomething());
CompletableFuture<Integer> f = AsyncUtils.supply(() -> calc());

// 编排
AsyncUtils.then(f, v -> v + 1);
AsyncUtils.combine(f1, f2, Integer::sum);
AsyncUtils.withFallback(f, ex -> -1);

// 批量并发（结果顺序与入参一致）
List<R> rs = AsyncUtils.supplyAll(List.of(() -> a(), () -> b()));
List<R> rs2 = AsyncUtils.mapAll(items, item -> handle(item));
AsyncUtils.runAll(List.of(() -> t1(), () -> t2()));

// 等待
AsyncUtils.allOf(futures).join();
List<T> results = AsyncUtils.joinAll(futures);
T r = AsyncUtils.join(future, Duration.ofSeconds(3));   // 带超时

// 调度
AsyncUtils.schedule(task, Duration.ofSeconds(10));
AsyncUtils.scheduleAtFixedRate(task, Duration.ofMinutes(1));
AsyncUtils.scheduleWithFixedDelay(task, Duration.ofMinutes(1));
```

业务方自定义同名 `ypbinTaskExecutor` / `AsyncConfigurer` 时不被覆盖。

### excel — 导入导出

基于 FastExcel，注解驱动。实体字段用 `@ExcelProperty` 标注列名：

```java
public class UserExcel {
    @ExcelProperty("用户名")
    private String username;
    @ExcelProperty("年龄")
    private Integer age;
}

// 导入：同步全量 / 指定 sheet / 自定义表头行
List<UserExcel> list = ExcelUtils.read(inputStream, UserExcel.class);
List<UserExcel> s2 = ExcelUtils.read(inputStream, UserExcel.class, 1);          // 第 2 个 sheet
List<UserExcel> h2 = ExcelUtils.read(inputStream, UserExcel.class, 0, 2);       // 表头占 2 行

// 大文件分批流式读取，避免一次性载入内存
ExcelUtils.readInBatch(inputStream, UserExcel.class, 1000, batch -> saveBatch(batch));

// 导出到 HTTP 响应（浏览器下载，文件名自动 UTF-8 编码）
ExcelUtils.export(response, "用户列表", UserExcel.class, list);

// 仅导出/排除指定列（字段名）
ExcelUtils.writeIncludeColumns(out, "用户", UserExcel.class, list, List.of("username"));

// 多 sheet 导出
ExcelUtils.exportMultiSheet(response, "报表", List.of(
    ExcelUtils.SheetData.of("用户", UserExcel.class, users),
    ExcelUtils.SheetData.of("订单", OrderExcel.class, orders)));
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

### messaging — 消息（邮件 / WebSocket / SSE / MQTT）

**邮件**：SMTP 配置默认读 `ypbin.mail.*`，也可由业务方实现 `MailConfigProvider` 从数据库读取，支持后台可视化配置、改完不重启即时生效（`MailService` 按配置指纹缓存底层 sender，配置变化自动重建）：

```yaml
ypbin:
  mail:
    host: smtp.qq.com
    port: 465
    username: your@qq.com
    password: your-auth-code      # 授权码/密码
    from: noreply@qq.com          # 发件人，为空取 username
    from-name: 系统通知            # 发件人显示名（可选）
    ssl-enabled: true
    starttls-enabled: false
```

```java
@Autowired
private MailService mailService;

mailService.sendText("to@example.com", "标题", "正文");
mailService.sendHtml("to@example.com", "标题", "<h1>HTML 正文</h1>");
mailService.sendWithAttachments("to@example.com", "标题", "正文", false, new File("report.xlsx"));
mailService.sendTest("to@example.com");   // 后台"保存配置前先测一封"，失败抛异常带原因
boolean ready = mailService.isConfigured();
```

发件人默认取 `from`（为空取 `username`）。非注入场景（异步任务、工具方法）可用静态门面 `MailUtils`：

```java
MailUtils.sendText("to@example.com", "标题", "正文");
MailUtils.sendHtml("to@example.com", "标题", "<h1>HTML</h1>");
```

**后台动态配置**：admin 把 SMTP 配置存表、做页面时，实现 `MailConfigProvider` 从数据库读即可覆盖默认配置文件来源，改完下次发送即时生效：

```java
@Component
public class DbMailConfigProvider implements MailConfigProvider {
    @Override
    public MailConfig getConfig() {
        // 从配置表读 SMTP 参数，组装为 MailConfig
    }
}
```

**短信**：基于短信聚合框架 sms4j，一套接口统一阿里云/腾讯云/华为云等多厂商。需引入 sms4j 依赖与对应厂商依赖并配置：

```xml
<dependency>
    <groupId>org.dromara.sms4j</groupId>
    <artifactId>sms4j-spring-boot-starter</artifactId>
</dependency>
```

```yaml
# sms4j 原生配置（厂商密钥/模板），配置文件方式
sms:
  blends:
    ali:                      # configId
      supplier: alibaba
      access-key-id: xxx
      access-key-secret: xxx
      signature: 签名
      template-id: SMS_xxx
```

```java
@Autowired
private SmsService smsService;

smsService.send("13800138000", "1234");                               // 单变量
smsService.sendByTemplate("13800138000", "SMS_xxx", Map.of("code", "1234"));
smsService.sendByConfig("ali", "138...", "SMS_xxx", Map.of("code", "1234")); // 指定厂商
```

非注入场景用静态门面 `SmsUtils.send(...)`。**后台动态配置**：厂商密钥要做成后台可配置时，实现 sms4j 的 `SmsReadConfig` 从数据库读取（sms4j 原生扩展点），改完即时生效，无需重启——starter 不再包一层，避免配置翻译。

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

**SSE（服务端单向推送）+ 统一推送门面**：适合「全局未读提醒」「扫码登录状态变更」「大屏数据刷新」等服务端主动推、免前端长轮询的场景。SSE 基于 HTTP，比 WebSocket 更轻、浏览器 `EventSource` 自动重连。开启：

```yaml
ypbin:
  sse:
    enabled: true
    register-endpoint: true      # 内置订阅端点；生产建议关闭改用带鉴权的自建端点
    path: /ypbin/sse/subscribe
    timeout: 300000              # 连接超时(ms)，到期客户端自动重连
```

前端建立订阅（`userId` 生产应由登录态解析，勿信任前端传参）：

```javascript
const es = new EventSource('/ypbin/sse/subscribe?userId=123');
es.addEventListener('unread-count', e => render(JSON.parse(e.data)));
```

后端用统一门面 `PushService` 推送，屏蔽底层通道：

```java
@Autowired
private PushService pushService;

pushService.sendToUser("123", "unread-count", Map.of("count", 5));   // 推指定用户
pushService.broadcast("dashboard-refresh", dashboardData);           // 广播（大屏刷新）
boolean online = pushService.isOnline("123");                        // 是否在线
```

非注入场景（异步任务、事件监听、工具方法）用静态门面 `PushUtils`（内部委托 `PushService`）：

```java
PushUtils.sendToUser("123", "unread-count", Map.of("count", 5));
PushUtils.broadcast("dashboard-refresh", dashboardData);
```

**多实例说明**：SSE 连接与 `PushService` 默认基于单实例内存连接表；微服务多副本下，A 实例发起的推送到不了连在 B 实例的客户端。跨实例扇出需在上层配合 Redis Pub/Sub 或 MQTT 中转（业务方自定义 `PushService` 覆盖默认实现即可接入）。

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

消费推荐实现 `MqttMessageHandler` Bean，容器启动后自动订阅并进入回调：

```java
@Component
public class DeviceUpMessageHandler implements MqttMessageHandler {
    @Override
    public String topic() {
        return "device/+/up";
    }

    @Override
    public Integer qos() {
        return 1;
    }

    @Override
    public void handle(String topic, String payload) {
        // 这里就是 MQTT 消费回调
    }
}
```

临时订阅也可直接使用 `MqttSubscriber`，以「主题 → 回调」接收消息（回调参数为 topic 与 UTF-8 解码后的 payload）：

```java
@Autowired
private MqttSubscriber mqttSubscriber;

mqttSubscriber.subscribe("device/+/up", (topic, payload) -> handle(topic, payload));
mqttSubscriber.subscribe("alarm/#", 2, (topic, payload) -> alarm(payload));  // 指定 QoS
mqttSubscriber.unsubscribe("device/+/up");
```

断线自动重连后 Paho 会丢失原订阅，`MqttSubscriber` 会登记主题和消费回调，并在重连完成时自动恢复订阅，业务无需处理。

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

非注入场景（校验工具、DTO 自校验）可用静态门面 `SensitiveWordUtils`：

```java
if (SensitiveWordUtils.contains(text)) { ... }
String clean = SensitiveWordUtils.filter(text, '*');
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

对外提供给第三方对接的接口做签名校验（`accessKey + timestamp + nonce + sign` 四件套），防篡改与重放。**需同时开启 web 的可重复读请求**（见 web 章节）：

```yaml
ypbin:
  sign:
    enabled: true
    mode: ANNOTATION          # ANNOTATION（仅 @ApiSign 接口）或 GLOBAL（全局，按 skip-path 排除）
    algorithm: HMAC_SHA256    # 或 MD5（兼容旧系统）
    timeout: 60               # 签名有效期(秒)
    replay-protect: true      # nonce 防重放（有 Redis 用 Redis，否则内存）
    apps:
      - access-key: ak-001
        secret-key: your-secret-key
        app-name: 合作方A
        expire-time: 2027-01-01T00:00:00   # 失效时间，为空永不过期
        enabled: true
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
Map<String, String> signed = SignClient.sign(bizParams, "ak-001", "your-secret-key", SignAlgorithm.HMAC_SHA256);
// signed 含 accessKey/timestamp/nonce/sign + 业务参数，随请求发送
```

**应用来源**：默认读 `ypbin.sign.apps` 配置。应用有管理表时实现 `SignAppProvider` 从数据库按 accessKey 加载（密钥建议加密存储），覆盖默认实现即可；校验时自动判断应用是否禁用、是否过期。

```java
@Component
public class DbSignAppProvider implements SignAppProvider {
    @Override
    public Optional<SignApp> findByAccessKey(String accessKey) {
        // 从 sys_app 查询并转换为 SignApp（含 secretKey/expireTime/enabled）
    }
}
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

### cloud-core — 微服务 Feign 增强

引入即自动装配，默认提供请求头透传与 R 响应错误解码。依赖 `spring-cloud-starter-openfeign`
和 `spring-cloud-starter-circuitbreaker-resilience4j`。

```xml
<dependency>
    <groupId>cn.ypbin.starter</groupId>
    <artifactId>ypbin-starter-cloud-core</artifactId>
</dependency>
```

**请求头透传**：Feign 调用时自动把上游请求头（默认 `Authorization` / `X-Request-Id` / `X-Trace-Id`）
透传给下游。可通过配置扩展白名单：

```yaml
ypbin:
  cloud:
    feign:
      propagate-headers:
        - Authorization
        - X-Request-Id
        - X-Tenant-Id
```

**统一错误解码**：下游返回非 2xx 且响应体为 ypbin 统一 `R` 时，自动转为 `FeignRemoteException`
（含下游业务码与提示），由全局异常处理器转换为 HTTP 200 + `R.code` 的错误响应。

**CircuitBreaker 默认开启**：模块自动注入最低优先级默认值 `spring.cloud.openfeign.circuitbreaker.enabled=true`，
使 Resilience4j 熔断实际参与 Feign 调用链。关闭方式：

```yaml
ypbin:
  cloud:
    feign:
      circuitbreaker-enabled: false
```

**R 专用 Fallback 辅助**：对返回 `R<T>` 的 FeignClient，可继承 `RFeignFallbackFactory` 减少重复代码：

```java
@Component
class UserClientFallbackFactory extends RFeignFallbackFactory<UserClient> {
    @Override public UserClient create(Throwable cause) {
        return id -> fail(cause, "用户服务暂不可用");
    }
}

@FeignClient(name = "user-service", fallbackFactory = UserClientFallbackFactory.class)
public interface UserClient {
    R<UserDto> getById(Long id);
}
```

完整配置项：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `ypbin.cloud.feign.enabled` | `true` | 总开关 |
| `ypbin.cloud.feign.error-decoder-enabled` | `true` | R 错误解码 |
| `ypbin.cloud.feign.circuitbreaker-enabled` | `true` | 默认开启 CircuitBreaker |
| `ypbin.cloud.feign.propagate-headers` | `Authorization`, `X-Request-Id`, `X-Trace-Id` | 透传请求头白名单 |

### cloud-nacos — Nacos 依赖聚合

一键引入 Nacos 注册发现 + 配置中心 + LoadBalancer，版本锁定无需手动指定：

```xml
<dependency>
    <groupId>cn.ypbin.starter</groupId>
    <artifactId>ypbin-starter-cloud-nacos</artifactId>
</dependency>
```

本模块整合 Nacos 注册发现、配置中心、LoadBalancer 依赖，并提供 Nacos ConfigData 启动兜底。Nacos 自动配置由 spring-cloud-alibaba 提供，业务方按常规配置
`spring.cloud.nacos.discovery.server-addr` 和 `spring.cloud.nacos.config.server-addr` 即可。

同时，模块会处理 Spring Boot 3.x / Spring Cloud Alibaba 下的 Nacos 启动早期默认值：默认 profile、Nacos ConfigData 导入、Nacos 日志、Actuator info 与 Bean 覆盖开关。

默认注入低优先级配置，不覆盖业务方显式配置；无 active profile 时默认使用 `dev`，并按公共配置 + 环境配置 + 应用环境配置生成 Nacos 导入：

```properties
spring.profiles.default=dev
spring.config.import=optional:nacos:application.yaml,optional:nacos:application-dev.yaml
spring.cloud.nacos.config.import-check.enabled=false
nacos.logging.default.config.enabled=false
management.info.process.enabled=true
spring.main.allow-bean-definition-overriding=false
```

业务方配置了 `spring.application.name=order-service` 且 profile 为 `prod` 时，默认导入：

```properties
spring.config.import=optional:nacos:application.yaml,optional:nacos:application-prod.yaml,optional:nacos:order-service-prod.yaml
```

业务方可覆盖或关闭：

```yaml
ypbin:
  cloud:
    nacos:
      enabled: true
      default-profile-enabled: true
      default-profile: dev
      fail-on-multiple-preset-profiles: true
      application-name: order-service       # 可选：为空则不注入 spring.application.name
      application-description: 订单服务      # 可选：为空则不注入 info.desc
      service-version: 1.0.0                # 可选：为空则不注入 info.version
      config-import-enabled: true
      config-import:                        # 可选：显式指定后不再自动生成
      config-prefix: application
      config-file-extension: yaml
      include-profile-config: true
      include-application-profile-config: true
      config-import-check-enabled: false
      logging-default-config-enabled: false
      management-info-process-enabled: true
      bean-definition-overriding-enabled: false
```

定位：Nacos 相关默认值、Nacos ConfigData 导入和 Nacos 集成测试都归属于 `cloud-nacos`，不再单独拆启动模块，避免模块边界发散。

### cloud-loadbalancer — 版本灰度负载均衡

提供请求头驱动的灰度流量路由，与 Spring Cloud LoadBalancer 无缝集成。引入即替换默认轮询策略为版本灰度策略：

```xml
<dependency>
    <groupId>cn.ypbin.starter</groupId>
    <artifactId>ypbin-starter-cloud-loadbalancer</artifactId>
</dependency>
```

```yaml
ypbin:
  cloud:
    loadbalancer:
      enabled: true
      version: gray             # 当前服务灰度版本（可选）
      version-headers:          # 按顺序取第一个非空请求头作为请求灰度版本
        - X-Version
        - version
      metadata-key: version     # 服务实例 metadata 中版本字段名
      weight-metadata-key: weight
      default-weight: 1
      fallback-to-stable: true  # 灰度实例匹配不到时是否回退正式实例
      prior-ip-patterns:
        - 10.20.0.*
```

**路由规则**：
- 请求头有灰度版本 → 只选匹配 metadata 的实例，无匹配时按 `fallback-to-stable` 决定是否回退正式实例。
- 请求头无灰度版本 → 默认只选无版本标记的正式实例。
- 配置 `version` → 自动以低优先级写入 Nacos discovery metadata，无需手动维护。

### cloud-gateway — 网关通用横切能力

在 Spring Cloud Gateway（WebFlux）基础上提供开箱即用的横切能力，不预设路由规则：

```xml
<dependency>
    <groupId>cn.ypbin.starter</groupId>
    <artifactId>ypbin-starter-cloud-gateway</artifactId>
</dependency>
```

**核心能力**（默认开启）：

- **请求 ID 透传**：入口生成/复用 `X-Request-Id`，写入响应头，与 cloud-core 配合贯穿调用链。
- **身份头清洗**：默认移除客户端传入的 `X-User-Id` / `X-Tenant-Id` / `X-Dept-Id` / `X-Roles`，防身份伪造。
- **全局异常 JSON 响应**：Gateway 异常统一转为 `R` JSON（HTTP 200），与 Servlet 全局异常保持风格一致。
- **WebFlux CORS**：与 Servlet CorsFilter 独立，`ypbin.gateway.cors` 前缀单独配置。

可选能力（按需开启）：

**统一认证**：

```yaml
ypbin:
  gateway:
    auth:
      enabled: true
      exclude-paths:
        - /actuator/**
        - /swagger-ui/**
```

```java
@Component
public class JwtAuthProvider implements GatewayAuthProvider {
    @Override public Mono<GatewayAuthResult> authenticate(ServerWebExchange exchange) {
        // 校验 token，成功则返回可信身份头
        return Mono.just(GatewayAuthResult.success(Map.of("X-User-Id", userId)));
    }
}
```

**Swagger 文档聚合**：自动从 Gateway 路由表解析 `lb://service-name` 生成 Swagger UI 下拉列表：

```yaml
ypbin:
  gateway:
    swagger:
      enabled: true
```

网关需同时引入 `springdoc-openapi-starter-webflux-ui`，前端访问 `/swagger-ui.html` 即可切换下游微服务文档。

**Nacos 动态路由**：从 Nacos 配置中心加载 JSON 路由定义，变更实时刷新：

```yaml
ypbin:
  gateway:
    route:
      nacos:
        enabled: true
        data-id: gateway-routes.json
        group: DEFAULT_GROUP
```

Nacos 配置示例：

```json
[
  {
    "id": "user-service",
    "uri": "lb://user-service",
    "predicates": [{"name": "Path", "args": {"pattern": "/user/**"}}],
    "order": 0
  }
]
```

JSON 解析失败保留当前路由，Nacos 不可达保留默认路由。

### cloud-observability — 可观测性

打通日志与链路：入口读取（网关签发的）`X-Request-Id` 写入 SLF4J MDC，使同一请求的所有日志携带同一
requestId，便于跨服务聚合。核心能力零重依赖，引入即生效：

```xml
<dependency>
    <groupId>cn.ypbin.starter</groupId>
    <artifactId>ypbin-starter-cloud-observability</artifactId>
</dependency>
```

日志 pattern 引用 MDC 键即可输出：

```yaml
logging:
  pattern:
    level: "%5p [${spring.application.name:},%X{requestId:-}]"
```

配置项：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `ypbin.observability.enabled` | `true` | 总开关 |
| `ypbin.observability.request-id-header` | `X-Request-Id` | 请求 ID 头名，与网关保持一致 |
| `ypbin.observability.mdc-key` | `requestId` | 写入 MDC 的键名 |

**完整分布式链路追踪（可选）**：本模块默认只做 requestId ↔ MDC 关联。若需要 span 上报到
Zipkin / Tempo / SkyWalking，额外引入 Micrometer Tracing 桥接与 exporter，不绑定具体后端：

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

```yaml
management:
  tracing:
    sampling:
      probability: 1.0
  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces
```

微服务链路的本地端到端自测见 [`deploy/README.md`](deploy/README.md)。

### cloud-sentinel — 流量防护

在 cloud-core 的 Resilience4j（调用方 Feign 熔断）之外，提供**被调方保护**：Web 接口限流、网关限流、
热点参数限流，配合可视化 Dashboard 与 Nacos 规则热更新。两者定位互补、可共存：

```xml
<dependency>
    <groupId>cn.ypbin.starter</groupId>
    <artifactId>ypbin-starter-cloud-sentinel</artifactId>
</dependency>
```

引入即生效的能力：Sentinel Web 过滤器、`@SentinelResource` 切面、Dashboard 传输、Nacos 规则数据源
由 spring-cloud-starter-alibaba-sentinel 自动装配；本模块额外提供**被限流/降级时的统一 `R` 响应**
（`code=429`，遵循项目 HTTP 200 约定），替换 Sentinel 默认纯文本。

```yaml
spring:
  cloud:
    sentinel:
      transport:
        dashboard: localhost:8858      # 连接 Sentinel 控制台（需单独部署）
      datasource:
        flow:
          nacos:
            server-addr: localhost:8848
            data-id: ${spring.application.name}-flow-rules
            group-id: SENTINEL_GROUP
            rule-type: flow

ypbin:
  cloud:
    sentinel:
      block-message: 请求过于频繁，请稍后重试   # 被限流提示，可自定义
```

配置项：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `ypbin.cloud.sentinel.enabled` | `true` | 是否启用统一 R 限流响应 |
| `ypbin.cloud.sentinel.block-message` | 请求过于频繁，请稍后重试 | 被限流时的提示信息 |

**网关限流（可选）**：网关应用额外引入 `sentinel-spring-cloud-gateway-v6x-adapter`（本模块已声明为
optional 依赖），即可对路由维度限流，规则同样从 Nacos 热加载。

> Sentinel Dashboard 是独立进程，需单独部署（`deploy/docker-compose.yml` 已内置一个用于本地自测）。
> Resilience4j 与 Sentinel 是「调用方容错」与「被调方保护」的分工，无需二选一。

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

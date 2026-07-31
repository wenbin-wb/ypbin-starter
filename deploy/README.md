# 微服务链路自测指南

本目录提供 ypbin-starter 微服务能力的本地端到端（E2E）自测环境与步骤。ROADMAP 中 Cloud 层多次声明
「本机无 Nacos，端到端未验证」，本文档用于消除该验证缺口——在有 Docker 的环境按此操作即可完整验证
服务注册、Feign 调用、网关路由、动态路由、灰度、缓存与限流。

> 单元测试已覆盖各组件的装配与核心逻辑（`mvn test`）；本文档补充的是**需要真实 Nacos/Redis/MySQL
> 的运行时行为**，两者互补。

## 1. 启动依赖环境

```bash
docker compose -f deploy/docker-compose.yml up -d
```

启动后确认：

- Nacos 控制台：http://localhost:8848/nacos （默认账号密码 nacos / nacos）
- Sentinel 控制台：http://localhost:8858 （账号密码 sentinel / sentinel）
- Redis：localhost:6379
- MySQL：localhost:3306（库 ypbin，root / root123456）

停止与清理：

```bash
docker compose -f deploy/docker-compose.yml down      # 停止
docker compose -f deploy/docker-compose.yml down -v    # 连数据卷一起清理
```

## 2. 最小服务配置样例

任意 Spring Boot 应用引入微服务模块后，`application.yml` 参考：

```yaml
spring:
  application:
    name: user-service
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
      config:
        server-addr: localhost:8848
        file-extension: yaml
  data:
    redis:
      host: localhost
      port: 6379

ypbin:
  cloud:
    loadbalancer:
      version: v1          # 本实例灰度版本，会自动写入 Nacos metadata
```

## 3. 验证清单

### 3.1 服务注册发现

1. 启动两个不同 `spring.application.name` 的服务。
2. 打开 Nacos 控制台「服务管理 → 服务列表」，确认实例已注册、metadata 含 `version`。

### 3.2 Feign 调用 + 请求头透传

1. 服务 A 声明 `@FeignClient(name = "user-service")` 调用服务 B。
2. 服务 A 入口带 `Authorization` / `X-Request-Id`，确认服务 B 能收到同样的头。
3. 关掉服务 B，确认服务 A 的 Feign 调用触发 CircuitBreaker，返回统一 `R.fail`（而非裸异常）。

### 3.3 网关路由 + 横切

1. 启动网关（引入 `ypbin-starter-cloud-gateway`），配置一条 `lb://user-service` 路由。
2. 经网关访问下游，确认响应头带 `X-Request-Id`。
3. 客户端故意传 `X-User-Id`，确认到达下游时已被网关清洗（身份头防伪造）。
4. 访问不存在的路由，确认返回统一 `R` JSON（而非 WebFlux 默认错误页）。

### 3.4 灰度路由

1. 启动 user-service 两个实例，分别配 `ypbin.cloud.loadbalancer.version=v1` 和 `v2`。
2. 请求带 `X-Version: v2`，确认只路由到 v2 实例；不带版本头时只路由到无版本标记的正式实例。

### 3.5 Nacos 动态路由

1. 网关开启 `ypbin.gateway.route.nacos.enabled=true`。
2. 在 Nacos 新建配置 `gateway-routes.json`（内容见根 README 的 cloud-gateway 章节）。
3. 修改该配置并发布，确认网关无需重启即生效（观察日志 `Nacos dynamic routes refreshed`）。

### 3.6 日志链路关联

1. 引入 `ypbin-starter-cloud-observability`，日志 pattern 加入 `%X{requestId}`。
2. 确认同一请求的所有日志都带同一个 requestId，且与网关下发的 `X-Request-Id` 一致。

### 3.7 Sentinel 限流 + 规则热更新

1. 服务引入 `ypbin-starter-cloud-sentinel`，配置连接 Dashboard 与 Nacos 规则源：

   ```yaml
   spring:
     cloud:
       sentinel:
         transport:
           dashboard: localhost:8858
         datasource:
           flow:
             nacos:
               server-addr: localhost:8848
               data-id: ${spring.application.name}-flow-rules
               group-id: SENTINEL_GROUP
               rule-type: flow
   ```

2. 启动服务并访问任意接口，确认 Sentinel 控制台「实时监控」出现该服务的 QPS 曲线。
3. 在 Nacos 新建配置 `${appName}-flow-rules`（group `SENTINEL_GROUP`），写入限流规则 JSON：

   ```json
   [{"resource":"/api/demo","grade":1,"count":2}]
   ```

4. 高频访问 `/api/demo`（每秒 > 2 次），确认被限流时返回**统一 `R` JSON**（`code=429`），而非 Sentinel 默认纯文本。
5. 在 Nacos 修改 `count` 并发布，确认规则秒级生效、无需重启。

## 4. 完整分布式链路追踪（可选）

`ypbin-starter-cloud-observability` 默认只做 requestId ↔ MDC 关联（零重依赖）。若需要真正的分布式
链路追踪（span 上报到 Zipkin / Tempo / SkyWalking 等），业务方额外引入 Micrometer Tracing 桥接与
exporter：

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

不绑定具体后端：换 Zipkin/Tempo/其它 OTLP 兼容后端只需改 endpoint。



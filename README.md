<div align="center">

# ⚙️ ypbin-starter

**为企业级 Spring Boot 应用打造的系统级基建**

> 业务系统只写业务，基建交给 starter。单体与微服务同源 · 约定优于配置 · 可覆盖可扩展 · 已发布 Maven Central

**Java 21 · Spring Boot 4.1 · 35 个模块**

[![CI](https://github.com/wenbin-wb/ypbin-starter/actions/workflows/ci.yml/badge.svg)](https://github.com/wenbin-wb/ypbin-starter/actions/workflows/ci.yml)
[![Coverage](https://img.shields.io/badge/Coverage-JaCoCo%20Verified-brightgreen.svg)](https://github.com/wenbin-wb/ypbin-starter/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/cn.ypbin/ypbin-starter-bom?label=Maven%20Central&color=blue)](https://central.sonatype.com/artifact/cn.ypbin/ypbin-starter-bom)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen.svg)](https://spring.io/projects/spring-boot)

[快速开始](#-快速开始) · [模块总览](#-模块总览) · [设计取舍](#-设计取舍) · [对接方式](#-业务系统如何对接) · [文档](https://ypbin.cn/guide/starter/)

</div>

---

## ✨ 效果预览

<div align="center">

![ypbin-admin 运行概览](https://ypbin.cn/screenshots/admin-ui/dashboard.png)
*基于 ypbin-starter 构建的完整后台：ypbin-admin*

</div>

## 📌 简介

每个团队起新项目，都要把统一响应、鉴权、缓存、多租户、数据权限、日志审计、微服务治理这些东西重写一遍——同样的坑，不同的踩法。`ypbin-starter` 把这层「系统级基建」一次性做对、做透，沉淀成一组可独立引入的 Spring Boot Starter：**业务系统只写业务，基建交给 starter。**

它不是又一个把开源库简单包一层的脚手架。每一个模块的边界、每一个默认值、每一处扩展点的位置，都是在真实生产场景里权衡过的结果——**安全默认优先于便利、扩展点优先于配置项、约定优先于文档。**

## 🧭 目录

- [核心特性](#-核心特性)
- [设计取舍](#-设计取舍)
- [技术栈](#-技术栈)
- [快速开始](#-快速开始)
- [模块总览](#-模块总览)
- [业务系统如何对接](#-业务系统如何对接)
- [单体 vs 微服务](#-单体-vs-微服务)
- [版本与兼容性](#-版本与兼容性)
- [贡献与许可证](#-贡献与许可证)

## ⚡ 核心特性

### 🏗️ 一套架构，单体微服务同源

基础层不依赖 Spring Cloud，单体直接用、微服务叠加治理层，对外契约完全一致，前端无感知。避免了「单体一套、微服务另起炉灶」的常见撕裂。

### 🔌 能力即插即拔，随时被接管

所有能力 Bean 一律 `@ConditionalOnMissingBean` + `@ConditionalOnProperty`——你定义同类型 Bean 就覆盖默认实现，改一行配置就关停整个模块。starter 定义抽象与默认行为，业务方按需注入自己的实现，**不改 starter 一行源码**。

### 🛡️ 难做对的地方，替你做对了

| 容易踩的坑 | ypbin 的做法 |
|---|---|
| 缓存击穿 / 穿透 / 雪崩 | 三重防护 + 多级缓存（Caffeine + Redis + Pub/Sub 失效广播） |
| 列表序列化 N+1 | `@RefText` 强制批量取数 + 切面预加载，业务零改动 |
| 密码错误锁定 TTL 竞态 | 内建原子实现，无窗口漏洞 |
| 接口签名重放 | 时钟偏移容忍 + 重放窗口，MD5/HMAC 可配 |
| 异步线程上下文丢失 | `@Async` 统一接管 + 上下文透传 |
| 字段敏感信息泄露 | `@Sensitive` 脱敏、字段加密（AES-GCM / 国密） |

### 🏭 工程治理

- `${revision}` + flatten 统一版本，对外 BOM 一键导入
- spotless 强制代码风格与 license 头
- 已发布 Maven Central，遵循语义化版本
- JaCoCo 覆盖率门禁，对抗性代码审查

## 🎯 设计取舍

> 好的框架不在于它做了什么，而在于它拒绝做什么。

- **认证选 Sa-Token，而非自研 JWT 或 Spring Security。** 后台管理的诉求是「会话、踢人、续期、多端」，Spring Security 的过滤器链对此过重，自研 JWT 又要重造轮子。需要开放平台级 access+refresh 双令牌时才上 OAuth2，不为极少数场景绑架全局复杂度。
- **扩展点强制批量，从 API 层面消灭 N+1。** `@RefText` 的数据源接口一次收一组 ID、返回映射，业务方想写出 N+1 都难；列表翻译由切面在序列化前自动预加载，业务代码零改动。**把正确的做法设成唯一的做法。**
- **异常统一 HTTP 200 + 业务码。** 前后端交互中 HTTP 状态码语义混乱是常见摩擦源，本项目约定所有业务异常走 200、由 `R.code` 区分，前端只需一套拦截逻辑。这是明确的取舍，不是疏忽。
- **数据权限只拦标注方法，不做全局无差别切面。** 全局拦截会让定时任务、登录校验等内部查询悄悄丢数据；`@DataPermission` 显式开启，边界清晰、可预期。
- **starter 只给运行时与扩展点，不碰业务表。** 客户端管理、字典、在线用户、任务调度——表结构和页面归业务系统，starter 提供抽象和默认（读配置）实现。职责边界一刀切干净，**升级 starter 不会动业务数据**。

## 🧱 技术栈

| 领域 | 选型 |
|---|---|
| 语言 / 框架 | Java 21 · Spring Boot 4.1.0 |
| AI | Spring AI 2.0.0 |
| 认证 | Sa-Token 1.46.0 |
| ORM | MyBatis-Plus 3.5.17 |
| 缓存 | Redis + Caffeine 多级缓存 |
| 对象存储 | AWS SDK v2（S3 兼容） |
| API 文档 | SpringDoc OpenAPI 3.1.0 |
| Excel | Apache Fesod 2.0.2-incubating |
| 验证码 | tianai-captcha 1.5.5（滑块/旋转/点选/拼接） |
| 加解密 | AES-GCM / 国密 SM2·SM3·SM4（BouncyCastle 1.85.2） |
| 微服务 | Spring Cloud 2025.1.2 + Gateway / OpenFeign / LoadBalancer / Nacos |
| 熔断降级 | Resilience4j |

## 🚀 快速开始

### 1. 引入 BOM 统一版本

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>cn.ypbin</groupId>
            <artifactId>ypbin-starter-bom</artifactId>
            <version>1.4.1</version>
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
        <groupId>cn.ypbin</groupId>
        <artifactId>ypbin-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>cn.ypbin</groupId>
        <artifactId>ypbin-starter-data</artifactId>
    </dependency>

    <!-- 微服务架构 -->
    <dependency>
        <groupId>cn.ypbin</groupId>
        <artifactId>ypbin-starter-cloud-gateway</artifactId>
    </dependency>
    <dependency>
        <groupId>cn.ypbin</groupId>
        <artifactId>ypbin-starter-cloud-nacos</artifactId>
    </dependency>
    <dependency>
        <groupId>cn.ypbin</groupId>
        <artifactId>ypbin-starter-cloud-core</artifactId>
    </dependency>
</dependencies>
```

引入即自动装配，无需额外注解。

## 📦 模块总览

### 基础能力层（单体 / 微服务共用）

| 模块 | 职责 | 配置前缀 |
|---|---|---|
| `core` | 统一响应 R、异常体系、通用枚举、SpringUtils、上下文透传 | — |
| `json` | Jackson 统一序列化（时间格式、大数字转字符串）、`@Sensitive` 脱敏 | `ypbin.json` |
| `web` | 全局异常处理、CORS、404 统一 JSON、XSS 过滤 | `ypbin.web` |
| `data` | MyBatis-Plus 增强、`BaseEntity`、字段加密、雪花 ID | `ypbin.data` |
| `cache` | Redis 缓存 + 三重防护 + 多级缓存 | `ypbin.cache` |
| `security` | Sa-Token 封装、登录客户端、密码策略 | `ypbin.security` |
| `log` | `@Log` 操作日志 + 访问日志切面 | `ypbin.log` |
| `tools` | 限流 `@RateLimit`、幂等 `@Idempotent`、分布式锁、加解密 | `ypbin.tools` |
| `async` | 统一线程池、`@Async` 接管、上下文透传 | `ypbin.async` |
| `job` | 动态定时任务、分布式锁防重、执行监听 | `ypbin.job` |
| `excel` | 注解驱动导入导出 | — |
| `captcha` | 行为验证码（滑块/旋转/点选/拼接） | `ypbin.captcha` |
| `messaging` | 邮件、WebSocket、SSE、MQTT 统一推送 | `ypbin.mail` 等 |
| `sensitive-words` | DFA 敏感词检测/替换 | `ypbin.sensitive-words` |
| `i18n` | 多语言 | `ypbin.i18n` |
| `api-crypto` | 接口请求解密/响应加密 | `ypbin.api-crypto` |
| `sign` | 接口签名四件套、防重放 | `ypbin.sign` |
| `social` | 第三方登录 | `ypbin.social` |
| `ai` | Spring AI 对话（流式/非流式）、多轮记忆、RAG | `ypbin.ai` |
| `storage` | 本地 + S3 兼容对象存储 | `ypbin.storage` |
| `api-doc` | SpringDoc OpenAPI 元信息 | `ypbin.api-doc` |

### 扩展能力层

| 模块 | 职责 | 配置前缀 |
|---|---|---|
| `extension-tenant` | 行级租户隔离、`@TenantIgnore` | `ypbin.tenant` |
| `extension-crud` | 通用控制器/服务基类，防 Over-Posting | — |
| `extension-datapermission` | 行级数据范围过滤、`@DataPermission` 门控 | `ypbin.data-permission` |

### 微服务层

| 模块 | 职责 | 配置前缀 |
|---|---|---|
| `cloud-core` | OpenFeign 请求头透传、错误解码、熔断兜底 | `ypbin.cloud.feign` |
| `cloud-nacos` | Nacos 注册发现 + 配置中心 + 灰度聚合 | `ypbin.cloud.nacos` |
| `cloud-loadbalancer` | 版本灰度路由、权重随机 | `ypbin.cloud.loadbalancer` |
| `cloud-observability` | X-Request-Id 全链路、Micrometer Tracing | `ypbin.observability` |
| `cloud-sentinel` | Sentinel 限流、被拒统一 R、规则热更新 | `ypbin.cloud.sentinel` |
| `cloud-gateway` | Gateway 横切（CORS/异常/鉴权/动态路由） | `ypbin.gateway` |

### 聚合模块

| 模块 | 职责 |
|---|---|
| `app-web` | 一次引入单体 Web 常用基础能力 |
| `app-cloud` | 在 app-web 基础上叠加微服务能力 |
| `bom` / `dependencies` | 统一版本管理 |

## 🔗 业务系统如何对接

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
| `JobHandler`(+`@YpbinJob`) / `JobExecutionListener` | job | 可选 | 定时任务执行体 / 执行日志落库 |
| `IpLocationResolver` | log | 可选 | 操作日志 IP 归属地解析 |

除「必须」项外均有默认实现（多为读配置文件），想接数据库/后台配置时才覆盖。所有能力 Bean 均 `@ConditionalOnMissingBean`，定义同类型 Bean 即覆盖。

> 💡 完整的企业级落地范例见配套项目 **[ypbin-admin](https://github.com/wenbin-wb/ypbin-admin)**：基于本 starter 构建的后台管理系统，演示了 RBAC、字典、在线用户、任务调度等扩展点如何对接数据库与前端。

## 🏢 单体 vs 微服务

两套后端共用 L1 基础层，对外契约一致（详见 [CONTRACT.md](CONTRACT.md)），前端可复用同一套调用逻辑。

| | 单体应用 | 微服务应用 |
|---|---|---|
| 起步依赖 | `ypbin-starter-app-web` | 各业务服务引 `ypbin-starter-app-cloud` |
| 网关 | 无 | 独立部署，引 `ypbin-starter-cloud-gateway` |
| 鉴权 | 应用内 sa-token | 网关统一校验 + 内部身份头透传 |
| 对前端 | 契约一致 | 契约一致 |

## 📊 版本与兼容性

| ypbin-starter | Spring Boot | Spring Cloud | JDK |
|---|---|---|---|
| 1.0.x | 3.5.x | 2025.0.x | 17+ |
| 1.4.x+ | 4.1.x | 2025.1.x | 21+ |

遵循[语义化版本](https://semver.org/lang/zh-CN/)。版本变更详见 [CHANGELOG.md](CHANGELOG.md)。

## 🤝 贡献与许可证

### 本地构建

```bash
# 编译并安装到本地仓库（verify 阶段自动执行代码风格校验）
mvn clean install

# 一键格式化代码（统一 license 头、import 顺序、去除多余空白）
mvn com.diffplug.spotless:spotless-maven-plugin:apply
```

发布到 Maven Central 的完整流程与版本迭代规范见 [RELEASING.md](RELEASING.md)。

### 许可证

基于 [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) 开源，可自由用于商业项目。

Copyright © 2024-present wenbin

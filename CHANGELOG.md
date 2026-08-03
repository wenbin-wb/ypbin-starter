# 更新日志

本项目遵循[语义化版本](https://semver.org/lang/zh-CN/)：`主版本.次版本.修订号`。
- 主版本：不兼容的 API 变更
- 次版本：向后兼容的功能新增
- 修订号：向后兼容的问题修复

格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)。

## [未发布]

## [1.0.0] - 2026-08-01

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

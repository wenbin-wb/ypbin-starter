# __APP_NAME__

由 ypbin-starter 脚手架生成（`monolith` 预设：单体应用）。

## 运行

1. 准备 MySQL 与 Redis，并按需修改 `src/main/resources/application.yml` 的连接信息与口令。
2. 执行建表 DDL（示例模块用 `demo` 表，可仿照 ypbin-admin 的 `deploy/sql`）。
3. 启动：

```bash
mvn test
mvn spring-boot:run     # 默认端口 __PORT__
```

## 已引入能力

| 能力 | 模块 |
|---|---|
| 统一响应 / 全局异常 / CORS / XSS | `ypbin-starter-web` |
| JSON（时间格式、Long 转字符串） | `ypbin-starter-json` |
| MyBatis-Plus（分页、多租户、逻辑删除） | `ypbin-starter-data` |
| 缓存（本地 + Redis、防击穿/穿透/雪崩） | `ypbin-starter-cache` |
| 认证鉴权（Sa-Token）与密码策略 | `ypbin-starter-security` |
| 工具（分布式锁、限流、幂等、国密） | `ypbin-starter-tools` |
| 接口文档 | `ypbin-starter-api-doc` |

# __APP_NAME__

由 ypbin-starter 脚手架生成（`api-only` 预设）。

## 运行

```bash
mvn test                 # 单元测试
mvn spring-boot:run      # 启动（默认端口 __PORT__）
```

- 示例接口：`GET /demo/ping`
- 接口文档：`/swagger-ui.html`

## 约定

- 统一响应体 `R<T>`：HTTP 恒 200，由 `R.code` 区分成功/失败；业务异常抛 `BusinessException`。
- Controller 只做路由与编排，业务逻辑放 Service。
- 每个类需含类级 Javadoc（`@author` + `@since`）。
- 每个写操作方法应带 `@SaCheckPermission` / `@Idempotent` / `@Log`（引入对应模块后）。

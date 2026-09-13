# __APP_NAME__

由 ypbin-starter 脚手架生成（`microservice` 预设）。

## 模块

| 模块 | 职责 |
|---|---|
| `__ARTIFACT_ID__-api` | **服务契约**：DTO 与 Feign 客户端接口，调用方依赖它，不依赖实现 |
| `__ARTIFACT_ID__-service` | **服务实现**：Web + 安全 + 注册配置 + Feign 增强 |

## 运行

```bash
mvn test                 # 两个模块的单元测试
mvn -pl __ARTIFACT_ID__-service spring-boot:run   # 启动服务（需 Nacos）
```

## 跨服务调用

调用方引入 `__ARTIFACT_ID__-api`，注入 `DemoClient` 即可：

```java
@RequiredArgsConstructor
public class SomeService {
    private final DemoClient demoClient;
    public DemoDto load(Long id) { return demoClient.getById(id).getData(); }
}
```

> 服务间调用默认注入 Feign 连接/读取超时（5s/10s）与熔断；身份头透传在直连场景下建议
> 配置 `ypbin.cloud.feign.trusted-source-token` 启用来源校验。

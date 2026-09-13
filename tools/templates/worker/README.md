# __APP_NAME__

由 ypbin-starter 脚手架生成（`worker` 预设：后台任务进程，非 Web）。

## 运行

```bash
mvn test
mvn spring-boot:run
```

任务进程默认不启动 Web 容器（`spring.main.web-application-type=none`）。

## XXL-JOB

`ypbin.xxl-job.enabled` 默认 `false`。部署好调度中心后改为 `true` 并设置
`admin-addresses`，`@XxlJob("demoJob")` 即可被调度。

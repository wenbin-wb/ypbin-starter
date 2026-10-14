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

> ⚠️ 调度中心（xxl-job-admin）与执行器（本模块）必须**同版本成对升级**。xxl-job 3.5.0 起
> AccessToken 为执行器维度且不可为空：必须显式设置 `ypbin.xxl-job.access-token`，且与 admin 中
> 同名 `appname` 的 `access_token` 完全一致，否则执行器启动即失败。

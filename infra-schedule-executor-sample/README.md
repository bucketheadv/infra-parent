# infra-schedule-executor-sample

独立部署的执行器示例。不连接调度库，仅通过 HTTP 向调度中心上报心跳并接收任务。

```bash
export SCHEDULE_ADMIN_ADDRESS='http://127.0.0.1:18080'
# 分机部署时改为调度中心可达的本机对外地址，例如 http://10.0.0.12:18081
export SCHEDULE_EXECUTOR_ADDRESS='http://127.0.0.1:18081'
export SCHEDULE_ACCESS_TOKEN='...'
mvn -pl infra-schedule-executor-sample -am spring-boot:run
```

在管理端创建 `handler` 为 `sampleEchoHandler` 的任务即可验证。调度中心与执行器的 `SCHEDULE_ACCESS_TOKEN` 必须一致。

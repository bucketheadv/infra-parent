# infra-schedule-executor-sample

执行器接入实例，启动后会向 `SCHEDULE_ADMIN_ADDRESS` 上报自身地址，并接受管理端的任务调用。

```bash
export SCHEDULE_ADMIN_ADDRESS='http://127.0.0.1:18080'
export SCHEDULE_ACCESS_TOKEN='...'
mvn -pl infra-schedule-executor-sample -am spring-boot:run
```

在管理端创建 `handler` 为 `sampleEchoHandler` 的任务即可验证执行。管理端和执行器的 `SCHEDULE_ACCESS_TOKEN` 必须相同。

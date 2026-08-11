# infra-schedule-admin

独立调度管理后台。先执行 `infra-schedule` 模块的 `V1__infra_schedule.sql`，再设置环境变量后启动：

```bash
export SCHEDULE_DB_URL='jdbc:mysql://127.0.0.1:3306/infra_schedule?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai'
export SCHEDULE_DB_USERNAME='infra_schedule'
export SCHEDULE_DB_PASSWORD='...'
export SCHEDULE_ACCESS_TOKEN='...'
export SCHEDULE_ADMIN_ACCESS_TOKEN='...'
mvn -pl infra-schedule-admin -am spring-boot:run
```

管理接口位于 `/infra/schedule/**`。到期任务通过 MySQL 8 的 `SELECT ... FOR UPDATE SKIP LOCKED` 按页领取，`dispatch-batch-size` 控制页大小，`dispatch-max-pages` 限制单轮最多页数。

除执行器心跳外，管理接口必须携带 `X-Infra-Schedule-Admin-Token`，其值为 `SCHEDULE_ADMIN_ACCESS_TOKEN`。

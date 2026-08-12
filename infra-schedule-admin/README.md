# infra-schedule-admin

独立调度中心与管理后台。依赖 `infra-schedule` 提供领域模型与持久化，本模块注册调度扫描、管理 REST 与 Thymeleaf 页面。

先执行本模块 `src/main/resources/db/mysql/schema/20260812010000_schedule_schema.sql`（或 `db/migration/` 增量脚本），再设置环境变量后启动：

```bash
export SCHEDULE_DB_URL='jdbc:mysql://127.0.0.1:3306/infra_schedule?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai'
export SCHEDULE_DB_USERNAME='infra_schedule'
export SCHEDULE_DB_PASSWORD='...'
export SCHEDULE_ACCESS_TOKEN='...'
export SCHEDULE_ADMIN_ACCESS_TOKEN='...'
mvn -pl infra-schedule-admin -am spring-boot:run
```

纯执行器只需依赖 `infra-schedule`，**不要配置调度库 DataSource**；心跳与日志均通过 HTTP 上报调度中心。

管理接口位于 `/infra/schedule/**`。生产环境建议设置：
- `SCHEDULE_EXECUTOR_AUTH_ENABLED=true` 与 `SCHEDULE_ACCESS_TOKEN`（调度中心调用执行器）
- `SCHEDULE_ADMIN_AUTH_ENABLED=true` 与 `SCHEDULE_ADMIN_ACCESS_TOKEN`（管理页面调用 API）

到期任务通过 MySQL 8 的 `SELECT ... FOR UPDATE SKIP LOCKED` 按页领取，`dispatch-batch-size` 控制页大小，`dispatch-max-pages` 限制单轮最多页数。

除执行器心跳/日志回调外，管理接口需携带 `X-Infra-Schedule-Admin-Token`（`auth-enabled=true` 时）。

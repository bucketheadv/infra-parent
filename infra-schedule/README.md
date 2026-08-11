# infra-schedule

Kotlin 实现的可嵌入式分布式任务调度中心，提供 Cron/固定间隔调度、MyBatis-Flex 持久化租约抢占、执行器路由、分片广播、阻塞策略、超时与重试、执行日志及可选管理 REST 接口。

## 启用

新建库执行 `src/main/resources/db/mysql/schema/20260812010000_schedule_schema.sql`；已有库按 `src/main/resources/db/migration/` 增量升级后启用：

```yaml
infra:
  schedule:
    enabled: true
    management:
      enabled: false
```

管理端点为 `/infra/schedule/**`，默认关闭；开启后必须由接入应用配置 Spring Security 或网关鉴权。任务处理器实现 `ScheduleJobHandler` 并标记 `@ScheduleHandler("handlerName")`。

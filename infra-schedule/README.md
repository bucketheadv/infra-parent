# infra-schedule

Kotlin 实现的分布式任务调度**执行器 starter**（无数据库依赖）：Handler 执行、HTTP 执行端点、心跳与日志上报。

**调度中心**（MySQL 持久化、任务扫描、管理 REST、页面）在 **`infra-schedule-admin`** 中单独部署。

## 纯执行器

```yaml
infra:
  schedule:
    enabled: true
    executor:
      enabled: true
      address: http://10.0.0.12:18081      # 调度中心可达地址
      admin-address: http://10.0.0.10:18080
      access-token: ${SCHEDULE_ACCESS_TOKEN}
      auth-enabled: true
```

无需配置 `spring.datasource`。任务处理器实现 `ScheduleJobHandler` 并标记 `@ScheduleHandler("handlerName")`。

## 调度中心

见 `infra-schedule-admin` 模块。

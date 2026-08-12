# infra-schedule Code Review 重点检查点

面向 **infra-schedule**（执行器 starter）与 **infra-schedule-admin**（调度中心）的变更评审。按优先级排列：P0 必须阻断合并，P1 强烈建议修复，P2 建议优化。

---

## 1. 架构与模块边界（P0）

| 检查点 | 通过标准 | 常见反例 |
|--------|----------|----------|
| 模块职责 | 调度/持久化/扫描仅在 admin；schedule 模块无 DB 依赖 | schedule 又引入 `infra-db` 或 Flyway |
| 自动配置顺序 | Admin 持久化在 `DataSourceAutoConfiguration` 之后；主配置在持久化之后 | 嵌套 `@ConditionalOnBean(DataSource)` 导致仓储 Bean 未注册 |
| 仓储 Bean | `management.enabled=true` 时**必须**注册 Flex 仓储，**不得**注册 InMemory | InMemory 先于 Flex 注册，页面查不到库数据 |
| 执行器部署 | 纯执行器不配 DataSource；`management.enabled=false` | sample 误配调度库 |
| 双向 HTTP | Admin 调执行器 run/beat/cancel；执行器调 Admin 心跳/日志回调 | 假设同 JVM in-process 为唯一路径 |

**必看文件**

- `infra-schedule/.../InfraScheduleAutoConfiguration.kt` — InMemory 仅在 `management.enabled=false`
- `infra-schedule-admin/.../InfraScheduleAdminPersistenceAutoConfiguration.kt` — MySQL 仓储与 MapperScan
- `infra-schedule-admin/.../InfraScheduleAdminAutoConfiguration.kt` — 调度链 Bean 依赖顺序

---

## 2. 分布式调度与并发（P0）

| 检查点 | 通过标准 | 风险 |
|--------|----------|------|
| 任务领取 | `lockDuePage` 使用 `FOR UPDATE SKIP LOCKED`；同一事务内写租约 | 无 SKIP LOCKED → 多 Admin 互等锁 |
| 租约释放 | `completeSchedule` / `releaseClaim` 仅释放本 `scheduler-id` 持有的租约 | 误清他人租约 → 重复调度 |
| 推进 next_trigger | 领取后立即 `completeSchedule`，不等待执行结束 | 若改为执行完再推进 → Cron 漂移 |
| 重叠触发 | 文档/代码一致：重叠由**执行器阻塞策略**处理 | 在调度中心加全局 mutex → 与 xxl-job 语义背离 |
| scheduler-id | 集群内唯一、稳定（非每次启动随机 UUID） | 随机 ID → 租约归属混乱、排查困难 |
| 虚拟线程池 | worker / attempt 线程池有 `destroyMethod=shutdown` | 泄漏导致进程无法优雅退出 |

**必看文件**

- `admin/persistence/mapper/ScheduleMappers.kt` — `lockDuePage` SQL
- `admin/persistence/FlexScheduleRepositories.kt` — `claimDueJobs`、`completeSchedule`
- `admin/service/ScheduleService.kt` — `dispatchDueJobs`、`submit`

---

## 3. 僵尸日志与探活（P0 / P1）

| 优先级 | 检查点 | 通过标准 |
|--------|--------|----------|
| P0 | 回收前探活 | `reapStaleRunningLogs` / `reconcileActiveLogs` 必须先 `isLogStillAlive`，不可直接标 LOST |
| P0 | 探活不可达 | 网络失败视为「不可观测」→ 可回收；仍在跑 → 必须跳过 |
| P1 | 阈值下限 | `staleAfterMillis` 取 `max(配置, claimLease, 60s)`，避免过短误杀 |
| P1 | 回收不杀进程 | LOST 只更新 DB；除 timeout/cancel 外不 remote kill |
| P1 | 调度前 reconcile | 每次 trigger 前清理该 job 不可观测的 QUEUED/RUNNING |
| P2 | 历史日志 | 无自动 DELETE；若新增清理任务需独立 PR + 运维文档 |

**必看文件**

- `admin/service/ScheduleService.kt` — `reapStaleRunningLogs`、`isLogStillAlive`、`reconcileActiveLogs`
- `admin/core/HttpScheduleCancelClient.kt` — `isRunning` / `cancel`
- `admin/persistence/mapper/ScheduleMappers.kt` — `findStaleRunningCandidates`、`markLostIfActive`

---

## 4. 路由策略（P0 / P1）

| 策略 | Review 要点 |
|------|-------------|
| ROUND | 游标存 MySQL（`infra_schedule_route_cursor`）；cursorKey 为 `executor:{id}` 或 group |
| LFU / LRU | 统计 UPSERT 到 `infra_schedule_route_stat`；多 Admin 共享 |
| CONSISTENT_HASH | 环上键用**原始地址**（`hashRingKey`），非 normalize 后 URL；MD5 + 100  vnode |
| FAILOVER | 有序 beat；区分「心跳失败」与「节点不可达」文案 |
| BUSYOVER | idleBeat 带 jobId；与 SERIAL 长任务组合时易全忙 — 需在任务配置层说明 |
| SHARDING_BROADCAST | 返回全部候选；`execute` 对每个 shard 独立重试/日志 |
| 地址解析 | `host:port` normalize 为 `http://...`；无效地址 skip 并打 warn |
| 枚举兼容 | `RouteStrategy.parse` 兼容 `ROUND_ROBIN`、`BROADCAST` |

**反模式**

- 路由状态放 InMemory（多 Admin 不一致）
- 修改 HASH 算法或环键规则未写迁移说明
- FAILOVER/BUSYOVER 在 `applyRoute` 内只返回一个节点（应返回有序列表供探活）

**必看文件**

- `infra-schedule/.../ExecutorRegistry.kt` — `applyRoute`、`expandAddresses`
- `infra-schedule/.../RouteHash.kt`
- `admin/service/ScheduleService.kt` — `resolveExecutors`、`selectFailover`、`selectBusyover`

---

## 5. 阻塞策略（P0 / P1）

| 检查点 | 通过标准 |
|--------|----------|
| 生效位置 | 仅在执行器 `ExecutorTaskTracker` / `ExecutorJobThread`，非调度中心 |
| SERIAL | 同 jobId 队列串行；QUEUED → RUNNING 状态流转正确 |
| DISCARD_LATER | 忙碌时返回 `JobExecutionResult.discarded`；日志 SKIPPED |
| COVER_EARLY | `stopForCover` 后重建 JobThread；旧 log 终态不遗漏 |
| blockStrategy 传递 | `JobExecutionContext.blockStrategy` 从任务定义带到 `/run` |
| 与调度重叠 | 调度不等待上次完成；Reviewer 确认变更是增强而非改变此语义 |

**必看文件**

- `infra-schedule/.../ExecutorTaskTracker.kt`
- `infra-schedule/.../ExecutorJobThread.kt`
- `admin/service/ScheduleService.kt` — `executeWithRetry` 对 `discarded` 的处理

---

## 6. 常驻任务 resident（P1）

| 检查点 | 说明 |
|--------|------|
| 语义边界 | resident **不**跳过调度、**不**禁用重叠触发；主要影响丢弃时的日志文案 |
| 与 DISCARD_LATER | 丢弃 message 为「常驻任务丢弃后续触发」 |
| 与僵尸回收 | 与普通任务相同探活逻辑；长常驻需调大 `stale-running-log-millis` |
| 文档一致 | 模型注释与实现一致（若承诺「不保留 SKIPPED 日志」则必须有删除逻辑） |
| DISABLED | 停任务不杀已在跑实例 — Review 时确认未引入「强杀」副作用 |

---

## 7. 持久化与数据一致性（P0）

| 检查点 | 通过标准 |
|--------|----------|
| 任务更新 | `save` 使用 `update(entity, false)` 写入 null 字段（cron 变更、next_trigger 清空） |
| 日志主键 | 新增 log 时 id=0 必须走 DB 自增，禁止 insert 显式 0 |
| 终态更新 | `updateIfRunning` / `markLostIfActive` 带 status 条件，防覆盖终态 |
| 跨模块类型 | 跨模块 public 属性不做 smart cast；用局部变量 |
| 事务 | `claimDueJobs` 锁行与写租约在同一 `@Transactional` |
| 迁移脚本 | 新字段/表有 V{n}__*.sql 与 schema 快照同步 |

---

## 8. HTTP 协议与鉴权（P0 / P1）

| 检查点 | 通过标准 |
|--------|----------|
| JSON 头 | 客户端强制 `Accept` / `Content-Type: application/json` |
| Token | Admin→Executor：`X-Infra-Schedule-Access-Token`；Admin API：`X-Infra-Schedule-Admin-Token` |
| auth 401 | 鉴权失败有明确日志，非 silent fail |
| 执行器端点 | run / beat / idleBeat / running / cancel 与 `ScheduleWebPaths` 一致 |
| 日志回调 | started / finish / handle-append 走 Admin API；执行器 `ScheduleLogReporter` 配对 |

---

## 9. 配置与安全（P1）

| 检查点 | 通过标准 |
|--------|----------|
| 密钥 | Token 走环境变量，不写死 application.yml |
| 默认值 | 本地 `auth-enabled: false` 可接受；生产必须显式开启 |
| Admin 端口 | 与业务 8080 隔离（默认 18080） |
| 执行器 address | 非回环地址（跨机部署时） |
| 配置项文档 | 新增 `InfraScheduleProperties` 字段同步 README / CHECKLIST |

---

## 10. 前端与管理面（P2）

| 检查点 | 通过标准 |
|--------|----------|
| Admin Token | `ScheduleAdminPageModelAdvice` 注入；`dashboard.js` 自动带头 |
| API 路径 | 与 `ScheduleWebPaths` 单点维护，无散落字面量 |
| 错误展示 | 401/403 与空数据区分，避免「有库无数据」误判 |
| 执行器在线 | 页面心跳超时与 `heartbeatTimeoutMillis` 默认值一致 |

---

## 11. Review 快速决策树

```
变更是否动到自动配置？
  ├─ 是 → 检查 Bean 顺序、ConditionalOn*、management/executor 开关
  └─ 否 → 继续

变更是否动到 claim/dispatch？
  ├─ 是 → 检查 SKIP LOCKED、租约、next_trigger 推进时机
  └─ 否 → 继续

变更是否动到路由？
  ├─ 是 → 检查多 Admin 共享状态、HASH 环键、FAILOVER/BUSYOVER 探活
  └─ 否 → 继续

变更是否动到执行器 run 路径？
  ├─ 是 → 检查 blockStrategy、JobThread、日志回写
  └─ 否 → 继续

变更是否动到日志状态机？
  └─ 是 → 检查探活、LOST 条件、updateIfRunning 乐观条件
```

---

## 12. PR 描述建议附带项

合并 PR 时建议说明：

- [ ] 是否变更调度语义（重叠触发 / 推进时机）
- [ ] 是否变更路由/HASH（影响已有任务落点）
- [ ] 是否需要新 DB 迁移及执行顺序
- [ ] 是否需调整 `stale-running-log-millis` 等运维参数
- [ ] Admin / Executor 是否需同步发布（协议或配置不兼容时）

---

## 相关文档

- [SCHEDULE_CHECKLIST.md](./SCHEDULE_CHECKLIST.md) — 上线与巡检检查清单
- [../README.md](../README.md) — 模块启动与环境变量

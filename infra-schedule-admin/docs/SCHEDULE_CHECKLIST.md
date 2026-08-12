# infra-schedule 分布式调度检查清单

面向 **infra-schedule-admin（调度中心）+ infra-schedule（执行器）** 架构，可用于上线前自检、故障排查和定期巡检。

---

## 一、分布式调度基础

### 1.1 部署与模块边界

- [ ] **调度中心**单独部署 `infra-schedule-admin`，配置了 `spring.datasource` 且 `infra.schedule.management.enabled=true`
- [ ] **执行器**只依赖 `infra-schedule`，**未配置**调度库 DataSource
- [ ] Admin 端口与执行器端口分离（默认 Admin `18080`，执行器各自独立）
- [ ] 执行器 `executor.admin-address` 指向可达的 Admin 地址
- [ ] 执行器 `executor.address` 为 Admin **能访问**的地址（非 localhost，除非同机）

### 1.2 多调度节点（Admin 集群）

- [ ] 每个 Admin 实例配置**唯一** `infra.schedule.scheduler-id`（环境变量 `SCHEDULE_SCHEDULER_ID`）
- [ ] 数据库为 **MySQL 8+**（依赖 `FOR UPDATE SKIP LOCKED` 领任务）
- [ ] 已建表/迁移：`infra_schedule_job`、`infra_schedule_execution_log`、`infra_schedule_executor`、`infra_schedule_executor_registry`、`infra_schedule_route_stat`、`infra_schedule_route_cursor`
- [ ] 多节点时钟基本同步（NTP），避免 `next_trigger_at` 偏差过大
- [ ] 领任务租约 `claim-lease-millis`（默认 60s）大于单次扫描周期，避免租约频繁过期重领

### 1.3 任务领取与重叠触发

- [ ] 到期任务通过 `claimDueJobs` + 行锁领取，写入 `claim_owner` / `claim_until`
- [ ] 领取后**立即推进** `next_trigger_at`（执行未完成也会按 Cron/固定频率继续触发）
- [ ] 重叠触发由**执行器侧**阻塞策略处理，不是调度中心排队等待
- [ ] `dispatch-batch-size`（默认 100）与 `dispatch-max-pages`（默认 10）符合任务量级，避免单轮扫描占用过久
- [ ] `scan-interval-millis`（默认 1000ms）与业务延迟要求匹配

### 1.4 执行器注册与心跳

- [ ] 执行器分组（appname）全局唯一，与任务 `executorGroup` 一致
- [ ] 自动注册模式（`AUTO_REGISTER`）下，心跳间隔 < 超时阈值（默认 10s / 30s）
- [ ] 手动地址模式（`MANUAL`）地址格式正确：`http://host:port` 或 `host:port`（会自动补 `http://`）
- [ ] Admin 页面「执行器管理」中节点在线、地址列表与预期一致
- [ ] 执行器被 DISABLED 后不再参与路由

### 1.5 鉴权与网络

- [ ] 生产环境开启 `SCHEDULE_EXECUTOR_AUTH_ENABLED=true`，Admin 与执行器共用 `SCHEDULE_ACCESS_TOKEN`
- [ ] 生产环境开启 `SCHEDULE_ADMIN_AUTH_ENABLED=true`，页面/API 携带 `X-Infra-Schedule-Admin-Token`
- [ ] Admin → 执行器：`/infra/schedule/executor/run`、`/beat`、`/idleBeat`、`/running`、`/cancel` 可达
- [ ] 执行器 → Admin：心跳、日志回调（started/finish/handle-append）可达
- [ ] 防火墙/网关未拦截 JSON 请求（需 `Content-Type: application/json`）

### 1.6 常见分布式问题速查

| 现象 | 优先检查 |
|------|----------|
| 库里有任务，页面为空 | Admin 是否连对库；`management.enabled` + DataSource 是否正确；是否误用内存仓储 |
| 任务从不触发 | 任务状态 ENABLED；`next_trigger_at` 是否到期；Cron 是否正确 |
| 多 Admin 重复触发同一时刻 | MySQL 版本、`SKIP LOCKED`、租约是否生效 |
| 任务显示失败「没有可用执行器」 | 心跳超时、分组不匹配、地址无效、执行器 DISABLED |
| 调度延迟大 | `scan-interval-millis`、到期任务堆积、`dispatch-max-pages` 不足 |
| 触发成功但执行器无日志 | 路由到了错误节点；Token 401；地址不可达 |

---

## 二、调度日志清理与僵尸进程

> **说明**：当前实现的是**僵尸日志回收**（状态修正），**没有**内置历史日志自动物理删除；大表清理需 DBA/运维策略。

### 2.1 日志状态含义

- [ ] 理解终态：`SUCCESS` / `FAILED` / `TIMEOUT` / `CANCELLED` / `SKIPPED` / `LOST`
- [ ] 活跃态：`QUEUED`（已下发、排队）/ `RUNNING`（执行中）
- [ ] `LOST` = 调度中心判定「目标节点不可达或该 logId 已不在跑」，**不会 kill 远程进程**

### 2.2 僵尸日志回收机制

- [ ] 每轮调度扫描调用 `reapStaleRunningLogs`（与 `dispatchDueJobs` 同周期）
- [ ] 阈值：`stale-running-log-millis`（默认 **600000ms = 10 分钟**），实际取 `max(阈值, claim-lease, 60s)`
- [ ] 单轮上限：`stale-running-log-batch-size`（默认 **100** 条）
- [ ] 候选条件：`status IN (QUEUED, RUNNING)` 且 `trigger_time <= now - 阈值`
- [ ] 回收前**探活**：向目标执行器查 `/running?logId=`；本地则查 `ExecutorTaskTracker`
- [ ] 探活仍为真 → **跳过**（长任务不会被误杀）
- [ ] 探活不可达/为假 → 标记 `LOST`

### 2.3 调度前探活（reconcile）

- [ ] 每次任务触发前，对该 job 全部 `QUEUED/RUNNING` 日志探活
- [ ] 不可观测的僵尸记录在触发前即回收，避免阻塞策略误判「仍在跑」

### 2.4 僵尸「进程」与超时

- [ ] 调度中心**不会**因 LOST 回收而远程 kill 进程（仅改 DB 状态）
- [ ] 任务配置 `timeoutSeconds > 0` 时，调度侧 Future 超时会 **cancel + 请求执行器 cancel**
- [ ] 管理员「终止」会 cancel Future 并调用执行器 `/cancel`
- [ ] 执行器进程真崩溃后：心跳消失 → 路由失败；旧日志 eventually → `LOST`

### 2.5 日志清理（运维侧）

- [ ] 制定 `infra_schedule_execution_log` 保留策略（如按 `trigger_time` 归档/删除 30/90 天前）
- [ ] 关注 `handle_log` 字段（单条最大约 1MB，超长会截断）
- [ ] 索引存在：`idx_infra_schedule_log_status_trigger (status, trigger_time)` 便于僵尸扫描
- [ ] 定期监控 `QUEUED/RUNNING` 总量；长期不下降说明探活/网络/执行器异常
- [ ] 监控 `LOST` 突增：执行器大面积宕机、地址变更、Token/网络问题

### 2.6 配置检查项

```yaml
infra:
  schedule:
    claim-lease-millis: 60000          # 领任务租约
    stale-running-log-millis: 600000   # 僵尸回收阈值（建议 ≥ 最长任务预期时长）
    stale-running-log-batch-size: 100  # 单轮回收条数
    scan-interval-millis: 1000         # 回收与调度同频
```

- [ ] 长任务（>10 分钟）已将 `stale-running-log-millis` 调大，避免误回收
- [ ] 超短周期任务（秒级）+ `DISCARD_LATER`：关注 `SKIPPED` 日志是否堆积

---

## 三、路由策略

> 路由在**调度中心**选节点；`FAILOVER` / `BUSYOVER` 还会探活/空闲检测。

### 3.1 通用前置条件

- [ ] 任务指定 `executorId` → 只用该执行器的地址列表
- [ ] 未指定 → 按 `executorGroup` 下全部健康节点
- [ ] 候选为空 → 直接失败，写 FAILED 日志
- [ ] 多地址执行器：检查 `infra_schedule_executor_registry` 是否与心跳一致

### 3.2 各策略检查

| 策略 | 行为 | 检查要点 |
|------|------|----------|
| **FIRST** | 取排序后第一个 | 节点顺序稳定（按 executorId + address） |
| **LAST** | 取最后一个 | 同上 |
| **ROUND** | 轮询 | 多 Admin 共享 `infra_schedule_route_cursor`；cursorKey = `executor:{id}` 或 `group` |
| **RANDOM** | 随机 | 仅单次随机，无持久状态 |
| **CONSISTENT_HASH** | 按 jobId 哈希 | 环上键为**原始地址**（MD5 + 100 虚拟节点）；迁移时地址勿随意改 |
| **LFU** | 最少使用 | 共享 `infra_schedule_route_stat`；多 Admin 一致 |
| **LRU** | 最久未用 | 同上 |
| **FAILOVER** | 按序心跳探活 | 第一个 `/beat` 成功即用；全不可达则失败 |
| **BUSYOVER** | 按序空闲检测 | `/idleBeat?jobId=` 为真才用；全忙或不可达则失败 |
| **SHARDING_BROADCAST** | 广播全部节点 | 每个节点各执行一次（`shardIndex/shardTotal`） |

### 3.3 路由相关故障

- [ ] **ROUND/LFU/LRU 在多 Admin 下分布异常** → 检查 V10/V11 表是否存在、Admin 是否都连同一库
- [ ] **CONSISTENT_HASH 迁移后落点变了** → 地址 normalize 前后不一致；应用原始注册地址
- [ ] **FAILOVER 总报不可达** → Token、网络、执行器 HTTP 端点未启
- [ ] **BUSYOVER 总报全忙** → 任务 `blockStrategy=SERIAL` 且单 job 长期占用；或 idleBeat 实现异常
- [ ] **历史枚举兼容** → 库中 `ROUND_ROBIN`→`ROUND`，`BROADCAST`→`SHARDING_BROADCAST`

---

## 四、阻塞策略

> 阻塞策略在**执行器**按 `jobId` 的 JobThread 生效（SERIAL / DISCARD_LATER / COVER_EARLY）。

### 4.1 策略行为对照

| 策略 | 同一 job 已有执行/排队时 | 日志表现 |
|------|------------------------|----------|
| **SERIAL（单机串行）** | 新触发**入队**，按序执行 | 多条日志，先后 SUCCESS；前序可能长时间 QUEUED/RUNNING |
| **DISCARD_LATER（丢弃后续）** | **丢弃**本次触发 | `SKIPPED`，message 含「丢弃后续调度」 |
| **COVER_EARLY（覆盖之前）** | **终止**旧线程/清空队列，执行本次 | 前序可能 CANCELLED；本次正常执行 |

### 4.2 与调度模型的关系

- [ ] 调度中心领取后**不等待**上次执行结束（重叠触发是设计行为）
- [ ] 选 SERIAL 的长任务 + 短 Cron → 队列持续变长，关注执行器内存与日志量
- [ ] 选 DISCARD_LATER 的短周期任务 → 大量 SKIPPED 属正常，非故障
- [ ] 选 COVER_EARLY → 确认 Handler 可中断/幂等；被 cover 的任务可能中途终止

### 4.3 阻塞策略排查

- [ ] 「任务不并发」→ 应设 SERIAL，且执行器仅一个实例（或路由到单节点）
- [ ] 「总是丢触发」→ DISCARD_LATER + 上次未跑完；或调大 Cron 间隔
- [ ] 「旧任务被杀」→ COVER_EARLY 生效；检查是否误配
- [ ] 队列满（「触发队列已满」）→ 极端 SERIAL 堆积，需扩容或改策略

### 4.4 与超时/取消的交互

- [ ] `timeoutSeconds` 超时：调度侧 cancel + 远程 cancel，日志 `TIMEOUT`
- [ ] 管理员终止：日志 `CANCELLED`
- [ ] COVER_EARLY stop 旧线程：旧 log 可能 CANCELLED 或由执行器回写终态

---

## 五、常驻任务（resident）

### 5.1 字段含义

- [ ] `resident=true` 表示**常驻任务**（如长期运行/守护类，对齐 xxl-job 语义）
- [ ] 存储于 `infra_schedule_job.resident`（V6 迁移）
- [ ] 与 Cron/固定频率、阻塞策略、路由策略**独立配置**

### 5.2 当前实现行为

- [ ] 调度触发、路由、重叠触发机制与普通任务**相同**
- [ ] 被 `DISCARD_LATER` 丢弃时，日志仍为 `SKIPPED`，message 为「**常驻任务丢弃后续触发**」
- [ ] 僵尸回收 `reapStaleRunningLogs` **不区分**是否常驻：仍先探活， alive 则跳过
- [ ] **不会**因 resident 而跳过调度或禁止重叠触发

### 5.3 常驻任务推荐配置

- [ ] 预期「同时只跑一份」→ `blockStrategy=SERIAL` 或 `DISCARD_LATER`
- [ ] 预期「新触发顶替旧实例」→ `COVER_EARLY`（需 Handler 支持安全终止）
- [ ] 执行时间可能很长 → 调大 `stale-running-log-millis`，避免误标 LOST
- [ ] 单节点部署 → 路由用 FIRST / 指定 executorId；多节点慎用 BROADCAST
- [ ] 任务停用：设 `status=DISABLED`（**不会**强杀已在跑的实例，需手动 cancel）

### 5.4 常驻任务巡检

- [ ] 仅一条 RUNNING 且长期不变 → 正常（若业务本就常驻）
- [ ] RUNNING 但探活失败 → 将变 LOST；检查执行器是否真在跑
- [ ] 大量 SKIPPED + resident → DISCARD_LATER 下重复 Cron 触发，属预期
- [ ] 需要「进程级守护」→ 还需配合 K8s/systemd，调度层不负责拉起 OS 进程

---

## 六、快速巡检 SQL

```sql
-- 长期活跃日志（疑似僵尸）
SELECT id, job_id, status, trigger_time, target_address, message
FROM infra_schedule_execution_log
WHERE status IN ('QUEUED', 'RUNNING')
ORDER BY trigger_time ASC
LIMIT 50;

-- 到期但未领（租约/状态异常）
SELECT id, name, status, next_trigger_at, claim_owner, claim_until
FROM infra_schedule_job
WHERE status = 'ENABLED' AND next_trigger_at <= UNIX_TIMESTAMP() * 1000;

-- 近期 LOST 突增
SELECT DATE(FROM_UNIXTIME(trigger_time/1000)) d, COUNT(*) c
FROM infra_schedule_execution_log
WHERE status = 'LOST' AND trigger_time > UNIX_TIMESTAMP(NOW() - INTERVAL 7 DAY) * 1000
GROUP BY d ORDER BY d DESC;
```

---

## 七、建议巡检节奏

| 频率 | 内容 |
|------|------|
| 每次发布 | 模块边界、scheduler-id、Token、DB 迁移、执行器地址 |
| 每日 | 活跃日志数量、LOST 比例、执行器在线率 |
| 每周 | 路由表/游标表增长、execution_log 表大小、阻塞策略与 Cron 是否匹配 |
| 每月 | 历史日志归档删除、常驻任务探活阈值复核 |

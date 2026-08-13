# infra-schedule 需求与运行语义

## 1. 目标与边界

`infra-schedule` 参考 xxl-job 的调度模型，提供任务管理、分布式调度、执行器管理、路由、阻塞控制、执行日志和后台页面。

- `infra-schedule-admin` 是唯一的调度中心，所有任务、执行器、路由状态、触发 Outbox 和执行日志均持久化在 MySQL。
- 执行器仅依赖 `infra-schedule`，通过 HTTP 接收执行请求、向 Admin 上报心跳和日志；执行器不保存调度状态，也不需要连接调度库。
- Admin 集群必须连接同一个 MySQL 8+ 数据库，且每个节点配置唯一的 `infra.schedule.scheduler-id`。
- 执行器必须配置 `infra.schedule.executor.admin-address`，并配置一个可由 Admin 访问的 `executor.address`。

## 2. 分布式调度

### 2.1 任务领取

调度线程按照 `scan-interval-millis`（默认 1 秒）扫描已启用且已到期的任务。多个 Admin 节点通过 MySQL 行锁领取任务：

1. 使用 `SELECT ... FOR UPDATE SKIP LOCKED` 分页读取到期任务。
2. 当前节点将任务写为 `claim_owner` 和 `claim_until`，取得短期租约后立即提交事务。
3. 在同一 MySQL 事务内推进 `last_trigger_at`、`next_trigger_at`，并写入 `infra_schedule_trigger_outbox`。
4. Outbox 投递循环以独立租约领取 `PENDING` 记录，异步提交到本节点工作线程；工作线程被拒绝时记录重新变为 `PENDING`，下轮重试。

租约过期的任务或 Outbox 记录可以被其他 Admin 节点接管，节点故障不会阻塞后续扫描。每轮最多处理 `dispatch-batch-size * dispatch-max-pages` 条，避免任务堆积时长期占用调度线程。

### 2.2 触发语义

任务被领取后立即计算下一次触发时间，不等待上一次执行结束。因此短周期任务可以产生重叠触发；实际的重叠处理由执行器侧阻塞策略决定。

手动执行也先写入 Outbox，不会绕过可靠投递路径，也不会改变 Cron 或固定频率的下一次计划时间。任务停用或删除时，会取消仍处于 `PENDING` / `PROCESSING` 的 Outbox 记录；已经提交到工作线程或执行器的执行会继续走取消流程或自然结束。

当前 Outbox 保障“计划推进与待投递记录”在一个事务内完成，并支持投递租约恢复。它不是端到端 exactly-once 协议：执行器侧业务处理必须按任务参数或业务键实现幂等，以承受网络超时、进程崩溃和可能的重复投递。

## 3. 执行器与路由

执行器的 `id` 为数据库自增主键，`executor_group` 为全局唯一分组标识，`executor_name` 仅用于展示。任务优先按 `executor_id` 选择执行器；兼容旧任务时可按 `executor_group` 路由。

执行器支持两种地址来源：

- `MANUAL`：管理员维护一个或多个固定地址。
- `AUTO_REGISTER`：执行器通过心跳注册实例地址，超过 `heartbeat-timeout-millis` 未上报的地址不参与路由。

执行器被禁用后不再参与任务选择。调用失败、没有健康节点或路由探测失败会写入失败执行日志，不会阻塞其他任务。

### 3.1 路由策略

| 策略 | 当前逻辑 |
| --- | --- |
| `FIRST` | 选择排序后的第一个可路由地址。 |
| `LAST` | 选择排序后的最后一个可路由地址。 |
| `ROUND` | 使用 MySQL 共享游标轮询，Admin 集群共享进度。 |
| `RANDOM` | 从可路由地址中随机选择一个。 |
| `CONSISTENT_HASH` | 根据任务 ID 在原始地址构成的一致性哈希环上选择节点。 |
| `LEAST_FREQUENTLY_USED` | 选择 MySQL 共享统计中使用次数最少的节点。 |
| `LEAST_RECENTLY_USED` | 选择 MySQL 共享统计中最久未被选择的节点。 |
| `FAILOVER` | 按顺序调用 `/beat`，选择第一个确认可达的节点。 |
| `BUSYOVER` | 按顺序调用 `/idleBeat`，选择第一个确认空闲的节点。 |
| `SHARDING_BROADCAST` | 向所有候选节点发送一次，并传递 `shardIndex` / `shardTotal`。 |

`ROUND`、`LEAST_FREQUENTLY_USED` 和 `LEAST_RECENTLY_USED` 依赖 MySQL 中的共享游标或统计表，因此多 Admin 节点的决策保持一致。`FAILOVER` 与 `BUSYOVER` 的网络探测失败视为该节点不可选，不等待网络恢复。

## 4. 阻塞策略

阻塞策略在执行器侧按 `jobId` 生效。每个任务在单个执行器进程内维护一个 `JobThread` 和触发队列，调度中心不因任务正在执行而停止产生新的触发。

| 策略 | 当前逻辑 | 日志结果 |
| --- | --- | --- |
| `SERIAL` | 当前任务正在运行或有队列时，新触发进入队列，按顺序执行。 | 触发先为 `QUEUED`，实际开始后为 `RUNNING`，结束后进入终态。 |
| `DISCARD_LATER` | 当前任务正在运行或有队列时，直接拒绝本次新触发。 | 普通任务写 `SKIPPED`。常驻任务不保留这条跳过日志。 |
| `COVER_EARLY` | 请求终止旧线程并清空旧队列，然后执行新触发。 | 被覆盖触发回写取消；若旧线程在 `cover-early-wait-millis` 内未确认退出，则拒绝新触发，避免并发执行。 |

阻塞策略的范围是“同一个执行器实例中的同一个 jobId”。当任务可被路由到多个执行器地址时，不同实例之间仍可能并行执行；需要全局单实例时，应固定一个执行器地址或由业务锁、幂等键保证。

## 5. 常驻任务

`resident=true` 用于长期运行或守护型 Handler。它不改变任务的定时计算、分布式领取、路由或超时逻辑，仍可配置 Cron / 固定频率及任一路由、阻塞策略。

- 建议长期单实例任务使用固定执行器，并选择 `DISCARD_LATER` 或 `SERIAL`。
- `DISCARD_LATER` 丢弃常驻任务的重复触发时，系统删除刚创建的执行日志，不在后台生成大量 `SKIPPED` 记录。
- `COVER_EARLY` 适合允许被安全中断和重启的常驻任务；Handler 必须响应中断并保证业务幂等。
- 停用任务不会强制终止已开始的 Handler；需要停止时，应从后台取消对应执行日志，系统会通知执行器 `/cancel`。

## 6. 执行状态、超时与僵尸日志

执行日志的活跃状态为 `QUEUED`、`RUNNING`；终态包括 `SUCCESS`、`FAILED`、`TIMEOUT`、`CANCELLED`、`SKIPPED` 和 `LOST`。

任务显式配置 `timeoutSeconds` 时优先使用该值；未配置时使用 `max-execution-millis`（默认 1 小时）。超时时调度中心会取消本地等待 Future，并向远程执行器请求 `/cancel`，日志标为 `TIMEOUT`。管理员取消或删除任务时也会发起同样的取消请求。

每轮扫描会回收僵尸活跃日志：

1. 候选日志为触发时间早于 `max(stale-running-log-millis, claim-lease-millis, 60000ms)` 的 `QUEUED` 或 `RUNNING` 记录。
2. 对远程执行器调用 `/running?logId=...`；本地执行器查询 `ExecutorTaskTracker`。
3. 只有执行器明确返回“不存在”时，才将日志更新为 `LOST`。
4. 网络不可达、鉴权失败或协议异常属于未知状态，保留活跃日志等待下次探测，避免把实际仍在运行的任务误判为丢失。

`LOST` 是日志状态修正，不会自动杀死远程进程。执行器失联、日志回调失败或进程崩溃应结合心跳、网络监控和业务幂等进行处置。

## 7. 历史日志清理

每轮调度结束后，系统按 `execution-log-retention-millis` 清理已结束日志，默认保留 30 天；设置为 `0` 可关闭自动清理。

- 仅删除 `finish_time` 早于保留阈值且不再是 `QUEUED` / `RUNNING` 的记录。
- 每轮删除数量受 `execution-log-cleanup-batch-size` 限制，默认 1000，避免长事务、锁等待和数据库抖动。
- 活跃日志、Outbox 记录和任务定义不在该清理范围内。
- `handle_log` 在追加时限制为约 1 MB；应结合保留时间、任务频率和日志量评估数据库容量。

## 8. 关键配置

```yaml
infra:
  schedule:
    scheduler-id: infra-schedule-admin-1
    scan-interval-millis: 1000
    claim-lease-millis: 60000
    dispatch-batch-size: 100
    dispatch-max-pages: 10
    stale-running-log-millis: 600000
    stale-running-log-batch-size: 100
    worker-threads: 8
    worker-queue-capacity: 1000
    max-execution-millis: 3600000
    cover-early-wait-millis: 5000
    execution-log-retention-millis: 2592000000
    execution-log-cleanup-batch-size: 1000
    trigger-outbox-retention-millis: 2592000000
    trigger-outbox-cleanup-batch-size: 1000
    scheduler-threads: 4
```

生产部署应保证 Admin 与执行器时钟同步、MySQL 高可用、执行器地址可达，并启用 Admin/执行器之间的访问令牌校验。

`worker-threads` 与 `worker-queue-capacity` 共同限制本节点同时处理和内存等待的 Outbox 数量。队列满时，触发记录会释放回 MySQL 的 `PENDING` 状态，而非无限创建等待线程；容量应按执行器吞吐、任务超时和数据库积压监控结果调整。

## 9. 变更 Review 要求

任何涉及调度、执行器协议、持久化、路由、阻塞策略、超时、日志或后台管理的变更，必须完成代码 review，并在自动化测试或可复现的集成环境中覆盖以下异常场景。不得只验证正常的单节点成功路径。

| 场景 | 必须验证的结果 |
| --- | --- |
| Admin 节点宕机 | 在任务领取后、Outbox 投递前、工作线程开始前、任务执行中分别终止节点；租约到期后其他节点能继续调度，且不会将未开始触发永久标记完成。 |
| MySQL 锁、慢事务与租约过期 | 多个 Admin 并发扫描时 `SKIP LOCKED` 不产生锁等待；租约持有者停顿或数据库短暂不可用时，恢复后由有效持有者推进计划，不覆盖已更新的任务定义。 |
| Outbox 重复投递 | 处理节点在远程调用前后崩溃、续租失败、网络响应丢失时，记录可恢复且业务侧能依据幂等键容忍至少一次投递；工作线程开始、每次重试和广播下一分片前须确认仍持有租约，失去租约后不得继续发起新的调用。 |
| 执行器网络超时 | `/run`、`/beat`、`/idleBeat`、`/running`、`/cancel` 分别超时、401、5xx 或返回空响应时，不阻塞扫描线程；任务级超时不能被更短的 HTTP 超时提前误判。 |
| 执行器失联与回调乱序 | 心跳中断、进程崩溃、地址变更、旧节点回调、started/finish/业务日志乱序或重复到达时，只在确认任务不存在后标记 `LOST`，终态不被旧回调覆盖。 |
| 阻塞队列堆积 | `SERIAL` 队列达到上限、`DISCARD_LATER` 高频丢弃、`COVER_EARLY` 中 Handler 忽略中断时，不能形成并发覆盖、无限等待或未收口日志。 |
| 路由策略异常 | 空执行器、禁用执行器、手动地址不可达、自动注册地址过期、广播部分失败、FAILOVER/BUSYOVER 探测失败、ROUND/LFU/LRU 多节点并发更新时，结果和日志应可解释且不阻塞其他任务。 |
| 任务停用、更新和删除 | 与任务领取、Outbox 处理、工作线程启动、执行器排队/运行并发发生时，未开始触发必须撤销；已开始执行的取消应按日志 ID 精确发出，不能误取消同任务其他实例。 |
| 常驻任务 | 长时间运行、重复触发、停用、手动取消、超时和执行器重启时，确认策略符合预期；`DISCARD_LATER` 不生成无意义日志，`COVER_EARLY` 仅在旧任务确认退出后启动新任务。 |
| 日志与 Outbox 清理竞态 | 清理与执行器 started/finish/追加日志回调并发时，活跃记录不得删除；已完成记录按批清理，不产生长事务、锁等待或表无限增长。 |
| 注册并发与身份变化 | 多节点同时收到同一新分组首个心跳、同一实例重复心跳、手动/自动地址模式切换、执行器分组唯一键冲突时，应幂等完成而非返回 5xx；删除执行器必须拒绝仍被任务引用的记录，且须防止检查后新建任务形成孤儿引用。 |
| 时间与容量边界 | Admin 和 MySQL 时钟偏差、Cron 追赶、大量到期任务、超大业务日志、执行器地址数量增长、历史数据保留期调整时，分页上限、截断和清理策略应保持可控。 |

Review 还必须确认：任务 Handler 具备业务幂等性和中断处理能力；共享令牌不会写入日志；监控能够发现 Outbox 积压、活跃日志积压、`LOST`/`TIMEOUT` 激增、心跳异常、路由失败和清理持续失败。

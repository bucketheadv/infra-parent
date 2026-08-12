package io.infra.structure.schedule.core

import io.infra.structure.schedule.api.ScheduleExecutor
import io.infra.structure.schedule.model.ExecutorHeartbeat

/** 为已注册的远程执行器创建 HTTP 调用适配器。 */
fun interface ScheduleExecutorClientFactory {
    /** 根据心跳记录创建可调用执行器；没有有效地址时返回 null。 */
    fun create(heartbeat: ExecutorHeartbeat): ScheduleExecutor?
}

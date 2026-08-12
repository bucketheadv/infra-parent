package io.infra.structure.schedule.executor.sample

import io.infra.structure.schedule.api.ScheduleHandler
import io.infra.structure.schedule.api.ScheduleJobHandler
import io.infra.structure.schedule.api.ScheduleLogHelper
import io.infra.structure.schedule.model.JobExecutionContext
import io.infra.structure.schedule.model.JobExecutionResult
import org.springframework.stereotype.Component
import java.lang.Thread.sleep

/** 用于验证执行器连通性、业务日志上报和广播分片信息的示例处理器。 */
@Component
@ScheduleHandler("sampleEchoHandler")
class SampleEchoScheduleHandler : ScheduleJobHandler {
    /** 通过 [ScheduleLogHelper] 上报过程日志，并模拟可中断的耗时任务。 */
    override fun execute(context: JobExecutionContext): JobExecutionResult {
        ScheduleLogHelper.log("示例任务开始: jobId={}, logId={}", context.jobId, context.logId)
        ScheduleLogHelper.log("参数: {}", context.parameters.ifBlank { "<empty>" })
        ScheduleLogHelper.log("分片: {}/{}", context.shardIndex, context.shardTotal)
        sleep(10_000)
        ScheduleLogHelper.log("示例任务结束")
        return JobExecutionResult.success(context.parameters.takeIf { it.isNotBlank() })
    }
}

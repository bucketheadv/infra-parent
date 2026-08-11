package io.infra.structure.schedule.executor.sample

import io.infra.structure.schedule.api.ScheduleHandler
import io.infra.structure.schedule.api.ScheduleJobHandler
import io.infra.structure.schedule.model.JobExecutionContext
import io.infra.structure.schedule.model.JobExecutionResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.lang.Thread.sleep

/** 用于验证执行器连通性、参数传递和广播分片信息的示例处理器。 */
@Component
@ScheduleHandler("sampleEchoHandler")
class SampleEchoScheduleHandler : ScheduleJobHandler {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** 记录调度上下文并返回成功结果。 */
    override fun execute(context: JobExecutionContext): JobExecutionResult {
        sleep(3000)
        logger.info(
            "执行示例任务: jobId={}, parameters={}, shard={}/{}",
            context.jobId,
            context.parameters,
            context.shardIndex,
            context.shardTotal
        )
        return JobExecutionResult.success(context.parameters.takeIf { it.isNotBlank() })
    }
}

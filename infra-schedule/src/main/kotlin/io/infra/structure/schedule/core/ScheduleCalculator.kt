package io.infra.structure.schedule.core

import io.infra.structure.schedule.model.ScheduleJob
import io.infra.structure.schedule.model.ScheduleType
import org.springframework.scheduling.support.CronExpression
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

object ScheduleCalculator {
    /** 校验触发配置和执行策略中的所有范围约束。 */
    fun validate(job: ScheduleJob) {
        require(job.name.isNotBlank()) { "任务名称不能为空" }
        require(job.handler.isNotBlank()) { "任务处理器不能为空" }
        require(job.maxRetryCount >= 0) { "最大重试次数不能小于 0" }
        require(job.retryIntervalMillis >= 0) { "重试间隔不能小于 0" }
        require(job.timeoutSeconds >= 0) { "超时时间不能小于 0" }
        when (job.scheduleType) {
            ScheduleType.CRON -> CronExpression.parse(requireNotNull(job.cron) { "Cron 任务缺少表达式" })
            ScheduleType.FIXED_RATE -> require((job.fixedRateMillis ?: 0) > 0) { "固定间隔必须大于 0" }
        }
    }

    /** 从 [from] 时间点之后计算首次触发时间。 */
    fun nextTriggerAt(job: ScheduleJob, from: Long): Long = when (job.scheduleType) {
        ScheduleType.CRON -> CronExpression.parse(requireNotNull(job.cron)).next(
            ZonedDateTime.ofInstant(Instant.ofEpochMilli(from), ZoneId.systemDefault())
        )?.toInstant()?.toEpochMilli() ?: error("Cron 表达式没有下一次执行时间")
        ScheduleType.FIXED_RATE -> from + requireNotNull(job.fixedRateMillis)
    }

    /**
     * 计算严格晚于 [now] 的下一次触发时间。
     * 固定间隔任务会跳过停机期间错过的周期，防止恢复后集中补偿执行。
     */
    fun nextFutureTriggerAt(job: ScheduleJob, from: Long, now: Long): Long {
        var candidate = nextTriggerAt(job, from)
        while (candidate <= now) candidate = nextTriggerAt(job, candidate)
        return candidate
    }

    /**
     * 预览即将到来的若干次调度时间。
     * 若任务已有未来的 [ScheduleJob.nextTriggerAt]，则以其作为第一次，再向后推算。
     */
    fun nextTriggerTimes(job: ScheduleJob, now: Long, count: Int): List<Long> {
        require(count > 0) { "预览次数必须大于 0" }
        val times = ArrayList<Long>(count)
        var cursor = now
        val scheduled = job.nextTriggerAt
        if (scheduled != null && scheduled > now) {
            times += scheduled
            cursor = scheduled
        }
        while (times.size < count) {
            val next = nextTriggerAt(job, cursor)
            times += next
            cursor = next
        }
        return times
    }
}

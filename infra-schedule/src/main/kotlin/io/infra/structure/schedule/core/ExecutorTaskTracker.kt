package io.infra.structure.schedule.core

import io.infra.structure.schedule.model.BlockStrategy
import io.infra.structure.schedule.model.JobExecutionContext
import io.infra.structure.schedule.model.JobExecutionResult
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 执行器侧任务协调器：按 jobId 维护 JobThread，解释阻塞策略（对齐 xxl-job ExecutorBizImpl.run）。
 * HTTP `/run` 与本地执行器共用。
 */
class ExecutorTaskTracker(
    private val handlerRegistry: HandlerRegistry,
    private val coverEarlyWaitMillis: Long = 5_000L,
    private val onExecutionStarted: (JobExecutionContext) -> Unit = {},
    private val onExecutionFinished: (JobExecutionContext, JobExecutionResult, Long) -> Unit = { _, _, _ -> }
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val jobThreads = ConcurrentHashMap<Long, ExecutorJobThread>()
    /**
     * 仅为正在协调的 jobId 保留互斥锁，并通过引用计数延迟回收。
     * 线程退出与新请求交错时不能提前移除锁，否则两个请求可能各自创建一把锁并同时协调同一个 jobId。
     */
    private val admissionLocks = ConcurrentHashMap<Long, AdmissionLock>()

    /**
     * 按阻塞策略入队或丢弃/覆盖，并同步等待本次触发的执行结果。
     */
    fun run(context: JobExecutionContext): JobExecutionResult {
        val jobId = context.jobId
        if (jobId <= 0) {
            return JobExecutionResult.failure("无效的 jobId")
        }
        val admission = admissionLocks.compute(jobId) { _, current ->
            (current ?: AdmissionLock()).also { it.users.incrementAndGet() }
        } ?: error("无法取得任务协调锁: $jobId")
        val ticket = TriggerTicket(context)
        try {
            synchronized(admission.monitor) {
                var jobThread = jobThreads[jobId]
                if (jobThread != null && !jobThread.isAlive) {
                    jobThreads.remove(jobId, jobThread)
                    jobThread = null
                }
                if (jobThread != null && jobThread.isRunningOrHasQueue()) {
                    when (context.blockStrategy) {
                        BlockStrategy.DISCARD_LATER -> {
                            logger.info(
                                "DISCARD_LATER 丢弃触发: jobId={}, logId={}",
                                jobId,
                                context.logId
                            )
                            return JobExecutionResult.discarded("block strategy effect：丢弃后续调度")
                        }
                        BlockStrategy.COVER_EARLY -> {
                            val reason = "block strategy effect：覆盖之前调度"
                            logger.warn(
                                "COVER_EARLY 覆盖前序: jobId={}, logId={}",
                                jobId,
                                context.logId
                            )
                            if (!jobThread.stopForCover(reason, coverEarlyWaitMillis)) {
                                return JobExecutionResult.failure("COVER_EARLY 等待旧任务退出超时，拒绝并发执行")
                            }
                            jobThreads.remove(jobId, jobThread)
                            jobThread = null
                        }
                        BlockStrategy.SERIAL -> {
                            // 入队串行
                        }
                    }
                }
                if (jobThread == null) {
                    jobThread = ExecutorJobThread(
                        jobId,
                        handlerRegistry,
                        onExecutionStarted,
                        onExecutionFinished
                    ) { exited ->
                        // COVER_EARLY 会持有 admission monitor 等待旧线程退出；退出回调不能再争用
                        // 该 monitor，否则 join 会互相等待。Map.compute 保证回收与新请求取锁原子串行。
                        jobThreads.remove(jobId, exited)
                        admissionLocks.computeIfPresent(jobId) { _, current ->
                            if (current === admission && current.users.get() == 0) null else current
                        }
                    }
                    jobThreads[jobId] = jobThread
                    jobThread.start()
                }
                if (!jobThread.offer(ticket)) {
                    return JobExecutionResult.failure("触发队列已满")
                }
            }
        } finally {
            synchronized(admission.monitor) {
                if (admission.users.decrementAndGet() == 0 && !jobThreads.containsKey(jobId)) {
                    admissionLocks.computeIfPresent(jobId) { _, current ->
                        if (current === admission && current.users.get() == 0) null else current
                    }
                }
            }
        }
        return ticket.await()
    }

    /** 中断指定日志对应的 handler 或队列项。 */
    fun cancel(logId: Long): Boolean {
        var cancelled = false
        for (thread in jobThreads.values) {
            if (thread.cancel(logId)) cancelled = true
        }
        if (cancelled) {
            logger.warn("已向执行线程发送中止信号: logId={}", logId)
        }
        return cancelled
    }

    /** 指定日志 ID 是否仍在执行或排队。 */
    fun isRunning(logId: Long): Boolean = jobThreads.values.any { it.isRunning(logId) }

    /** 指定 job 是否空闲（无运行中 handler 且队列为空），供 BUSYOVER idleBeat。 */
    fun isJobIdle(jobId: Long): Boolean {
        val thread = jobThreads[jobId] ?: return true
        if (!thread.isAlive) return true
        return !thread.isRunningOrHasQueue()
    }

    /** 同一 jobId 的入队协调锁与当前等待请求数。 */
    private class AdmissionLock {
        val monitor = Any()
        val users = AtomicInteger()
    }
}

package io.infra.structure.trace.task

import org.slf4j.MDC
import org.springframework.core.task.TaskDecorator

/**
 * 异步任务的 MDC 传播装饰器。
 *
 * 提交任务时快照当前线程的 MDC，执行时恢复、结束后还原，保证异步线程内日志
 * 与同步链路共享同一个 traceId/spanId。引用方可直接作为 TaskDecorator Bean 使用，
 * 也可配置到自定义线程池上。
 *
 * @author sven
 */
class TraceTaskDecorator : TaskDecorator {

    override fun decorate(runnable: Runnable): Runnable {
        // 1. 提交任务时快照父线程的 MDC（含 traceId/spanId）
        val contextMap = MDC.getCopyOfContextMap()
        return Runnable {
            // 2. 保存执行线程原有的 MDC，避免污染复用线程上的其他任务
            val previous = MDC.getCopyOfContextMap()
            // 3. 恢复父线程的链路上下文，使异步日志归属同一条链路
            if (contextMap != null) {
                MDC.setContextMap(contextMap)
            }
            try {
                runnable.run()
            } finally {
                // 4. 任务结束后还原原上下文，防止链路上下文串扰
                if (previous != null) {
                    MDC.setContextMap(previous)
                } else {
                    MDC.clear()
                }
            }
        }
    }
}

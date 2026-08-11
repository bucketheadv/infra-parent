package io.infra.structure.schedule.executor.sample

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/** 可独立运行的调度执行器接入示例。 */
@SpringBootApplication
class InfraScheduleExecutorSampleApplication

/** 启动示例执行器。 */
fun main(args: Array<String>) {
    runApplication<InfraScheduleExecutorSampleApplication>(*args)
}

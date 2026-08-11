package io.infra.structure.schedule.admin

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * 独立部署的调度管理后台。
 *
 * 任务管理接口由 infra-schedule 自动配置提供，根路径为 /infra/schedule；本应用仅负责
 * 持有 MySQL 调度表、分页领取到期任务和将任务路由至已注册的执行器。
 */
@SpringBootApplication
class InfraScheduleAdminApplication

/** 启动调度管理后台。 */
fun main(args: Array<String>) {
    runApplication<InfraScheduleAdminApplication>(*args)
}

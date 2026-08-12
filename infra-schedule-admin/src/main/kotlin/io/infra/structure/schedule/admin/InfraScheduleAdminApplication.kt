package io.infra.structure.schedule.admin

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * 独立部署的调度中心与管理后台。
 *
 * 调度扫描与管理 REST 由 infra-schedule-admin 自动配置提供（/infra/schedule）；
 * 领域模型与持久化来自 infra-schedule starter。
 */
@SpringBootApplication
class InfraScheduleAdminApplication

/** 启动调度管理后台。 */
fun main(args: Array<String>) {
    runApplication<InfraScheduleAdminApplication>(*args)
}

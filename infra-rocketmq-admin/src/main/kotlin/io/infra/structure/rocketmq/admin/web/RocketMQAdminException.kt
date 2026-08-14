package io.infra.structure.rocketmq.admin.web

/** 管理后台业务异常；由 API 与页面异常处理器统一转换为友好提示。 */
class RocketMQAdminException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

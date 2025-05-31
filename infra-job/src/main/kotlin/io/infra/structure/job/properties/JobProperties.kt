package io.infra.structure.job.properties

import io.infra.structure.job.constants.configPrefix
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * @author liuqinglin
 * Date: 2025/5/13 18:19
 */
@ConfigurationProperties(prefix = configPrefix)
data class JobProperties(
    /**
     * 是否启用模块
     */
    val enabled: Boolean = false,

    /**
     * 注册应用名称
     */
    val appName: String? = null,

    /**
     * 注册admin地址
     */
    val adminAddresses: String? = null,

    /**
     * Token
     */
    val accessToken: String? = null,

    /**
     * 注册本机地址
     */
    val address: String? = null,

    /**
     * 本地ip
     */
    val ip :String? = null,

    /**
     * 端口号
     */
    val port: Int = 9999,

    /**
     * 日志打印目录
     */
    val logPath: String? = System.getProperty("user.home") + "/logs/task-center/jobhandler",

    /**
     * 保留日志天数
     */
    val logRetentionDays: Int = 2,
)
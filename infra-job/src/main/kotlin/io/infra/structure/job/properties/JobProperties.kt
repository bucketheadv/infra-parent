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
    var enabled: Boolean = false,

    /**
     * 注册应用名称
     */
    var appName: String? = null,

    /**
     * 注册admin地址
     */
    var adminAddresses: String? = null,

    /**
     * Token
     */
    var accessToken: String? = null,

    /**
     * 注册本机地址
     */
    var address: String? = null,

    /**
     * 本地ip
     */
    var ip :String? = null,

    /**
     * 端口号
     */
    var port: Int = 9999,

    /**
     * 日志打印目录
     */
    var logPath: String? = System.getProperty("user.home") + "/logs/task-center/jobhandler",

    /**
     * 保留日志天数
     */
    var logRetentionDays: Int = 2,
)
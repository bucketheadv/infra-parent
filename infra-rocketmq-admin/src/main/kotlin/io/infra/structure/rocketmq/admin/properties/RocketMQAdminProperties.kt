package io.infra.structure.rocketmq.admin.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * RocketMQ 管理后台配置。
 *
 * 管理后台通过 `rocketmq-tools` 提供的 [org.apache.rocketmq.tools.admin.DefaultMQAdminExt]
 * 直连 NameServer 查询集群、Topic、消费组与消息，因此需要独立的 NameServer 地址；
 * 与 `infra.rocketmq.producers/consumers` 的业务配置相互独立。
 */
@ConfigurationProperties("infra.rocketmq.admin")
class RocketMQAdminProperties {
    /** 管理后台开关，默认关闭。 */
    var enabled: Boolean = false
    /** NameServer 地址，多个地址使用分号分隔。 */
    var namesrvAddr: String = "127.0.0.1:9876"
    /** 可选的 ACL 访问密钥（配置了 access-key 才启用 ACL 认证）。 */
    var accessKey: String? = null
    /** 可选的 ACL 密钥，与 [accessKey] 配套使用。 */
    var secretKey: String? = null
    /** 管理客户端实例名，用于区分不同的管理进程。 */
    var instanceName: String = "infra-rocketmq-admin"
    /** 是否启用 TLS 连接。 */
    var useTLS: Boolean = false
    /** 单次管理操作（查询、重发等）的超时毫秒数。 */
    var operationTimeoutMillis: Long = 5_000
    /** 管理端接口安全开关。 */
    var authEnabled: Boolean = false
    /** 管理端访问令牌，不可在配置文件中写入明文。 */
    var accessToken: String? = null
}
package io.infra.structure.rocketmq.admin.support

import io.infra.structure.rocketmq.admin.properties.RocketMQAdminProperties
import org.apache.rocketmq.acl.common.AclClientRPCHook
import org.apache.rocketmq.acl.common.SessionCredentials
import org.apache.rocketmq.remoting.RPCHook
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * RocketMQ 管理客户端的线程安全包装。
 *
 * [DefaultMQAdminExt] 内部存在共享状态，对低并发的管理后台直接复用单个实例即可；
 * 这里统一串行化全部管理操作，避免并发查询与写操作互相干扰。
 */
class RocketMQAdminClient(
    private val properties: RocketMQAdminProperties
) : Closeable {

    private val log = LoggerFactory.getLogger(RocketMQAdminClient::class.java)
    private val lock = ReentrantLock()

    @Volatile
    private var started = false

    private val admin: DefaultMQAdminExt = buildAdmin(properties)

    /** 串行执行一次管理操作。 */
    fun <T> execute(block: (DefaultMQAdminExt) -> T): T = lock.withLock {
        ensureStarted()
        block(admin)
    }

    /** 是否已成功连接到 NameServer。 */
    val connected: Boolean
        get() = started

    private fun ensureStarted() {
        if (started) return
        try {
            admin.start()
            started = true
            log.info("RocketMQ 管理客户端已启动，NameServer: {}", properties.namesrvAddr)
        } catch (exception: Exception) {
            throw IllegalStateException("无法连接 RocketMQ NameServer（${properties.namesrvAddr}）：${exception.message}", exception)
        }
    }

    override fun close() {
        lock.withLock {
            if (started) {
                try {
                    admin.shutdown()
                } catch (exception: Exception) {
                    log.warn("RocketMQ 管理客户端关闭异常", exception)
                } finally {
                    started = false
                }
            }
        }
    }

    private companion object {
        fun buildAdmin(properties: RocketMQAdminProperties): DefaultMQAdminExt {
            val rpcHook: RPCHook? = properties.accessKey?.takeIf { it.isNotBlank() }?.let {
                AclClientRPCHook(SessionCredentials(it, properties.secretKey ?: ""))
            }
            val admin = if (rpcHook != null) DefaultMQAdminExt(rpcHook) else DefaultMQAdminExt()
            admin.namesrvAddr = properties.namesrvAddr
            admin.instanceName = properties.instanceName
            admin.isUseTLS = properties.useTLS
            admin.isVipChannelEnabled = false
            return admin
        }
    }
}
package io.infra.structure.schedule.core

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.TreeMap

/** 路由用哈希与一致性 HASH（对齐 xxl-job `ExecutorRouteConsistentHash`）。 */
object RouteHash {
    private const val VIRTUAL_NODE_NUM = 100

    /**
     * 对齐 xxl-job：MD5 取 digest 前 4 字节组成 32 位无符号整数。
     */
    fun md5Hash32(key: String): Long {
        val digest = MessageDigest.getInstance("MD5").digest(key.toByteArray(StandardCharsets.UTF_8))
        val hashCode = ((digest[3].toLong() and 0xFF) shl 24) or
            ((digest[2].toLong() and 0xFF) shl 16) or
            ((digest[1].toLong() and 0xFF) shl 8) or
            (digest[0].toLong() and 0xFF)
        return hashCode and 0xffffffffL
    }

    /**
     * 在候选地址中选一个（虚拟节点 + 环上顺时针查找）。
     * [routeKey] 通常为 jobId 字符串。
     */
    fun selectConsistentAddress(routeKey: String, addresses: List<String>): String? {
        if (addresses.isEmpty()) return null
        if (addresses.size == 1) return addresses.first()
        val ring = TreeMap<Long, String>()
        for (address in addresses) {
            for (index in 0 until VIRTUAL_NODE_NUM) {
                ring[md5Hash32("SHARD-$address-NODE-$index")] = address
            }
        }
        val jobHash = md5Hash32(routeKey)
        val tail = ring.tailMap(jobHash)
        return if (tail.isNotEmpty()) {
            tail[tail.firstKey()]
        } else {
            ring.firstEntry().value
        }
    }

    /** LFU/LRU 等统计使用的稳定地址键（normalize 后）。 */
    fun addressKey(routed: RoutedExecutor): String =
        routed.address?.let { ExecutorAddresses.normalizeHttpBaseUrl(it) ?: it.trim() }
            ?: "local:${routed.dbId}"

    /** 一致性 HASH 环上使用的原始地址键（对齐 xxl-job）。 */
    fun hashRingKey(routed: RoutedExecutor): String =
        routed.hashRingKey?.takeIf { it.isNotBlank() } ?: "local:${routed.dbId}"
}

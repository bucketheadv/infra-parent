package io.infra.structure.job.util

import java.io.IOException
import java.net.ServerSocket

/**
 * 网络工具类
 * 用于替代 xxl-job 3.3.0 中移除的 NetUtil.findAvailablePort 方法
 * @author sven
 * Created on 2025/12/6
 */
object NetUtil {
    /**
     * 查找可用端口
     * @param defaultPort 默认端口，如果该端口不可用，则尝试下一个端口
     * @return 可用的端口号
     * @throws RuntimeException 如果找不到可用端口（端口范围 1-65535）
     */
    @JvmStatic
    fun findAvailablePort(defaultPort: Int): Int {
        var port = defaultPort
        // 确保端口在有效范围内
        if (port < 1) {
            port = 1
        }
        if (port > 65535) {
            port = 65535
        }
        
        // 从指定端口开始查找，最多尝试 1000 个端口
        val maxAttempts = 1000
        var attempts = 0
        
        while (attempts < maxAttempts && port <= 65535) {
            if (isPortAvailable(port)) {
                return port
            }
            port++
            attempts++
        }
        
        throw RuntimeException("无法找到可用端口，已尝试从端口 $defaultPort 开始的 $maxAttempts 个端口")
    }
    
    /**
     * 检查端口是否可用
     * @param port 端口号
     * @return true 如果端口可用，false 如果端口已被占用
     */
    private fun isPortAvailable(port: Int): Boolean {
        return try {
            ServerSocket(port).use {
                true
            }
        } catch (_: IOException) {
            false
        }
    }
}


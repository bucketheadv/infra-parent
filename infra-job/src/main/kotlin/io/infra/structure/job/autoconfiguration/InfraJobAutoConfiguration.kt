package io.infra.structure.job.autoconfiguration

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor
import com.xxl.job.core.util.NetUtil
import io.infra.structure.core.tool.BinderTool
import io.infra.structure.job.constants.configPrefix
import io.infra.structure.job.properties.JobProperties
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.EnvironmentAware
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment

/**
 * @author liuqinglin
 * Date: 2025/5/13 18:30
 */

@Configuration
@ConditionalOnProperty(prefix = configPrefix, value = ["enabled"], havingValue = "true")
class InfraJobAutoConfiguration (
    @Value("\${spring.application.name:}")
    private val applicationName: String?
) : EnvironmentAware {
    private var environment: Environment? = null

    @Bean
    fun jobProperties(): JobProperties? = BinderTool.bind(environment, configPrefix, JobProperties::class.java)

    @Bean
    @ConditionalOnMissingBean
    fun xxlJobSpringExecutor(jobProperties: JobProperties): XxlJobSpringExecutor {
        val appName = jobProperties.appName ?: applicationName
        val executor = XxlJobSpringExecutor()
        executor.setAppname(appName)
        executor.setAdminAddresses(jobProperties.adminAddresses)
        executor.setAccessToken(jobProperties.accessToken)
        executor.setAddress(jobProperties.address)
        executor.setIp(jobProperties.ip)
        val port = NetUtil.findAvailablePort(jobProperties.port)
        executor.setPort(port)
        executor.setLogPath(jobProperties.logPath)
        executor.setLogRetentionDays(jobProperties.logRetentionDays)
        return executor
    }

    override fun setEnvironment(environment: Environment) {
        this.environment = environment
    }
}
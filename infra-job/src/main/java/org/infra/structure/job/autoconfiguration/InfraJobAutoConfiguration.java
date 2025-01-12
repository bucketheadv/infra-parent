package org.infra.structure.job.autoconfiguration;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import com.xxl.job.core.util.NetUtil;
import org.apache.commons.lang3.StringUtils;
import org.infra.structure.core.tool.BinderTool;
import org.infra.structure.job.constants.Const;
import org.infra.structure.job.properties.JobProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.lang.NonNull;

/**
 * @author qinglinl
 * Created on 2022/3/8 3:57 下午
 */
@Configuration
@ConditionalOnProperty(prefix = Const.configPrefix, value = "enabled", havingValue = "true")
public class InfraJobAutoConfiguration implements EnvironmentAware {

    private Environment environment;

    @Value("${spring.application.name:}")
    private String applicationName;

    @Bean
    public JobProperties jobProperties() {
        return BinderTool.bind(environment, Const.configPrefix, JobProperties.class);
    }

    @Bean
    @ConditionalOnMissingBean
    public XxlJobSpringExecutor xxlJobSpringExecutor(JobProperties jobProperties) {
        String appName = StringUtils.defaultIfBlank(jobProperties.getAppName(), applicationName);
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAppname(appName);
        executor.setAdminAddresses(jobProperties.getAdminAddresses());
        executor.setAccessToken(jobProperties.getAccessToken());
        executor.setAddress(jobProperties.getAddress());
        executor.setIp(jobProperties.getIp());
        int port = NetUtil.findAvailablePort(jobProperties.getPort());
        executor.setPort(port);
        executor.setLogPath(jobProperties.getLogPath());
        executor.setLogRetentionDays(jobProperties.getLogRetentionDays());
        return executor;
    }

    @Override
    public void setEnvironment(@NonNull Environment environment) {
        this.environment = environment;
    }
}

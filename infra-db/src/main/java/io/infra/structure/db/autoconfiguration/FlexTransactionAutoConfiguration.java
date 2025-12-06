package io.infra.structure.db.autoconfiguration;

import com.mybatisflex.core.row.Db;
import com.mybatisflex.spring.FlexTransactionManager;
import com.mybatisflex.spring.boot.MybatisFlexAutoConfiguration;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.annotation.TransactionManagementConfigurer;

@ConditionalOnClass({Db.class})
@Role(2)
@ConditionalOnMissingBean({TransactionManager.class})
@Configuration(
        proxyBeanMethods = false
)
@AutoConfigureOrder(Integer.MIN_VALUE)
@AutoConfigureAfter({MybatisFlexAutoConfiguration.class})
@AutoConfigureBefore({TransactionAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class})
public class FlexTransactionAutoConfiguration implements TransactionManagementConfigurer {
    private final FlexTransactionManager flexTransactionManager = new FlexTransactionManager();

    @NotNull
    @Bean(
            name = {"transactionManager"}
    )
    public PlatformTransactionManager annotationDrivenTransactionManager() {
        return this.flexTransactionManager;
    }
}

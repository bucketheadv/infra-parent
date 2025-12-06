package io.infra.structure.core.autoconfiguration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * @author sven
 * Created on 2025/1/12 14:52
 */
@Slf4j
@Configuration
@ComponentScan(basePackages = "io.infra.structure.core")
public class InfraCoreAutoConfiguration {

    /**
     * 配置 ObjectMapper Bean
     * 在 Spring Boot 4.0 中，如果缺少 spring-boot-starter-json，需要手动配置
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        
        // 注册 Kotlin 模块（如果项目使用 Kotlin）
        try {
            objectMapper.registerModule(new KotlinModule.Builder().build());
        } catch (Exception e) {
            log.warn("注册 KotlinModule 失败，Kotlin 支持可能不可用", e);
        }
        
        // 时间序列化为时间戳
        objectMapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // 未知属性时，不抛出异常
        objectMapper.disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES);
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        
        log.info("ObjectMapper Bean 配置成功");
        return objectMapper;
    }
}

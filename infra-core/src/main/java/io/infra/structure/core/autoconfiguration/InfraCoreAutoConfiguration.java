package io.infra.structure.core.autoconfiguration;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jackson.autoconfigure.JacksonProperties;
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
@EnableConfigurationProperties(JacksonProperties.class)
public class InfraCoreAutoConfiguration {

    /**
     * 配置 ObjectMapper Bean
     * 在 Spring Boot 4.0 中，如果缺少 spring-boot-starter-json，需要手动配置
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper objectMapper(JacksonProperties jacksonProperties) {
        ObjectMapper objectMapper = new ObjectMapper();
        
        // 注册 Kotlin 模块（如果项目使用 Kotlin）
        try {
            objectMapper.registerModule(new KotlinModule.Builder().build());
        } catch (Exception e) {
            log.warn("注册 KotlinModule 失败，Kotlin 支持可能不可用", e);
        }

        // 由应用配置决定接口字段命名，Kotlin/Java 属性仍使用 camelCase。
        String propertyNamingStrategy = jacksonProperties.getPropertyNamingStrategy();
        objectMapper.setPropertyNamingStrategy(resolvePropertyNamingStrategy(
                propertyNamingStrategy == null ? "SNAKE_CASE" : propertyNamingStrategy));

        // 由应用配置决定接口响应的字段包含策略。
        JsonInclude.Include defaultPropertyInclusion = jacksonProperties.getDefaultPropertyInclusion();
        if (defaultPropertyInclusion != null) {
            objectMapper.setDefaultPropertyInclusion(defaultPropertyInclusion);
        }
        
        // 时间序列化为时间戳
        objectMapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // 未知属性时，不抛出异常
        objectMapper.disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES);
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        
        log.info("ObjectMapper Bean 配置成功");
        return objectMapper;
    }

    /** 将 YAML 中的 Jackson 命名策略常量解析为对应实现。 */
    private PropertyNamingStrategy resolvePropertyNamingStrategy(String propertyNamingStrategy) {
        try {
            return (PropertyNamingStrategy) PropertyNamingStrategies.class
                    .getField(propertyNamingStrategy)
                    .get(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalArgumentException("不支持的 Jackson 属性命名策略：" + propertyNamingStrategy, exception);
        }
    }
}

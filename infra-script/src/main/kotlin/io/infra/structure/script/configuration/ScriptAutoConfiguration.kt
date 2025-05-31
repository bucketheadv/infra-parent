package io.infra.structure.script.configuration

import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

/**
 * @author liuqinglin
 * Date: 2025/5/30 17:56
 */
@Configuration
@ComponentScan(basePackages = ["io.infra.structure.script"])
class ScriptAutoConfiguration
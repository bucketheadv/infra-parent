package io.infra.kotlin.spring

import jakarta.annotation.PostConstruct
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component

/**
 * @author liuqinglin
 * Date: 2025/4/19 22:02
 */

lateinit var springContext: ApplicationContext

@Component
class SpringSingletonProvider(private val applicationContext: ApplicationContext) {
    @PostConstruct
    fun init() {
        springContext = applicationContext
    }
}
package io.infra.structure.core.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

val beanCopier = jacksonObjectMapper()

/**
 * @author liuqinglin
 * Date: 2025/4/19 23:33
 */
inline fun <reified T> ObjectMapper.copyAs(source: Any?): T? {
    if (source == null) {
        return null
    }
    val str = writeValueAsString(source)
    return readValue(str, T::class.java)
}

package io.infra.structure.core.utils

import com.fasterxml.jackson.core.type.TypeReference
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

inline fun <reified T> ObjectMapper.copyArrayAs(source: List<*>?): ArrayList<T> {
    if (source.isNullOrEmpty()) {
        return arrayListOf()
    }
    val str = writeValueAsString(source)
    return readValue(str, object : TypeReference<ArrayList<T>>() {})
}

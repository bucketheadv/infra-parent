package io.infra.structure.redis.utils

import io.infra.structure.core.tool.JsonTool
import io.infra.structure.redis.core.JedisTemplate
import kotlin.text.isNullOrBlank
import kotlin.time.Duration

/**
 * @author liuqinglin
 * Date: 2025/4/30 17:39
 */
inline fun <reified T> JedisTemplate.fetch(key: String, expires: Duration, lambda: () -> T?): T? {
    return fetch<T>(key, expires.inWholeSeconds, lambda)
}

inline fun <reified T> JedisTemplate.fetch(key: String, expires: Long, lambda: () -> T?): T? {
    val value = get(key)
    if (!value.isNullOrBlank()) {
        return JsonTool.parseObject(value, T::class.java)
    }
    val data = lambda.invoke()
    data?.let {
        setex(key, expires, JsonTool.toJsonString(data))
    }
    return data
}
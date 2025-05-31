package io.infra.structure.redis.utils

import io.infra.structure.core.tool.JsonTool
import io.infra.structure.db.model.DbEntity
import io.infra.structure.redis.core.JedisTemplate
import java.io.Serializable
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

inline fun <reified T : DbEntity<ID>, reified ID : Serializable> JedisTemplate.fetchEntityMulti(suffix: String,
                                                                                                ids: List<ID>,
                                                                                                expireSeconds: Long = 300,
                                                                                                lambda: (List<ID>) -> List<T>): List<T> {
    if (ids.isEmpty()) {
        return listOf()
    }
    val missingIds = arrayListOf<ID>()
    val result = arrayListOf<T>()
    ids.forEach { id ->
        val key = "$suffix:$id"
        val value = get(key)
        if (value.isNullOrBlank()) {
            missingIds.add(id)
        } else {
            result.add(JsonTool.parseObject(value, T::class.java))
        }
    }
    if (missingIds.isEmpty()) {
        return result
    }

    val data = lambda(missingIds)
    data.forEach {
        val key = "$suffix:${it.id}"
        setex(key, expireSeconds, JsonTool.toJsonString(it))
        result.add(it)
    }
    return result
}
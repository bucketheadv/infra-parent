package io.infra.structure.core.utils

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author liuqinglin
 * Date: 2025/4/9 00:46
 */

inline fun <reified T>getLogger(): Logger = LoggerFactory.getLogger(T::class.java)

fun getLogger(clazz: Class<*>): Logger = LoggerFactory.getLogger(clazz)

interface Loggable {
    val log: Logger
    get() = getLogger(javaClass)
}
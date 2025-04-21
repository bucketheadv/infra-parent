package io.infra.structure.kotlin.enums

/**
 * @author liuqinglin
 * Date: 2025/4/19 21:35
 */

interface CodeEnum<T> {
    val code: T
}

interface NumberCodeEnum : CodeEnum<Number>

interface StringCodeEnum : CodeEnum<String>

inline fun <reified E, T> enumByCode(code: T): E? where E : Enum<E>, E : CodeEnum<T> {
    return enumValues<E>().firstOrNull { it.code == code }
}

inline fun <reified E> enumOfCode(code: Number): E? where E : Enum<E>, E : NumberCodeEnum {
    return enumByCode(code)
}

inline fun <reified E> enumOfCode(code: String): E? where E : Enum<E>, E : StringCodeEnum {
    return enumByCode(code)
}
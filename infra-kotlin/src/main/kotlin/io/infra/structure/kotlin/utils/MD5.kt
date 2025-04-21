package io.infra.structure.kotlin.utils

import java.security.MessageDigest

/**
 * @author liuqinglin
 * Date: 2025/4/8 23:08
 */

fun String.md5(): String {
    val md = MessageDigest.getInstance("MD5")
    val digest = md.digest(toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}
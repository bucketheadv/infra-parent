package io.infra.structure.core.utils

import com.fasterxml.jackson.databind.DeserializationFeature
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson

/**
 * @author liuqinglin
 * Date: 2025/5/1 09:29
 */

object HttpTool {
    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            jackson {
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }

    suspend inline fun <reified T> get(url: String,
                                       headers: Map<String, String> = emptyMap(),
                                       params: Map<String, String> = emptyMap()): T {
        val finalUrl = URLBuilder(url).apply {
            params.forEach { (key, value) -> parameters.append(key, value) }
        }.buildString()
        val response: HttpResponse = client.get(finalUrl) {
            headers.forEach { (key, value) -> header(key, value) }
        }
        return response.body()
    }

    suspend inline fun <reified T> postJson(url: String,
                                            headers: Map<String, String> = emptyMap(),
                                            body: Any): T {
        val response = client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(body)
            headers.forEach { (key, value) -> header(key, value) }
        }
        return response.body()
    }

    suspend inline fun <reified T> postFormUrlEncoded(url: String,
                                                      headers: Map<String, String> = emptyMap(),
                                                      formParams: Map<String, String> = emptyMap()): T {
        val response = client.post(url) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(Parameters.build {
                formParams.forEach { (key, value) -> append(key, value) }
            })
            headers.forEach { (key, value) -> header(key, value) }
        }
        return response.body()
    }

    suspend inline fun <reified T> putJson(url: String,
                                           headers: Map<String, String> = emptyMap(),
                                           body: Any): T {
        val response = client.put(url) {
            contentType(ContentType.Application.Json)
            setBody(body)
            headers.forEach { (key, value) -> header(key, value) }
        }
        return response.body()
    }

    suspend inline fun <reified T> patchJson(url: String,
                                             headers: Map<String, String> = emptyMap(),
                                             body: Any): T {
        val response = client.patch(url) {
            contentType(ContentType.Application.Json)
            setBody(body)
            headers.forEach { (key, value) -> header(key, value) }
        }
        return response.body()
    }

    suspend inline fun <reified T> delete(url: String,
                                          headers: Map<String, String> = emptyMap(),
                                          params: Map<String, String> = emptyMap()): T {
        val finalUrl = URLBuilder(url).apply {
            params.forEach { (key, value) -> parameters.append(key, value) }
        }.buildString()
        val response: HttpResponse = client.delete(finalUrl) {
            headers.forEach { (key, value) -> header(key, value) }
        }
        return response.body()
    }
}
package com.hyper.market.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.endpoint
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import java.io.IOException
import kotlinx.coroutines.runBlocking

internal object KtorMarketHttpClient {
    private val stripDefaultHeaders = createClientPlugin("StripDefaultHeaders") {
        onRequest { request, _ ->
            request.headers.remove("Accept-Charset")
            request.headers.remove(HttpHeaders.Accept)
        }
    }

    private val client = HttpClient(CIO) {
        expectSuccess = false
        followRedirects = true
        install(stripDefaultHeaders)
        engine {
            endpoint {
                connectTimeout = CONNECT_TIMEOUT_MS.toLong()
                socketTimeout = READ_TIMEOUT_MS.toLong()
            }
        }
    }

    @JvmStatic
    fun get(url: String, requestHeaders: Map<String, String>): String = execute {
        get(url) { applyHeaders(requestHeaders) }
    }

    @JvmStatic
    fun postForm(
        url: String,
        parameters: Map<String, String>,
        requestHeaders: Map<String, String>,
    ): String = execute {
        post(url) {
            applyHeaders(requestHeaders)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(Parameters.build {
                parameters.forEach { (key, value) -> append(key, value) }
            }.formUrlEncode())
        }
    }

    private fun execute(request: suspend HttpClient.() -> HttpResponse): String = runBlocking {
        val response = request(client)
        val body = response.bodyAsText()
        if (response.status.value !in HTTP_SUCCESS) {
            throw IOException("Xiaomi API HTTP ${response.status.value}: $body")
        }
        body
    }

    private fun io.ktor.client.request.HttpRequestBuilder.applyHeaders(
        requestHeaders: Map<String, String>,
    ) {
        headers {
            requestHeaders.forEach { (name, value) -> append(name, value) }
        }
    }

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000
    private val HTTP_SUCCESS = 200..299
}

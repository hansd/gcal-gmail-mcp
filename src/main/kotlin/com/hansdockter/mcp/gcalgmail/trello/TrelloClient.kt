package com.hansdockter.mcp.gcalgmail.trello

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

class TrelloClient(private val credentials: TrelloCredentials) {

    private val httpClient = HttpClient(CIO)

    private val baseUrl = "https://api.trello.com/1"

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun get(path: String, params: Map<String, String> = emptyMap()): JsonElement {
        val response = httpClient.get("$baseUrl$path") {
            url {
                parameters.append("key", credentials.apiKey)
                parameters.append("token", credentials.token)
                params.forEach { (k, v) -> parameters.append(k, v) }
            }
        }
        return handleResponse(response)
    }

    suspend fun post(path: String, params: Map<String, String> = emptyMap()): JsonElement {
        val response = httpClient.post("$baseUrl$path") {
            url {
                parameters.append("key", credentials.apiKey)
                parameters.append("token", credentials.token)
                params.forEach { (k, v) -> parameters.append(k, v) }
            }
        }
        return handleResponse(response)
    }

    suspend fun put(path: String, params: Map<String, String> = emptyMap()): JsonElement {
        val response = httpClient.put("$baseUrl$path") {
            url {
                parameters.append("key", credentials.apiKey)
                parameters.append("token", credentials.token)
                params.forEach { (k, v) -> parameters.append(k, v) }
            }
        }
        return handleResponse(response)
    }

    suspend fun delete(path: String, params: Map<String, String> = emptyMap()): JsonElement {
        val response = httpClient.delete("$baseUrl$path") {
            url {
                parameters.append("key", credentials.apiKey)
                parameters.append("token", credentials.token)
                params.forEach { (k, v) -> parameters.append(k, v) }
            }
        }
        return handleResponse(response)
    }

    private suspend fun handleResponse(response: HttpResponse): JsonElement {
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw RuntimeException("Trello API error ${response.status.value}: $body")
        }
        return json.parseToJsonElement(body)
    }
}

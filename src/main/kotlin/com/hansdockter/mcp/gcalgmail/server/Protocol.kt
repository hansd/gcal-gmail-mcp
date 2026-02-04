package com.hansdockter.mcp.gcalgmail.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: JsonElement? = null,
    val method: String,
    val params: JsonElement? = null
)

@Serializable
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: JsonElement? = null,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null
)

@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String
)

@Serializable
data class ToolListResult(
    val tools: List<McpTool>
)

@Serializable
data class McpTool(
    val name: String,
    val description: String,
    @SerialName("inputSchema")
    val inputSchema: JsonElement
)

@Serializable
data class ToolCallResult(
    val content: List<ToolContent>
)

@Serializable
data class ToolContent(
    val type: String,
    val text: String
)

@Serializable
data class InitializeResult(
    val protocolVersion: String = "2024-11-05",
    val capabilities: ServerCapabilities,
    val serverInfo: ServerInfo
)

@Serializable
data class ServerCapabilities(
    val tools: ToolsCapability = ToolsCapability()
)

@Serializable
data class ToolsCapability(
    val listChanged: Boolean = false
)

@Serializable
data class ServerInfo(
    val name: String,
    val version: String
)

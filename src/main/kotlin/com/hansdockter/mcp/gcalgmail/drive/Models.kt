package com.hansdockter.mcp.gcalgmail.drive

import kotlinx.serialization.Serializable

@Serializable
data class ListDocCommentsArgs(
    val fileId: String,
    val pageSize: Int? = null,
    val pageToken: String? = null,
    val includeDeleted: Boolean? = null
)

@Serializable
data class GetDocCommentArgs(
    val fileId: String,
    val commentId: String
)

@Serializable
data class CreateDocCommentArgs(
    val fileId: String,
    val content: String,
    val quotedContent: String? = null
)

@Serializable
data class ReplyToDocCommentArgs(
    val fileId: String,
    val commentId: String,
    val content: String
)

@Serializable
data class ResolveDocCommentArgs(
    val fileId: String,
    val commentId: String
)

@Serializable
data class DeleteDocCommentArgs(
    val fileId: String,
    val commentId: String
)

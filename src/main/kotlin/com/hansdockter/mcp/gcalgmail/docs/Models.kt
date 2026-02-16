package com.hansdockter.mcp.gcalgmail.docs

import kotlinx.serialization.Serializable

@Serializable
data class GetMeetingNotesArgs(
    val calendarId: String = "primary",
    val eventId: String
)

@Serializable
data class GetAgendaItemsArgs(
    val calendarId: String = "primary",
    val eventId: String
)

@Serializable
data class AddAgendaItemArgs(
    val calendarId: String = "primary",
    val eventId: String,
    val item: String
)

@Serializable
data class ReadDocContentArgs(
    val fileId: String
)

@Serializable
data class CreateEmailReviewDocArgs(
    val subject: String,
    val body: String,
    val to: List<String>? = null,
    val cc: List<String>? = null,
    val bcc: List<String>? = null
)

@Serializable
data class CreateDocArgs(
    val title: String,
    val content: String
)

@Serializable
data class UpdateDocArgs(
    val fileId: String,
    val content: String
)

@Serializable
data class ReplaceDocTextArgs(
    val fileId: String,
    val replacements: List<TextReplacement>
)

@Serializable
data class TextReplacement(
    val find: String,
    val replace: String,
    val matchCase: Boolean = true
)

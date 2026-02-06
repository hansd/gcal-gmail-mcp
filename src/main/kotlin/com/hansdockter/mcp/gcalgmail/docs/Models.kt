package com.hansdockter.mcp.gcalgmail.docs

import kotlinx.serialization.Serializable

@Serializable
data class GetMeetingNotesArgs(
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
data class CreateEmailReviewDocArgs(
    val subject: String,
    val body: String,
    val to: List<String>? = null,
    val cc: List<String>? = null,
    val bcc: List<String>? = null
)

package com.hansdockter.mcp.gcalgmail.calendar

import kotlinx.serialization.Serializable

@Serializable
data class ListEventsArgs(
    val calendarId: String = "primary",
    val timeMin: String? = null,
    val timeMax: String? = null,
    val maxResults: Int = 25,
    val query: String? = null,
    val singleEvents: Boolean = true,
    val orderBy: String = "startTime"
)

@Serializable
data class GetEventArgs(
    val calendarId: String = "primary",
    val eventId: String
)

@Serializable
data class CreateEventArgs(
    val calendarId: String = "primary",
    val summary: String,
    val description: String? = null,
    val location: String? = null,
    val start: EventDateTime,
    val end: EventDateTime,
    val attendees: List<String>? = null,
    val recurrence: List<String>? = null,
    val reminders: EventReminders? = null,
    val conferenceData: Boolean = false,
    val visibility: String? = null,
    val colorId: String? = null
)

@Serializable
data class UpdateEventArgs(
    val calendarId: String = "primary",
    val eventId: String,
    val summary: String? = null,
    val description: String? = null,
    val location: String? = null,
    val start: EventDateTime? = null,
    val end: EventDateTime? = null,
    val attendees: List<String>? = null,
    val recurrence: List<String>? = null,
    val reminders: EventReminders? = null,
    val visibility: String? = null,
    val colorId: String? = null
)

@Serializable
data class DeleteEventArgs(
    val calendarId: String = "primary",
    val eventId: String,
    val sendUpdates: String = "all"
)

@Serializable
data class QuickAddEventArgs(
    val calendarId: String = "primary",
    val text: String,
    val sendUpdates: String = "none"
)

@Serializable
data class RespondToEventArgs(
    val calendarId: String = "primary",
    val eventId: String,
    val response: String,
    val comment: String? = null
)

@Serializable
data class ListCalendarsArgs(
    val showHidden: Boolean = false,
    val showDeleted: Boolean = false
)

@Serializable
data class GetFreeBusyArgs(
    val timeMin: String,
    val timeMax: String,
    val calendarIds: List<String> = listOf("primary"),
    val timeZone: String? = null
)

@Serializable
data class ListEventInstancesArgs(
    val calendarId: String = "primary",
    val eventId: String,
    val timeMin: String? = null,
    val timeMax: String? = null,
    val maxResults: Int = 25
)

@Serializable
data class ListEventAttachmentsArgs(
    val calendarId: String = "primary",
    val eventId: String
)

@Serializable
data class EventDateTime(
    val dateTime: String? = null,
    val date: String? = null,
    val timeZone: String? = null
)

@Serializable
data class EventReminders(
    val useDefault: Boolean = true,
    val overrides: List<ReminderOverride>? = null
)

@Serializable
data class ReminderOverride(
    val method: String,
    val minutes: Int
)

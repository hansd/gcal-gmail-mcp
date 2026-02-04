package com.hansdockter.mcp.gcalgmail.server

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object ToolSchemas {
    private fun stringSchema(description: String? = null): JsonObject = buildJsonObject {
        put("type", "string")
        if (description != null) put("description", description)
    }

    private fun boolSchema(description: String? = null): JsonObject = buildJsonObject {
        put("type", "boolean")
        if (description != null) put("description", description)
    }

    private fun numberSchema(description: String? = null): JsonObject = buildJsonObject {
        put("type", "number")
        if (description != null) put("description", description)
    }

    private fun arraySchema(items: JsonObject, description: String? = null): JsonObject = buildJsonObject {
        put("type", "array")
        put("items", items)
        if (description != null) put("description", description)
    }

    private fun objectSchema(properties: Map<String, JsonObject>, required: List<String> = emptyList(), description: String? = null): JsonObject =
        buildJsonObject {
            put("type", "object")
            put("properties", JsonObject(properties))
            if (required.isNotEmpty()) {
                putJsonArray("required") { required.forEach { add(JsonPrimitive(it)) } }
            }
            if (description != null) put("description", description)
        }

    val sendEmail: JsonObject = objectSchema(
        mapOf(
            "to" to arraySchema(stringSchema(), "List of recipient email addresses"),
            "subject" to stringSchema("Email subject"),
            "body" to stringSchema("Email body content"),
            "htmlBody" to stringSchema("HTML version of the email body"),
            "mimeType" to buildJsonObject {
                put("type", "string")
                putJsonArray("enum") {
                    add(JsonPrimitive("text/plain"))
                    add(JsonPrimitive("text/html"))
                    add(JsonPrimitive("multipart/alternative"))
                }
                put("default", "text/plain")
            },
            "cc" to arraySchema(stringSchema(), "List of CC recipients"),
            "bcc" to arraySchema(stringSchema(), "List of BCC recipients"),
            "threadId" to stringSchema("Thread ID to reply to"),
            "inReplyTo" to stringSchema("Message ID being replied to"),
            "attachments" to arraySchema(stringSchema(), "List of file paths to attach to the email")
        ),
        required = listOf("to", "subject", "body")
    )

    val readEmail: JsonObject = objectSchema(
        mapOf("messageId" to stringSchema("ID of the email message to retrieve")),
        required = listOf("messageId")
    )

    val searchEmails: JsonObject = objectSchema(
        mapOf(
            "query" to stringSchema("Gmail search query"),
            "maxResults" to numberSchema("Maximum number of results to return")
        ),
        required = listOf("query")
    )

    val modifyEmail: JsonObject = objectSchema(
        mapOf(
            "messageId" to stringSchema("ID of the email message to modify"),
            "labelIds" to arraySchema(stringSchema(), "List of label IDs to apply"),
            "addLabelIds" to arraySchema(stringSchema(), "List of label IDs to add"),
            "removeLabelIds" to arraySchema(stringSchema(), "List of label IDs to remove")
        ),
        required = listOf("messageId")
    )

    val deleteEmail: JsonObject = objectSchema(
        mapOf("messageId" to stringSchema("ID of the email message to delete")),
        required = listOf("messageId")
    )

    val listEmailLabels: JsonObject = objectSchema(emptyMap(), description = "Retrieves all available Gmail labels")

    val batchModifyEmails: JsonObject = objectSchema(
        mapOf(
            "messageIds" to arraySchema(stringSchema(), "List of message IDs to modify"),
            "addLabelIds" to arraySchema(stringSchema(), "Label IDs to add"),
            "removeLabelIds" to arraySchema(stringSchema(), "Label IDs to remove"),
            "batchSize" to numberSchema("Batch size (default 50)")
        ),
        required = listOf("messageIds")
    )

    val batchDeleteEmails: JsonObject = objectSchema(
        mapOf(
            "messageIds" to arraySchema(stringSchema(), "List of message IDs to delete"),
            "batchSize" to numberSchema("Batch size (default 50)")
        ),
        required = listOf("messageIds")
    )

    val createLabel: JsonObject = objectSchema(
        mapOf(
            "name" to stringSchema("Name for the new label"),
            "messageListVisibility" to buildJsonObject {
                put("type", "string")
                putJsonArray("enum") { add(JsonPrimitive("show")); add(JsonPrimitive("hide")) }
            },
            "labelListVisibility" to buildJsonObject {
                put("type", "string")
                putJsonArray("enum") { add(JsonPrimitive("labelShow")); add(JsonPrimitive("labelShowIfUnread")); add(JsonPrimitive("labelHide")) }
            }
        ),
        required = listOf("name")
    )

    val updateLabel: JsonObject = objectSchema(
        mapOf(
            "id" to stringSchema("ID of the label to update"),
            "name" to stringSchema("New name for the label"),
            "messageListVisibility" to buildJsonObject {
                put("type", "string")
                putJsonArray("enum") { add(JsonPrimitive("show")); add(JsonPrimitive("hide")) }
            },
            "labelListVisibility" to buildJsonObject {
                put("type", "string")
                putJsonArray("enum") { add(JsonPrimitive("labelShow")); add(JsonPrimitive("labelShowIfUnread")); add(JsonPrimitive("labelHide")) }
            }
        ),
        required = listOf("id")
    )

    val deleteLabel: JsonObject = objectSchema(
        mapOf("id" to stringSchema("ID of the label to delete")),
        required = listOf("id")
    )

    val getOrCreateLabel: JsonObject = objectSchema(
        mapOf(
            "name" to stringSchema("Name of the label to get or create"),
            "messageListVisibility" to buildJsonObject {
                put("type", "string")
                putJsonArray("enum") { add(JsonPrimitive("show")); add(JsonPrimitive("hide")) }
            },
            "labelListVisibility" to buildJsonObject {
                put("type", "string")
                putJsonArray("enum") { add(JsonPrimitive("labelShow")); add(JsonPrimitive("labelShowIfUnread")); add(JsonPrimitive("labelHide")) }
            }
        ),
        required = listOf("name")
    )

    val createFilter: JsonObject = objectSchema(
        mapOf(
            "criteria" to objectSchema(
                mapOf(
                    "from" to stringSchema(),
                    "to" to stringSchema(),
                    "subject" to stringSchema(),
                    "query" to stringSchema(),
                    "negatedQuery" to stringSchema(),
                    "hasAttachment" to boolSchema(),
                    "excludeChats" to boolSchema(),
                    "size" to numberSchema(),
                    "sizeComparison" to buildJsonObject {
                        put("type", "string")
                        putJsonArray("enum") { add(JsonPrimitive("unspecified")); add(JsonPrimitive("smaller")); add(JsonPrimitive("larger")) }
                    }
                )
            ),
            "action" to objectSchema(
                mapOf(
                    "addLabelIds" to arraySchema(stringSchema()),
                    "removeLabelIds" to arraySchema(stringSchema()),
                    "forward" to stringSchema()
                )
            )
        ),
        required = listOf("criteria", "action")
    )

    val listFilters: JsonObject = objectSchema(emptyMap(), description = "Retrieves all Gmail filters")

    val getFilter: JsonObject = objectSchema(
        mapOf("filterId" to stringSchema("ID of the filter to retrieve")),
        required = listOf("filterId")
    )

    val deleteFilter: JsonObject = objectSchema(
        mapOf("filterId" to stringSchema("ID of the filter to delete")),
        required = listOf("filterId")
    )

    val createFilterFromTemplate: JsonObject = objectSchema(
        mapOf(
            "template" to buildJsonObject {
                put("type", "string")
                putJsonArray("enum") {
                    add(JsonPrimitive("fromSender"))
                    add(JsonPrimitive("withSubject"))
                    add(JsonPrimitive("withAttachments"))
                    add(JsonPrimitive("largeEmails"))
                    add(JsonPrimitive("containingText"))
                    add(JsonPrimitive("mailingList"))
                }
            },
            "parameters" to objectSchema(
                mapOf(
                    "senderEmail" to stringSchema(),
                    "subjectText" to stringSchema(),
                    "searchText" to stringSchema(),
                    "listIdentifier" to stringSchema(),
                    "sizeInBytes" to numberSchema(),
                    "labelIds" to arraySchema(stringSchema()),
                    "archive" to boolSchema(),
                    "markAsRead" to boolSchema(),
                    "markImportant" to boolSchema()
                )
            )
        ),
        required = listOf("template", "parameters")
    )

    val downloadAttachment: JsonObject = objectSchema(
        mapOf(
            "messageId" to stringSchema("ID of the email message containing the attachment"),
            "attachmentId" to stringSchema("ID of the attachment to download"),
            "filename" to stringSchema("Filename to save as"),
            "savePath" to stringSchema("Directory to save the attachment")
        ),
        required = listOf("messageId", "attachmentId")
    )

    // Calendar schemas

    private val eventDateTimeSchema: JsonObject = objectSchema(
        mapOf(
            "dateTime" to stringSchema("RFC3339 timestamp (e.g., 2024-01-15T09:00:00-05:00)"),
            "date" to stringSchema("Date for all-day events (e.g., 2024-01-15)"),
            "timeZone" to stringSchema("Timezone (e.g., America/New_York)")
        ),
        description = "Event date/time. Use dateTime for timed events, date for all-day events"
    )

    private val reminderOverrideSchema: JsonObject = objectSchema(
        mapOf(
            "method" to buildJsonObject {
                put("type", "string")
                putJsonArray("enum") { add(JsonPrimitive("email")); add(JsonPrimitive("popup")) }
                put("description", "Reminder method")
            },
            "minutes" to numberSchema("Minutes before event to trigger reminder")
        ),
        required = listOf("method", "minutes")
    )

    private val eventRemindersSchema: JsonObject = objectSchema(
        mapOf(
            "useDefault" to boolSchema("Use calendar's default reminders"),
            "overrides" to arraySchema(reminderOverrideSchema, "Custom reminder overrides")
        )
    )

    val listEvents: JsonObject = objectSchema(
        mapOf(
            "calendarId" to stringSchema("Calendar ID (default: 'primary')"),
            "timeMin" to stringSchema("Start of time range (RFC3339, e.g., 2024-01-01T00:00:00Z)"),
            "timeMax" to stringSchema("End of time range (RFC3339)"),
            "maxResults" to numberSchema("Maximum events to return (default: 25)"),
            "query" to stringSchema("Free text search query"),
            "singleEvents" to boolSchema("Expand recurring events (default: true)"),
            "orderBy" to buildJsonObject {
                put("type", "string")
                putJsonArray("enum") { add(JsonPrimitive("startTime")); add(JsonPrimitive("updated")) }
                put("default", "startTime")
            }
        )
    )

    val getEvent: JsonObject = objectSchema(
        mapOf(
            "calendarId" to stringSchema("Calendar ID (default: 'primary')"),
            "eventId" to stringSchema("Event ID to retrieve")
        ),
        required = listOf("eventId")
    )

    val createEvent: JsonObject = objectSchema(
        mapOf(
            "calendarId" to stringSchema("Calendar ID (default: 'primary')"),
            "summary" to stringSchema("Event title"),
            "description" to stringSchema("Event description"),
            "location" to stringSchema("Event location"),
            "start" to eventDateTimeSchema,
            "end" to eventDateTimeSchema,
            "attendees" to arraySchema(stringSchema(), "List of attendee email addresses"),
            "recurrence" to arraySchema(stringSchema(), "RRULE strings (e.g., 'RRULE:FREQ=WEEKLY;COUNT=10')"),
            "reminders" to eventRemindersSchema,
            "conferenceData" to boolSchema("Create Google Meet link (default: false)"),
            "visibility" to buildJsonObject {
                put("type", "string")
                putJsonArray("enum") {
                    add(JsonPrimitive("default"))
                    add(JsonPrimitive("public"))
                    add(JsonPrimitive("private"))
                    add(JsonPrimitive("confidential"))
                }
            },
            "colorId" to stringSchema("Color ID (1-11)")
        ),
        required = listOf("summary", "start", "end")
    )

    val updateEvent: JsonObject = objectSchema(
        mapOf(
            "calendarId" to stringSchema("Calendar ID (default: 'primary')"),
            "eventId" to stringSchema("Event ID to update"),
            "summary" to stringSchema("New event title"),
            "description" to stringSchema("New event description"),
            "location" to stringSchema("New event location"),
            "start" to eventDateTimeSchema,
            "end" to eventDateTimeSchema,
            "attendees" to arraySchema(stringSchema(), "Updated list of attendee emails"),
            "recurrence" to arraySchema(stringSchema(), "Updated RRULE strings"),
            "reminders" to eventRemindersSchema,
            "visibility" to buildJsonObject {
                put("type", "string")
                putJsonArray("enum") {
                    add(JsonPrimitive("default"))
                    add(JsonPrimitive("public"))
                    add(JsonPrimitive("private"))
                    add(JsonPrimitive("confidential"))
                }
            },
            "colorId" to stringSchema("Color ID (1-11)")
        ),
        required = listOf("eventId")
    )

    val deleteEvent: JsonObject = objectSchema(
        mapOf(
            "calendarId" to stringSchema("Calendar ID (default: 'primary')"),
            "eventId" to stringSchema("Event ID to delete"),
            "sendUpdates" to buildJsonObject {
                put("type", "string")
                putJsonArray("enum") {
                    add(JsonPrimitive("all"))
                    add(JsonPrimitive("externalOnly"))
                    add(JsonPrimitive("none"))
                }
                put("default", "all")
                put("description", "Who to send cancellation notifications to")
            }
        ),
        required = listOf("eventId")
    )

    val quickAddEvent: JsonObject = objectSchema(
        mapOf(
            "calendarId" to stringSchema("Calendar ID (default: 'primary')"),
            "text" to stringSchema("Natural language event description (e.g., 'Lunch with Bob tomorrow at noon')"),
            "sendUpdates" to buildJsonObject {
                put("type", "string")
                putJsonArray("enum") {
                    add(JsonPrimitive("all"))
                    add(JsonPrimitive("externalOnly"))
                    add(JsonPrimitive("none"))
                }
                put("default", "none")
            }
        ),
        required = listOf("text")
    )

    val respondToEvent: JsonObject = objectSchema(
        mapOf(
            "calendarId" to stringSchema("Calendar ID (default: 'primary')"),
            "eventId" to stringSchema("Event ID to respond to"),
            "response" to buildJsonObject {
                put("type", "string")
                putJsonArray("enum") {
                    add(JsonPrimitive("accept"))
                    add(JsonPrimitive("decline"))
                    add(JsonPrimitive("tentative"))
                }
                put("description", "Your response to the event invitation")
            },
            "comment" to stringSchema("Optional comment with your response")
        ),
        required = listOf("eventId", "response")
    )

    val listCalendars: JsonObject = objectSchema(
        mapOf(
            "showHidden" to boolSchema("Include hidden calendars (default: false)"),
            "showDeleted" to boolSchema("Include deleted calendars (default: false)")
        )
    )

    val getFreeBusy: JsonObject = objectSchema(
        mapOf(
            "timeMin" to stringSchema("Start of time range (RFC3339)"),
            "timeMax" to stringSchema("End of time range (RFC3339)"),
            "calendarIds" to arraySchema(stringSchema(), "Calendar IDs to check (default: ['primary'])"),
            "timeZone" to stringSchema("Timezone for the query")
        ),
        required = listOf("timeMin", "timeMax")
    )

    val listEventInstances: JsonObject = objectSchema(
        mapOf(
            "calendarId" to stringSchema("Calendar ID (default: 'primary')"),
            "eventId" to stringSchema("Recurring event ID"),
            "timeMin" to stringSchema("Start of time range (RFC3339)"),
            "timeMax" to stringSchema("End of time range (RFC3339)"),
            "maxResults" to numberSchema("Maximum instances to return (default: 25)")
        ),
        required = listOf("eventId")
    )

    val listEventAttachments: JsonObject = objectSchema(
        mapOf(
            "calendarId" to stringSchema("Calendar ID (default: 'primary')"),
            "eventId" to stringSchema("Event ID")
        ),
        required = listOf("eventId")
    )
}

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

    // Docs schemas

    val getMeetingNotes: JsonObject = objectSchema(
        mapOf(
            "calendarId" to stringSchema("Calendar ID (default: 'primary')"),
            "eventId" to stringSchema("Event ID to get meeting notes from")
        ),
        required = listOf("eventId")
    )

    val readDocContent: JsonObject = objectSchema(
        mapOf(
            "fileId" to stringSchema("Google Doc file ID")
        ),
        required = listOf("fileId")
    )

    val getAgendaItems: JsonObject = objectSchema(
        mapOf(
            "calendarId" to stringSchema("Calendar ID (default: 'primary')"),
            "eventId" to stringSchema("Event ID to get agenda items from")
        ),
        required = listOf("eventId")
    )

    val addAgendaItem: JsonObject = objectSchema(
        mapOf(
            "calendarId" to stringSchema("Calendar ID (default: 'primary')"),
            "eventId" to stringSchema("Event ID containing the meeting notes document"),
            "item" to stringSchema("The agenda item text to add")
        ),
        required = listOf("eventId", "item")
    )

    val createDoc: JsonObject = objectSchema(
        mapOf(
            "title" to stringSchema("The title of the Google Doc"),
            "content" to stringSchema("Markdown content to render as formatted text in the document. Supports headings, bold, italic, links, code blocks, bullet/ordered lists, tables, block quotes, horizontal rules, and images.")
        ),
        required = listOf("title", "content")
    )

    val updateDoc: JsonObject = objectSchema(
        mapOf(
            "fileId" to stringSchema("Google Doc file ID"),
            "content" to stringSchema("Markdown content to append to the document, rendered as formatted text")
        ),
        required = listOf("fileId", "content")
    )

    val createEmailReviewDoc: JsonObject = objectSchema(
        mapOf(
            "subject" to stringSchema("Email subject (will be used as document title)"),
            "body" to stringSchema("Email body content"),
            "to" to arraySchema(stringSchema(), "List of recipient email addresses"),
            "cc" to arraySchema(stringSchema(), "List of CC recipients"),
            "bcc" to arraySchema(stringSchema(), "List of BCC recipients")
        ),
        required = listOf("subject", "body")
    )

    // Drive Comments schemas

    val listDocComments: JsonObject = objectSchema(
        mapOf(
            "fileId" to stringSchema("Google Doc file ID"),
            "pageSize" to numberSchema("Number of comments to return per page (default: 20)"),
            "pageToken" to stringSchema("Page token for fetching next page of results"),
            "includeDeleted" to boolSchema("Include deleted comments (default: false)")
        ),
        required = listOf("fileId")
    )

    val getDocComment: JsonObject = objectSchema(
        mapOf(
            "fileId" to stringSchema("Google Doc file ID"),
            "commentId" to stringSchema("ID of the comment to retrieve")
        ),
        required = listOf("fileId", "commentId")
    )

    val createDocComment: JsonObject = objectSchema(
        mapOf(
            "fileId" to stringSchema("Google Doc file ID"),
            "content" to stringSchema("The text content of the comment"),
            "quotedContent" to stringSchema("Text from the document that the comment refers to")
        ),
        required = listOf("fileId", "content")
    )

    val replyToDocComment: JsonObject = objectSchema(
        mapOf(
            "fileId" to stringSchema("Google Doc file ID"),
            "commentId" to stringSchema("ID of the comment to reply to"),
            "content" to stringSchema("The text content of the reply")
        ),
        required = listOf("fileId", "commentId", "content")
    )

    val resolveDocComment: JsonObject = objectSchema(
        mapOf(
            "fileId" to stringSchema("Google Doc file ID"),
            "commentId" to stringSchema("ID of the comment to resolve")
        ),
        required = listOf("fileId", "commentId")
    )

    val deleteDocComment: JsonObject = objectSchema(
        mapOf(
            "fileId" to stringSchema("Google Doc file ID"),
            "commentId" to stringSchema("ID of the comment to delete")
        ),
        required = listOf("fileId", "commentId")
    )

    // Trello schemas

    val listTrelloBoards: JsonObject = objectSchema(
        mapOf(
            "filter" to stringSchema("Board filter: open, closed, all (default: open)")
        )
    )

    val getTrelloBoard: JsonObject = objectSchema(
        mapOf(
            "boardId" to stringSchema("Trello board ID")
        ),
        required = listOf("boardId")
    )

    val searchTrello: JsonObject = objectSchema(
        mapOf(
            "query" to stringSchema("Search query string"),
            "modelTypes" to stringSchema("Types to search: cards, boards (default: cards,boards)"),
            "boardIds" to arraySchema(stringSchema(), "Limit search to specific board IDs"),
            "limit" to numberSchema("Maximum results (default: 10)")
        ),
        required = listOf("query")
    )

    val listTrelloLists: JsonObject = objectSchema(
        mapOf(
            "boardId" to stringSchema("Trello board ID"),
            "filter" to stringSchema("List filter: open, closed, all (default: open)")
        ),
        required = listOf("boardId")
    )

    val createTrelloList: JsonObject = objectSchema(
        mapOf(
            "boardId" to stringSchema("Trello board ID to create the list on"),
            "name" to stringSchema("Name for the new list"),
            "pos" to stringSchema("Position: top, bottom, or positive float")
        ),
        required = listOf("boardId", "name")
    )

    val updateTrelloList: JsonObject = objectSchema(
        mapOf(
            "listId" to stringSchema("Trello list ID"),
            "name" to stringSchema("New name for the list"),
            "pos" to stringSchema("New position: top, bottom, or positive float")
        ),
        required = listOf("listId")
    )

    val archiveTrelloList: JsonObject = objectSchema(
        mapOf(
            "listId" to stringSchema("Trello list ID to archive")
        ),
        required = listOf("listId")
    )

    val listTrelloCards: JsonObject = objectSchema(
        mapOf(
            "boardId" to stringSchema("Trello board ID (provide boardId or listId)"),
            "listId" to stringSchema("Trello list ID (provide boardId or listId)"),
            "filter" to stringSchema("Card filter: open, closed, all (default: open)")
        )
    )

    val getTrelloCard: JsonObject = objectSchema(
        mapOf(
            "cardId" to stringSchema("Trello card ID")
        ),
        required = listOf("cardId")
    )

    val createTrelloCard: JsonObject = objectSchema(
        mapOf(
            "listId" to stringSchema("ID of the list to create the card in"),
            "name" to stringSchema("Card title"),
            "desc" to stringSchema("Card description (supports markdown)"),
            "pos" to stringSchema("Position: top, bottom, or positive float"),
            "due" to stringSchema("Due date (ISO 8601 format)"),
            "labelIds" to arraySchema(stringSchema(), "Label IDs to apply"),
            "memberIds" to arraySchema(stringSchema(), "Member IDs to assign")
        ),
        required = listOf("listId", "name")
    )

    val updateTrelloCard: JsonObject = objectSchema(
        mapOf(
            "cardId" to stringSchema("Trello card ID"),
            "name" to stringSchema("New card title"),
            "desc" to stringSchema("New card description"),
            "pos" to stringSchema("New position: top, bottom, or positive float"),
            "due" to stringSchema("New due date (ISO 8601 format)"),
            "dueComplete" to boolSchema("Mark due date as complete"),
            "labelIds" to arraySchema(stringSchema(), "Label IDs to set (replaces existing)"),
            "memberIds" to arraySchema(stringSchema(), "Member IDs to set (replaces existing)")
        ),
        required = listOf("cardId")
    )

    val moveTrelloCard: JsonObject = objectSchema(
        mapOf(
            "cardId" to stringSchema("Trello card ID to move"),
            "listId" to stringSchema("Target list ID"),
            "boardId" to stringSchema("Target board ID (if moving to different board)"),
            "pos" to stringSchema("Position in target list: top, bottom, or positive float")
        ),
        required = listOf("cardId", "listId")
    )

    val archiveTrelloCard: JsonObject = objectSchema(
        mapOf(
            "cardId" to stringSchema("Trello card ID to archive")
        ),
        required = listOf("cardId")
    )

    val addTrelloCardComment: JsonObject = objectSchema(
        mapOf(
            "cardId" to stringSchema("Trello card ID"),
            "text" to stringSchema("Comment text")
        ),
        required = listOf("cardId", "text")
    )

    val listTrelloLabels: JsonObject = objectSchema(
        mapOf(
            "boardId" to stringSchema("Trello board ID")
        ),
        required = listOf("boardId")
    )

    val createTrelloLabel: JsonObject = objectSchema(
        mapOf(
            "boardId" to stringSchema("Trello board ID"),
            "name" to stringSchema("Label name"),
            "color" to stringSchema("Label color: green, yellow, orange, red, purple, blue, sky, lime, pink, black")
        ),
        required = listOf("boardId", "name", "color")
    )

    val updateTrelloLabel: JsonObject = objectSchema(
        mapOf(
            "labelId" to stringSchema("Trello label ID"),
            "name" to stringSchema("New label name"),
            "color" to stringSchema("New label color: green, yellow, orange, red, purple, blue, sky, lime, pink, black")
        ),
        required = listOf("labelId")
    )

    val deleteTrelloLabel: JsonObject = objectSchema(
        mapOf(
            "labelId" to stringSchema("Trello label ID to delete")
        ),
        required = listOf("labelId")
    )

    val listTrelloBoardMembers: JsonObject = objectSchema(
        mapOf(
            "boardId" to stringSchema("Trello board ID")
        ),
        required = listOf("boardId")
    )

    val assignTrelloCardMembers: JsonObject = objectSchema(
        mapOf(
            "cardId" to stringSchema("Trello card ID"),
            "memberIds" to arraySchema(stringSchema(), "Member IDs to assign (replaces existing members)")
        ),
        required = listOf("cardId", "memberIds")
    )

    val createTrelloChecklist: JsonObject = objectSchema(
        mapOf(
            "cardId" to stringSchema("Trello card ID to add checklist to"),
            "name" to stringSchema("Checklist name")
        ),
        required = listOf("cardId", "name")
    )

    val getTrelloChecklist: JsonObject = objectSchema(
        mapOf(
            "checklistId" to stringSchema("Trello checklist ID")
        ),
        required = listOf("checklistId")
    )

    val addTrelloChecklistItem: JsonObject = objectSchema(
        mapOf(
            "checklistId" to stringSchema("Trello checklist ID"),
            "name" to stringSchema("Check item name"),
            "pos" to stringSchema("Position: top, bottom, or positive float")
        ),
        required = listOf("checklistId", "name")
    )

    val updateTrelloChecklistItem: JsonObject = objectSchema(
        mapOf(
            "cardId" to stringSchema("Trello card ID containing the checklist item"),
            "checklistItemId" to stringSchema("Checklist item ID"),
            "state" to stringSchema("Item state: complete or incomplete"),
            "name" to stringSchema("New item name")
        ),
        required = listOf("cardId", "checklistItemId")
    )

    val deleteTrelloChecklist: JsonObject = objectSchema(
        mapOf(
            "checklistId" to stringSchema("Trello checklist ID to delete")
        ),
        required = listOf("checklistId")
    )

    // Trello Custom Fields

    val listTrelloCustomFields: JsonObject = objectSchema(
        mapOf(
            "boardId" to stringSchema("Trello board ID")
        ),
        required = listOf("boardId")
    )

    val getTrelloCardCustomFields: JsonObject = objectSchema(
        mapOf(
            "cardId" to stringSchema("Trello card ID")
        ),
        required = listOf("cardId")
    )

    val setTrelloCardCustomField: JsonObject = objectSchema(
        mapOf(
            "cardId" to stringSchema("Trello card ID"),
            "customFieldId" to stringSchema("Custom field ID (from list_trello_custom_fields)"),
            "value" to stringSchema("Value to set (for text, number, date, checkbox fields). For checkbox use 'true'/'false'. For date use ISO 8601 format."),
            "idValue" to stringSchema("Option ID to set (for list/dropdown fields only, from list_trello_custom_fields options)")
        ),
        required = listOf("cardId", "customFieldId")
    )
}

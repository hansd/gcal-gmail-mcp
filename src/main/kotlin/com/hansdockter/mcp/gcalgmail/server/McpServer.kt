package com.hansdockter.mcp.gcalgmail.server

import com.hansdockter.mcp.gcalgmail.calendar.*
import com.hansdockter.mcp.gcalgmail.docs.*
import com.hansdockter.mcp.gcalgmail.drive.*
import com.hansdockter.mcp.gcalgmail.gmail.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.serializer
import java.io.BufferedReader
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.InputStreamReader

private val JSON = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

@Serializable
private data class ToolCallParams(
    val name: String,
    val arguments: JsonElement? = null
)

class McpServer(private val gmail: GmailService, private val calendar: CalendarService, private val docs: DocsService, private val driveComments: DriveCommentsService) {
    fun run() {
        val reader = BufferedReader(InputStreamReader(System.`in`))
        val rawOut = FileOutputStream(FileDescriptor.out)

        reader.lineSequence().forEach { line ->
            if (line.isBlank()) return@forEach

            val response: JsonRpcResponse? = try {
                val request = JSON.decodeFromString(JsonRpcRequest.serializer(), line)
                when (request.method) {
                    "initialize" -> handleInitialize(request)
                    "notifications/initialized" -> null
                    "tools/list" -> handleList(request)
                    "tools/call" -> handleCall(request)
                    else -> JsonRpcResponse(
                        id = request.id,
                        error = JsonRpcError(code = -32601, message = "Method not found: ${request.method}")
                    )
                }
            } catch (e: Exception) {
                JsonRpcResponse(
                    error = JsonRpcError(code = -32700, message = "Parse error: ${e.message}")
                )
            }

            if (response != null) {
                val payload = JSON.encodeToString(JsonRpcResponse.serializer(), response)
                rawOut.write(payload.toByteArray(Charsets.UTF_8))
                rawOut.write('\n'.code)
                rawOut.flush()
            }
        }
    }

    private fun handleList(request: JsonRpcRequest): JsonRpcResponse {
        val tools = listOf(
            // Gmail tools
            McpTool("send_email", "Sends a new email", ToolSchemas.sendEmail),
            McpTool("draft_email", "Draft a new email", ToolSchemas.sendEmail),
            McpTool("read_email", "Retrieves the content of a specific email", ToolSchemas.readEmail),
            McpTool("search_emails", "Searches for emails using Gmail search syntax", ToolSchemas.searchEmails),
            McpTool("modify_email", "Modifies email labels (move to different folders)", ToolSchemas.modifyEmail),
            McpTool("delete_email", "Permanently deletes an email", ToolSchemas.deleteEmail),
            McpTool("list_email_labels", "Retrieves all available Gmail labels", ToolSchemas.listEmailLabels),
            McpTool("batch_modify_emails", "Modifies labels for multiple emails in batches", ToolSchemas.batchModifyEmails),
            McpTool("batch_delete_emails", "Permanently deletes multiple emails in batches", ToolSchemas.batchDeleteEmails),
            McpTool("create_label", "Creates a new Gmail label", ToolSchemas.createLabel),
            McpTool("update_label", "Updates an existing Gmail label", ToolSchemas.updateLabel),
            McpTool("delete_label", "Deletes a Gmail label", ToolSchemas.deleteLabel),
            McpTool("get_or_create_label", "Gets an existing label or creates it", ToolSchemas.getOrCreateLabel),
            McpTool("create_filter", "Creates a new Gmail filter", ToolSchemas.createFilter),
            McpTool("list_filters", "Retrieves all Gmail filters", ToolSchemas.listFilters),
            McpTool("get_filter", "Gets details of a specific Gmail filter", ToolSchemas.getFilter),
            McpTool("delete_filter", "Deletes a Gmail filter", ToolSchemas.deleteFilter),
            McpTool("create_filter_from_template", "Creates a filter using a pre-defined template", ToolSchemas.createFilterFromTemplate),
            McpTool("download_attachment", "Downloads an email attachment", ToolSchemas.downloadAttachment),
            // Calendar tools
            McpTool("list_calendar_events", "Lists calendar events with optional filters", ToolSchemas.listEvents),
            McpTool("get_calendar_event", "Gets detailed information about a calendar event", ToolSchemas.getEvent),
            McpTool("create_calendar_event", "Creates a new calendar event with optional attendees and Meet link", ToolSchemas.createEvent),
            McpTool("update_calendar_event", "Updates an existing calendar event", ToolSchemas.updateEvent),
            McpTool("delete_calendar_event", "Deletes a calendar event", ToolSchemas.deleteEvent),
            McpTool("quick_add_calendar_event", "Creates an event from natural language text", ToolSchemas.quickAddEvent),
            McpTool("respond_to_calendar_event", "Accept, decline, or tentatively accept an event invitation", ToolSchemas.respondToEvent),
            McpTool("list_calendars", "Lists all calendars accessible to the user", ToolSchemas.listCalendars),
            McpTool("get_free_busy", "Checks availability for specified calendars", ToolSchemas.getFreeBusy),
            McpTool("list_event_instances", "Lists instances of a recurring event", ToolSchemas.listEventInstances),
            McpTool("list_event_attachments", "Lists attachments for a calendar event", ToolSchemas.listEventAttachments),
            // Docs tools
            McpTool("get_meeting_notes", "Gets the Google Doc meeting notes attached to a calendar event", ToolSchemas.getMeetingNotes),
            McpTool("read_doc_content", "Reads the full text content of a Google Doc by its document ID", ToolSchemas.readDocContent),
            McpTool("get_agenda_items", "Gets the agenda items from the meeting notes document attached to a calendar event", ToolSchemas.getAgendaItems),
            McpTool("add_agenda_item", "Adds an agenda item to the meeting notes document attached to a calendar event", ToolSchemas.addAgendaItem),
            McpTool("create_doc", "Creates a new Google Doc with markdown content rendered as richly formatted text (headings, bold, italic, links, code, lists, tables)", ToolSchemas.createDoc),
            McpTool("update_doc", "Appends markdown content to an existing Google Doc, rendered as richly formatted text", ToolSchemas.updateDoc),
            McpTool("create_email_review_doc", "Creates a Google Doc with email content for collaborative review before sending", ToolSchemas.createEmailReviewDoc),
            // Drive Comments tools
            McpTool("list_doc_comments", "Lists comments on a Google Doc", ToolSchemas.listDocComments),
            McpTool("get_doc_comment", "Gets a specific comment with all its replies", ToolSchemas.getDocComment),
            McpTool("create_doc_comment", "Creates a new comment on a Google Doc", ToolSchemas.createDocComment),
            McpTool("reply_to_doc_comment", "Replies to an existing comment on a Google Doc", ToolSchemas.replyToDocComment),
            McpTool("resolve_doc_comment", "Resolves (closes) a comment thread on a Google Doc", ToolSchemas.resolveDocComment),
            McpTool("delete_doc_comment", "Deletes a comment from a Google Doc", ToolSchemas.deleteDocComment)
        )

        val result = ToolListResult(tools)
        return JsonRpcResponse(id = request.id, result = JSON.encodeToJsonElement(result))
    }

    private fun handleInitialize(request: JsonRpcRequest): JsonRpcResponse {
        val result = InitializeResult(
            capabilities = ServerCapabilities(),
            serverInfo = ServerInfo(
                name = "gcal-gmail-mcp",
                version = "0.1.0"
            )
        )
        return JsonRpcResponse(id = request.id, result = JSON.encodeToJsonElement(result))
    }

    private fun handleCall(request: JsonRpcRequest): JsonRpcResponse {
        return try {
            val params = JSON.decodeFromJsonElement(ToolCallParams.serializer(), request.params ?: JsonObject(emptyMap()))
            val args = params.arguments

            val text = when (params.name) {
                "send_email" -> {
                    val payload = decodeArgs<SendEmailArgs>(args)
                    val id = gmail.sendEmail(payload, draft = false)
                    "Email sent successfully with ID: $id"
                }
                "draft_email" -> {
                    val payload = decodeArgs<SendEmailArgs>(args)
                    val id = gmail.sendEmail(payload, draft = true)
                    "Email draft created successfully with ID: $id"
                }
                "read_email" -> {
                    val payload = decodeArgs<ReadEmailArgs>(args)
                    gmail.readEmail(payload.messageId)
                }
                "search_emails" -> {
                    val payload = decodeArgs<SearchEmailsArgs>(args)
                    gmail.searchEmails(payload.query, payload.maxResults ?: 10)
                }
                "modify_email" -> {
                    val payload = decodeArgs<ModifyEmailArgs>(args)
                    gmail.modifyEmail(payload)
                }
                "delete_email" -> {
                    val payload = decodeArgs<DeleteEmailArgs>(args)
                    gmail.deleteEmail(payload.messageId)
                }
                "list_email_labels" -> gmail.listLabels()
                "batch_modify_emails" -> {
                    val payload = decodeArgs<BatchModifyEmailsArgs>(args)
                    gmail.batchModifyEmails(payload)
                }
                "batch_delete_emails" -> {
                    val payload = decodeArgs<BatchDeleteEmailsArgs>(args)
                    gmail.batchDeleteEmails(payload)
                }
                "create_label" -> {
                    val payload = decodeArgs<CreateLabelArgs>(args)
                    val label = gmail.createLabel(payload)
                    "Label created successfully:\nID: ${label.id}\nName: ${label.name}\nType: ${label.type}"
                }
                "update_label" -> {
                    val payload = decodeArgs<UpdateLabelArgs>(args)
                    val label = gmail.updateLabel(payload)
                    "Label updated successfully:\nID: ${label.id}\nName: ${label.name}\nType: ${label.type}"
                }
                "delete_label" -> {
                    val payload = decodeArgs<DeleteLabelArgs>(args)
                    gmail.deleteLabel(payload.id)
                }
                "get_or_create_label" -> {
                    val payload = decodeArgs<GetOrCreateLabelArgs>(args)
                    val label = gmail.getOrCreateLabel(payload)
                    "Successfully got or created label:\nID: ${label.id}\nName: ${label.name}\nType: ${label.type}"
                }
                "create_filter" -> {
                    val payload = decodeArgs<CreateFilterArgs>(args)
                    val filter = gmail.createFilter(payload)
                    "Filter created successfully:\nID: ${filter.id}"
                }
                "list_filters" -> {
                    val filters = gmail.listFilters()
                    if (filters.isEmpty()) {
                        "No filters found."
                    } else {
                        val entries = filters.joinToString("\n") { f ->
                            val criteria = f.criteria
                            val action = f.action
                            val criteriaText = listOfNotNull(
                                criteria.from?.let { "from: $it" },
                                criteria.to?.let { "to: $it" },
                                criteria.subject?.let { "subject: $it" },
                                criteria.query?.let { "query: $it" }
                            ).joinToString(", ")
                            val actionText = listOfNotNull(
                                action.addLabelIds?.takeIf { it.isNotEmpty() }?.let { "addLabelIds: ${it.joinToString(",")}" },
                                action.removeLabelIds?.takeIf { it.isNotEmpty() }?.let { "removeLabelIds: ${it.joinToString(",")}" },
                                action.forward?.let { "forward: $it" }
                            ).joinToString(", ")
                            "ID: ${f.id}\nCriteria: $criteriaText\nActions: $actionText\n"
                        }
                        "Found ${filters.size} filters:\n\n$entries"
                    }
                }
                "get_filter" -> {
                    val payload = decodeArgs<GetFilterArgs>(args)
                    val filter = gmail.getFilter(payload.filterId)
                    "Filter details:\nID: ${filter.id}"
                }
                "delete_filter" -> {
                    val payload = decodeArgs<DeleteFilterArgs>(args)
                    gmail.deleteFilter(payload.filterId)
                }
                "create_filter_from_template" -> {
                    val payload = decodeArgs<CreateFilterFromTemplateArgs>(args)
                    val filter = gmail.createFilterFromTemplate(payload)
                    "Filter created from template '${payload.template}':\nID: ${filter.id}"
                }
                "download_attachment" -> {
                    val payload = decodeArgs<DownloadAttachmentArgs>(args)
                    gmail.downloadAttachment(payload)
                }
                // Calendar tools
                "list_calendar_events" -> {
                    val payload = decodeArgs<ListEventsArgs>(args)
                    calendar.listEvents(payload)
                }
                "get_calendar_event" -> {
                    val payload = decodeArgs<GetEventArgs>(args)
                    calendar.getEvent(payload)
                }
                "create_calendar_event" -> {
                    val payload = decodeArgs<CreateEventArgs>(args)
                    calendar.createEvent(payload)
                }
                "update_calendar_event" -> {
                    val payload = decodeArgs<UpdateEventArgs>(args)
                    calendar.updateEvent(payload)
                }
                "delete_calendar_event" -> {
                    val payload = decodeArgs<DeleteEventArgs>(args)
                    calendar.deleteEvent(payload)
                }
                "quick_add_calendar_event" -> {
                    val payload = decodeArgs<QuickAddEventArgs>(args)
                    calendar.quickAddEvent(payload)
                }
                "respond_to_calendar_event" -> {
                    val payload = decodeArgs<RespondToEventArgs>(args)
                    calendar.respondToEvent(payload)
                }
                "list_calendars" -> {
                    val payload = decodeArgs<ListCalendarsArgs>(args)
                    calendar.listCalendars(payload)
                }
                "get_free_busy" -> {
                    val payload = decodeArgs<GetFreeBusyArgs>(args)
                    calendar.getFreeBusy(payload)
                }
                "list_event_instances" -> {
                    val payload = decodeArgs<ListEventInstancesArgs>(args)
                    calendar.listEventInstances(payload)
                }
                "list_event_attachments" -> {
                    val payload = decodeArgs<ListEventAttachmentsArgs>(args)
                    calendar.listEventAttachments(payload)
                }
                // Docs tools
                "get_meeting_notes" -> {
                    val payload = decodeArgs<GetMeetingNotesArgs>(args)
                    docs.getMeetingNotes(payload)
                }
                "read_doc_content" -> {
                    val payload = decodeArgs<ReadDocContentArgs>(args)
                    docs.readDocContent(payload)
                }
                "get_agenda_items" -> {
                    val payload = decodeArgs<GetAgendaItemsArgs>(args)
                    docs.getAgendaItems(payload)
                }
                "add_agenda_item" -> {
                    val payload = decodeArgs<AddAgendaItemArgs>(args)
                    docs.addAgendaItem(payload)
                }
                "create_doc" -> {
                    val payload = decodeArgs<CreateDocArgs>(args)
                    docs.createDoc(payload)
                }
                "update_doc" -> {
                    val payload = decodeArgs<UpdateDocArgs>(args)
                    docs.updateDoc(payload)
                }
                "create_email_review_doc" -> {
                    val payload = decodeArgs<CreateEmailReviewDocArgs>(args)
                    docs.createEmailReviewDoc(payload)
                }
                // Drive Comments tools
                "list_doc_comments" -> {
                    val payload = decodeArgs<ListDocCommentsArgs>(args)
                    driveComments.listComments(payload)
                }
                "get_doc_comment" -> {
                    val payload = decodeArgs<GetDocCommentArgs>(args)
                    driveComments.getComment(payload)
                }
                "create_doc_comment" -> {
                    val payload = decodeArgs<CreateDocCommentArgs>(args)
                    driveComments.createComment(payload)
                }
                "reply_to_doc_comment" -> {
                    val payload = decodeArgs<ReplyToDocCommentArgs>(args)
                    driveComments.replyToComment(payload)
                }
                "resolve_doc_comment" -> {
                    val payload = decodeArgs<ResolveDocCommentArgs>(args)
                    driveComments.resolveComment(payload)
                }
                "delete_doc_comment" -> {
                    val payload = decodeArgs<DeleteDocCommentArgs>(args)
                    driveComments.deleteComment(payload)
                }
                else -> "Unknown tool: ${params.name}"
            }

            val result = ToolCallResult(listOf(ToolContent(type = "text", text = text)))
            JsonRpcResponse(id = request.id, result = JSON.encodeToJsonElement(result))
        } catch (e: Exception) {
            JsonRpcResponse(
                id = request.id,
                error = JsonRpcError(code = -32000, message = e.message ?: "Unknown error")
            )
        }
    }

    private inline fun <reified T> decodeArgs(args: JsonElement?): T {
        val element = args ?: JsonObject(emptyMap())
        return JSON.decodeFromJsonElement(serializer(), element)
    }
}

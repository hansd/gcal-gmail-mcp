package com.hansdockter.mcp.gcalgmail.server

import com.hansdockter.mcp.gcalgmail.calendar.*
import com.hansdockter.mcp.gcalgmail.docs.*
import com.hansdockter.mcp.gcalgmail.drive.*
import com.hansdockter.mcp.gcalgmail.gmail.*
import com.hansdockter.mcp.gcalgmail.meet.*
import com.hansdockter.mcp.gcalgmail.tasks.*
import com.hansdockter.mcp.gcalgmail.sheets.*
import com.hansdockter.mcp.gcalgmail.trello.*
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

class McpServer(private val gmail: GmailService, private val calendar: CalendarService, private val docs: DocsService, private val driveComments: DriveCommentsService, private val meet: MeetService, private val tasksService: TasksService, private val sheetsService: SheetsService, private val trello: TrelloService? = null) {
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
            McpTool("replace_doc_text", "Replaces text in a Google Doc using find/replace pairs. Supports multiple replacements in a single call with case-sensitive or case-insensitive matching.", ToolSchemas.replaceDocText),
            McpTool("create_email_review_doc", "Creates a Google Doc with email content for collaborative review before sending", ToolSchemas.createEmailReviewDoc),
            // Drive Comments tools
            McpTool("list_doc_comments", "Lists comments on a Google Doc", ToolSchemas.listDocComments),
            McpTool("get_doc_comment", "Gets a specific comment with all its replies", ToolSchemas.getDocComment),
            McpTool("create_doc_comment", "Creates a new comment on a Google Doc", ToolSchemas.createDocComment),
            McpTool("reply_to_doc_comment", "Replies to an existing comment on a Google Doc", ToolSchemas.replyToDocComment),
            McpTool("resolve_doc_comment", "Resolves (closes) a comment thread on a Google Doc", ToolSchemas.resolveDocComment),
            McpTool("delete_doc_comment", "Deletes a comment from a Google Doc", ToolSchemas.deleteDocComment),
            // Meet tools
            McpTool("list_conference_records", "Lists recent Google Meet conference records (meetings). Supports time-based filtering. Use get_transcript to find transcript Docs for a conference.", ToolSchemas.listConferenceRecords),
            McpTool("get_transcript", "Gets transcript metadata for a Google Meet conference, including the Google Docs file ID. Use read_doc_content with the returned document ID to read the full transcript.", ToolSchemas.getTranscript),
            McpTool("list_transcript_entries", "Lists raw transcript entries (speaker, text, timestamps) from the Meet API. Entries are deleted 30 days after the meeting; use read_doc_content for permanent access.", ToolSchemas.listTranscriptEntries),
            // Google Tasks tools
            McpTool("list_task_lists", "Lists all Google Tasks task lists", ToolSchemas.listTaskLists),
            McpTool("create_task_list", "Creates a new Google Tasks task list", ToolSchemas.createTaskList),
            McpTool("delete_task_list", "Deletes a Google Tasks task list", ToolSchemas.deleteTaskList),
            McpTool("list_tasks", "Lists tasks in a Google Tasks task list", ToolSchemas.listTasks),
            McpTool("get_task", "Gets a specific Google Tasks task", ToolSchemas.getTask),
            McpTool("create_task", "Creates a new task in a Google Tasks task list", ToolSchemas.createTask),
            McpTool("update_task", "Updates a Google Tasks task (title, notes, due date, status)", ToolSchemas.updateTask),
            McpTool("delete_task", "Deletes a Google Tasks task", ToolSchemas.deleteTask),
            McpTool("move_task", "Moves/reorders a task within a Google Tasks task list", ToolSchemas.moveTask),
            McpTool("clear_completed_tasks", "Clears all completed tasks from a Google Tasks task list", ToolSchemas.clearCompletedTasks),
            // Google Sheets tools
            McpTool("get_spreadsheet", "Gets spreadsheet metadata including title, sheets/tabs, and named ranges", ToolSchemas.getSpreadsheet),
            McpTool("read_sheet_values", "Reads cell values from a spreadsheet range (e.g., 'Sheet1!A1:D10')", ToolSchemas.readSheetValues),
            McpTool("read_sheet_multiple_ranges", "Reads cell values from multiple ranges in one call", ToolSchemas.readSheetMultipleRanges),
            McpTool("update_sheet_values", "Updates cell values in a spreadsheet range", ToolSchemas.updateSheetValues),
            McpTool("append_sheet_values", "Appends rows after existing data in a spreadsheet range", ToolSchemas.appendSheetValues),
            McpTool("create_spreadsheet", "Creates a new Google Sheets spreadsheet with optional sheet names", ToolSchemas.createSpreadsheet),
            McpTool("create_sheet", "Adds a new sheet/tab to an existing spreadsheet", ToolSchemas.createSheet)
        )

        val trelloTools = if (trello != null) listOf(
            // Trello tools
            McpTool("list_trello_boards", "List Trello boards for current user", ToolSchemas.listTrelloBoards),
            McpTool("get_trello_board", "Get Trello board details including lists and members", ToolSchemas.getTrelloBoard),
            McpTool("create_trello_board", "Create a new Trello board", ToolSchemas.createTrelloBoard),
            McpTool("search_trello", "Search across Trello boards and cards", ToolSchemas.searchTrello),
            McpTool("list_trello_lists", "List lists on a Trello board", ToolSchemas.listTrelloLists),
            McpTool("create_trello_list", "Create a new list on a Trello board", ToolSchemas.createTrelloList),
            McpTool("update_trello_list", "Update a Trello list name or position", ToolSchemas.updateTrelloList),
            McpTool("archive_trello_list", "Archive a Trello list", ToolSchemas.archiveTrelloList),
            McpTool("list_trello_cards", "List cards on a Trello board or in a list", ToolSchemas.listTrelloCards),
            McpTool("get_trello_card", "Get Trello card details including checklists, comments, and members", ToolSchemas.getTrelloCard),
            McpTool("create_trello_card", "Create a new Trello card", ToolSchemas.createTrelloCard),
            McpTool("update_trello_card", "Update a Trello card (name, description, due date, labels, members)", ToolSchemas.updateTrelloCard),
            McpTool("move_trello_card", "Move a Trello card to a different list or board", ToolSchemas.moveTrelloCard),
            McpTool("archive_trello_card", "Archive a Trello card", ToolSchemas.archiveTrelloCard),
            McpTool("add_trello_card_comment", "Add a comment to a Trello card", ToolSchemas.addTrelloCardComment),
            McpTool("list_trello_labels", "List labels on a Trello board", ToolSchemas.listTrelloLabels),
            McpTool("create_trello_label", "Create a new label on a Trello board", ToolSchemas.createTrelloLabel),
            McpTool("update_trello_label", "Update a Trello label", ToolSchemas.updateTrelloLabel),
            McpTool("delete_trello_label", "Delete a Trello label", ToolSchemas.deleteTrelloLabel),
            McpTool("list_trello_board_members", "List members on a Trello board", ToolSchemas.listTrelloBoardMembers),
            McpTool("assign_trello_card_members", "Assign members to a Trello card (replaces existing)", ToolSchemas.assignTrelloCardMembers),
            McpTool("create_trello_checklist", "Create a checklist on a Trello card", ToolSchemas.createTrelloChecklist),
            McpTool("get_trello_checklist", "Get a Trello checklist with all items", ToolSchemas.getTrelloChecklist),
            McpTool("add_trello_checklist_item", "Add an item to a Trello checklist", ToolSchemas.addTrelloChecklistItem),
            McpTool("update_trello_checklist_item", "Update or toggle a Trello checklist item", ToolSchemas.updateTrelloChecklistItem),
            McpTool("delete_trello_checklist", "Delete a Trello checklist", ToolSchemas.deleteTrelloChecklist),
            McpTool("list_trello_custom_fields", "List custom field definitions on a Trello board (names, types, dropdown options)", ToolSchemas.listTrelloCustomFields),
            McpTool("get_trello_card_custom_fields", "Get custom field values for a Trello card (e.g. Owner, Accountable, Project)", ToolSchemas.getTrelloCardCustomFields),
            McpTool("set_trello_card_custom_field", "Set a custom field value on a Trello card", ToolSchemas.setTrelloCardCustomField)
        ) else emptyList()

        val result = ToolListResult(tools + trelloTools)
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
                "replace_doc_text" -> {
                    val payload = decodeArgs<ReplaceDocTextArgs>(args)
                    docs.replaceDocText(payload)
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
                // Meet tools
                "list_conference_records" -> {
                    val payload = decodeArgs<ListConferenceRecordsArgs>(args)
                    meet.listConferenceRecords(payload)
                }
                "get_transcript" -> {
                    val payload = decodeArgs<GetTranscriptArgs>(args)
                    meet.getTranscript(payload)
                }
                "list_transcript_entries" -> {
                    val payload = decodeArgs<ListTranscriptEntriesArgs>(args)
                    meet.listTranscriptEntries(payload)
                }
                // Google Tasks tools
                "list_task_lists" -> {
                    val payload = decodeArgs<ListTaskListsArgs>(args)
                    tasksService.listTaskLists(payload)
                }
                "create_task_list" -> {
                    val payload = decodeArgs<CreateTaskListArgs>(args)
                    tasksService.createTaskList(payload)
                }
                "delete_task_list" -> {
                    val payload = decodeArgs<DeleteTaskListArgs>(args)
                    tasksService.deleteTaskList(payload)
                }
                "list_tasks" -> {
                    val payload = decodeArgs<ListTasksArgs>(args)
                    tasksService.listTasks(payload)
                }
                "get_task" -> {
                    val payload = decodeArgs<GetTaskArgs>(args)
                    tasksService.getTask(payload)
                }
                "create_task" -> {
                    val payload = decodeArgs<CreateTaskArgs>(args)
                    tasksService.createTask(payload)
                }
                "update_task" -> {
                    val payload = decodeArgs<UpdateTaskArgs>(args)
                    tasksService.updateTask(payload)
                }
                "delete_task" -> {
                    val payload = decodeArgs<DeleteTaskArgs>(args)
                    tasksService.deleteTask(payload)
                }
                "move_task" -> {
                    val payload = decodeArgs<MoveTaskArgs>(args)
                    tasksService.moveTask(payload)
                }
                "clear_completed_tasks" -> {
                    val payload = decodeArgs<ClearCompletedTasksArgs>(args)
                    tasksService.clearCompletedTasks(payload)
                }
                // Google Sheets tools
                "get_spreadsheet" -> {
                    val payload = decodeArgs<GetSpreadsheetArgs>(args)
                    sheetsService.getSpreadsheet(payload)
                }
                "read_sheet_values" -> {
                    val payload = decodeArgs<ReadSheetValuesArgs>(args)
                    sheetsService.readSheetValues(payload)
                }
                "read_sheet_multiple_ranges" -> {
                    val payload = decodeArgs<ReadSheetMultipleRangesArgs>(args)
                    sheetsService.readSheetMultipleRanges(payload)
                }
                "update_sheet_values" -> {
                    val payload = decodeArgs<UpdateSheetValuesArgs>(args)
                    sheetsService.updateSheetValues(payload)
                }
                "append_sheet_values" -> {
                    val payload = decodeArgs<AppendSheetValuesArgs>(args)
                    sheetsService.appendSheetValues(payload)
                }
                "create_spreadsheet" -> {
                    val payload = decodeArgs<CreateSpreadsheetArgs>(args)
                    sheetsService.createSpreadsheet(payload)
                }
                "create_sheet" -> {
                    val payload = decodeArgs<CreateSheetArgs>(args)
                    sheetsService.createSheet(payload)
                }
                // Trello tools
                "list_trello_boards" -> {
                    val t = trello ?: error("Trello not configured. Run 'trello-auth' first.")
                    val payload = decodeArgs<ListTrelloBoardsArgs>(args)
                    t.listBoards(payload)
                }
                "get_trello_board" -> {
                    val t = trello ?: error("Trello not configured. Run 'trello-auth' first.")
                    val payload = decodeArgs<GetTrelloBoardArgs>(args)
                    t.getBoard(payload)
                }
                "create_trello_board" -> {
                    val t = trello ?: error("Trello not configured. Run 'trello-auth' first.")
                    val payload = decodeArgs<CreateTrelloBoardArgs>(args)
                    t.createBoard(payload)
                }
                "search_trello" -> {
                    val t = trello ?: error("Trello not configured. Run 'trello-auth' first.")
                    val payload = decodeArgs<SearchTrelloArgs>(args)
                    t.search(payload)
                }
                "list_trello_lists" -> {
                    val t = trello ?: error("Trello not configured. Run 'trello-auth' first.")
                    val payload = decodeArgs<ListTrelloListsArgs>(args)
                    t.listLists(payload)
                }
                "create_trello_list" -> {
                    val t = trello ?: error("Trello not configured. Run 'trello-auth' first.")
                    val payload = decodeArgs<CreateTrelloListArgs>(args)
                    t.createList(payload)
                }
                "update_trello_list" -> {
                    val t = trello ?: error("Trello not configured. Run 'trello-auth' first.")
                    val payload = decodeArgs<UpdateTrelloListArgs>(args)
                    t.updateList(payload)
                }
                "archive_trello_list" -> {
                    val t = trello ?: error("Trello not configured. Run 'trello-auth' first.")
                    val payload = decodeArgs<ArchiveTrelloListArgs>(args)
                    t.archiveList(payload)
                }
                "list_trello_cards" -> {
                    val t = trello ?: error("Trello not configured. Run 'trello-auth' first.")
                    val payload = decodeArgs<ListTrelloCardsArgs>(args)
                    t.listCards(payload)
                }
                "get_trello_card" -> {
                    val t = trello ?: error("Trello not configured. Run 'trello-auth' first.")
                    val payload = decodeArgs<GetTrelloCardArgs>(args)
                    t.getCard(payload)
                }
                "create_trello_card" -> {
                    val t = trello ?: error("Trello not configured. Run 'trello-auth' first.")
                    val payload = decodeArgs<CreateTrelloCardArgs>(args)
                    t.createCard(payload)
                }
                "update_trello_card" -> {
                    val t = trello ?: error("Trello not configured. Run 'trello-auth' first.")
                    val payload = decodeArgs<UpdateTrelloCardArgs>(args)
                    t.updateCard(payload)
                }
                "move_trello_card" -> {
                    val t = trello ?: error("Trello not configured. Run 'trello-auth' first.")
                    val payload = decodeArgs<MoveTrelloCardArgs>(args)
                    t.moveCard(payload)
                }
                "archive_trello_card" -> {
                    val t = trello ?: error("Trello not configured. Run 'trello-auth' first.")
                    val payload = decodeArgs<ArchiveTrelloCardArgs>(args)
                    t.archiveCard(payload)
                }
                "add_trello_card_comment" -> {
                    val t = trello ?: error("Trello not configured. Run 'trello-auth' first.")
                    val payload = decodeArgs<AddTrelloCardCommentArgs>(args)
                    t.addCardComment(payload)
                }
                "list_trello_labels" -> {
                    val t = trello ?: error("Trello not configured. Run 'trello-auth' first.")
                    val payload = decodeArgs<ListTrelloLabelsArgs>(args)
                    t.listLabels(payload)
                }
                "create_trello_label" -> {
                    val t = trello ?: error("Trello not configured. Run 'trello-auth' first.")
                    val payload = decodeArgs<CreateTrelloLabelArgs>(args)
                    t.createLabel(payload)
                }
                "update_trello_label" -> {
                    val t = trello ?: error("Trello not configured. Run 'trello-auth' first.")
                    val payload = decodeArgs<UpdateTrelloLabelArgs>(args)
                    t.updateLabel(payload)
                }
                "delete_trello_label" -> {
                    val t = trello ?: error("Trello not configured. Run 'trello-auth' first.")
                    val payload = decodeArgs<DeleteTrelloLabelArgs>(args)
                    t.deleteLabel(payload)
                }
                "list_trello_board_members" -> {
                    val t = trello ?: error("Trello not configured. Run 'trello-auth' first.")
                    val payload = decodeArgs<ListTrelloBoardMembersArgs>(args)
                    t.listBoardMembers(payload)
                }
                "assign_trello_card_members" -> {
                    val t = trello ?: error("Trello not configured. Run 'trello-auth' first.")
                    val payload = decodeArgs<AssignTrelloCardMembersArgs>(args)
                    t.assignCardMembers(payload)
                }
                "create_trello_checklist" -> {
                    val t = trello ?: error("Trello not configured. Run 'trello-auth' first.")
                    val payload = decodeArgs<CreateTrelloChecklistArgs>(args)
                    t.createChecklist(payload)
                }
                "get_trello_checklist" -> {
                    val t = trello ?: error("Trello not configured. Run 'trello-auth' first.")
                    val payload = decodeArgs<GetTrelloChecklistArgs>(args)
                    t.getChecklist(payload)
                }
                "add_trello_checklist_item" -> {
                    val t = trello ?: error("Trello not configured. Run 'trello-auth' first.")
                    val payload = decodeArgs<AddTrelloChecklistItemArgs>(args)
                    t.addChecklistItem(payload)
                }
                "update_trello_checklist_item" -> {
                    val t = trello ?: error("Trello not configured. Run 'trello-auth' first.")
                    val payload = decodeArgs<UpdateTrelloChecklistItemArgs>(args)
                    t.updateChecklistItem(payload)
                }
                "delete_trello_checklist" -> {
                    val t = trello ?: error("Trello not configured. Run 'trello-auth' first.")
                    val payload = decodeArgs<DeleteTrelloChecklistArgs>(args)
                    t.deleteChecklist(payload)
                }
                "list_trello_custom_fields" -> {
                    val t = trello ?: error("Trello not configured. Run 'trello-auth' first.")
                    val payload = decodeArgs<ListTrelloCustomFieldsArgs>(args)
                    t.listCustomFields(payload)
                }
                "get_trello_card_custom_fields" -> {
                    val t = trello ?: error("Trello not configured. Run 'trello-auth' first.")
                    val payload = decodeArgs<GetTrelloCardCustomFieldsArgs>(args)
                    t.getCardCustomFields(payload)
                }
                "set_trello_card_custom_field" -> {
                    val t = trello ?: error("Trello not configured. Run 'trello-auth' first.")
                    val payload = decodeArgs<SetTrelloCardCustomFieldArgs>(args)
                    t.setCardCustomField(payload)
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

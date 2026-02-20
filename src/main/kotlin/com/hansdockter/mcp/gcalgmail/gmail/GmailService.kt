package com.hansdockter.mcp.gcalgmail.gmail

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.model.Label
import com.google.api.services.gmail.model.Message
import com.google.api.services.gmail.model.MessagePart
import com.google.api.services.gmail.model.MessagePartHeader
import com.google.api.services.gmail.model.ModifyMessageRequest
import com.google.api.services.gmail.model.Profile
import com.google.api.services.gmail.model.Filter
import com.google.api.client.googleapis.batch.BatchRequest
import com.google.api.client.googleapis.batch.json.JsonBatchCallback
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.http.HttpHeaders
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

class GmailService(private val credential: Credential) {
    private val transport = NetHttpTransport()
    private val jsonFactory = GsonFactory.getDefaultInstance()

    private val gmail: Gmail = Gmail.Builder(transport, jsonFactory, credential)
        .setApplicationName("gcal-gmail-mcp")
        .build()

    data class AttachmentInfo(
        val id: String,
        val filename: String,
        val mimeType: String,
        val size: Int
    )

    fun getProfile(): Profile = gmail.users().getProfile("me").execute()

    fun sendEmail(args: SendEmailArgs, draft: Boolean): String {
        val fromEmail = getProfile().emailAddress ?: "me"
        val raw = EmailComposer.createRawMessage(args, fromEmail)
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray(Charsets.UTF_8))

        return if (draft) {
            val message = Message().apply {
                setRaw(encoded)
                if (args.threadId != null) setThreadId(args.threadId)
            }
            val draftResult = gmail.users().drafts().create("me", com.google.api.services.gmail.model.Draft().setMessage(message)).execute()
            draftResult.id
        } else {
            val message = Message().apply {
                setRaw(encoded)
                if (args.threadId != null) setThreadId(args.threadId)
            }
            val result = gmail.users().messages().send("me", message).execute()
            result.id
        }
    }

    fun readEmail(messageId: String): String {
        val response = gmail.users().messages().get("me", messageId).setFormat("full").execute()
        val headers = response.payload?.headers ?: emptyList()

        val subject = headers.findHeader("Subject")
        val from = headers.findHeader("From")
        val to = headers.findHeader("To")
        val date = headers.findHeader("Date")
        val threadId = response.threadId ?: ""

        val content = extractEmailContent(response.payload)
        val body = content.text.ifBlank { content.html }
        val contentNote = if (content.text.isBlank() && content.html.isNotBlank()) {
            "[Note: This email is HTML-formatted. Plain text version not available.]\n\n"
        } else {
            ""
        }

        val attachments = mutableListOf<AttachmentInfo>()
        response.payload?.let { collectAttachments(it, attachments) }

        val attachmentInfo = if (attachments.isNotEmpty()) {
            "\n\nAttachments (${attachments.size}):\n" + attachments.joinToString("\n") {
                "- ${it.filename} (${it.mimeType}, ${kotlin.math.round(it.size / 1024.0)} KB, ID: ${it.id})"
            }
        } else {
            ""
        }

        return "Thread ID: $threadId\nSubject: $subject\nFrom: $from\nTo: $to\nDate: $date\n\n${contentNote}${body}${attachmentInfo}"
    }

    fun searchEmails(query: String, maxResults: Int = 10): String {
        // Phase 1: Collect all message IDs using pagination
        // Gmail API may return fewer results per page than maxResults, so we must
        // follow nextPageToken to get the full result set.
        val allMessages = mutableListOf<Message>()
        var pageToken: String? = null
        val remaining = maxResults

        do {
            val request = gmail.users().messages().list("me")
                .setQ(query)
                .setMaxResults(minOf(remaining - allMessages.size, 500).toLong())
            if (pageToken != null) request.setPageToken(pageToken)

            val response = request.execute()
            val pageMessages = response.messages ?: emptyList()
            allMessages.addAll(pageMessages)
            pageToken = response.nextPageToken
        } while (pageToken != null && allMessages.size < remaining)

        if (allMessages.isEmpty()) return "No messages found."

        // Phase 2: Fetch metadata for all messages using batch API (max 100 per batch)
        val detailsMap = mutableMapOf<String, Message>()
        val errors = mutableListOf<String>()

        allMessages.chunked(100).forEach { chunk ->
            val batch: BatchRequest = gmail.batch()
            val callback = object : JsonBatchCallback<Message>() {
                override fun onSuccess(message: Message, responseHeaders: HttpHeaders) {
                    detailsMap[message.id] = message
                }

                override fun onFailure(error: GoogleJsonError, responseHeaders: HttpHeaders) {
                    errors.add("Failed to fetch message: ${error.message}")
                }
            }

            chunk.forEach { msg ->
                gmail.users().messages().get("me", msg.id)
                    .setFormat("metadata")
                    .setMetadataHeaders(listOf("Subject", "From", "Date"))
                    .queue(batch, callback)
            }

            batch.execute()
        }

        // Preserve original order from search results
        val results = allMessages.mapNotNull { msg ->
            val detail = detailsMap[msg.id] ?: return@mapNotNull null
            val headers = detail.payload?.headers ?: emptyList()
            val subject = headers.findHeader("Subject")
            val from = headers.findHeader("From")
            val date = headers.findHeader("Date")

            "ID: ${msg.id}\nSubject: $subject\nFrom: $from\nDate: $date\n"
        }

        val errorSuffix = if (errors.isNotEmpty()) "\n\nWarnings:\n${errors.joinToString("\n")}" else ""
        return "Total results: ${allMessages.size}\n\n" + results.joinToString("\n") + errorSuffix
    }

    fun modifyEmail(args: ModifyEmailArgs): String {
        val request = ModifyMessageRequest()
        when {
            args.labelIds != null -> request.addLabelIds = args.labelIds
            else -> {
                if (args.addLabelIds != null) request.addLabelIds = args.addLabelIds
                if (args.removeLabelIds != null) request.removeLabelIds = args.removeLabelIds
            }
        }

        gmail.users().messages().modify("me", args.messageId, request).execute()
        return "Email ${args.messageId} labels updated successfully"
    }

    fun deleteEmail(messageId: String): String {
        gmail.users().messages().delete("me", messageId).execute()
        return "Email $messageId deleted successfully"
    }

    fun listLabels(): String {
        val response = gmail.users().labels().list("me").execute()
        val labels = response.labels ?: emptyList()

        val systemLabels = labels.filter { it.type == "system" }
        val userLabels = labels.filter { it.type == "user" }

        val summary = "Found ${labels.size} labels (${systemLabels.size} system, ${userLabels.size} user):\n\n"

        val systemText = "System Labels:\n" + systemLabels.joinToString("\n") { l ->
            "ID: ${l.id}\nName: ${l.name}\n"
        }

        val userText = "\nUser Labels:\n" + userLabels.joinToString("\n") { l ->
            "ID: ${l.id}\nName: ${l.name}\n"
        }

        return summary + systemText + userText
    }

    fun batchModifyEmails(args: BatchModifyEmailsArgs): String {
        val successes = mutableListOf<String>()
        val failures = mutableListOf<Pair<String, String>>()

        val request = ModifyMessageRequest().apply {
            addLabelIds = args.addLabelIds
            removeLabelIds = args.removeLabelIds
        }

        processBatches(args.messageIds, args.batchSize) { batch ->
            batch.forEach { id ->
                try {
                    gmail.users().messages().modify("me", id, request).execute()
                    successes.add(id)
                } catch (e: Exception) {
                    failures.add(id to (e.message ?: "Unknown error"))
                }
            }
        }

        val builder = StringBuilder()
        builder.append("Batch label modification complete.\n")
        builder.append("Successfully processed: ${successes.size} messages\n")
        if (failures.isNotEmpty()) {
            builder.append("Failed to process: ${failures.size} messages\n\n")
            builder.append("Failed message IDs:\n")
            builder.append(failures.joinToString("\n") { "- ${it.first.take(16)}... (${it.second})" })
        }
        return builder.toString()
    }

    fun batchDeleteEmails(args: BatchDeleteEmailsArgs): String {
        val successes = mutableListOf<String>()
        val failures = mutableListOf<Pair<String, String>>()

        processBatches(args.messageIds, args.batchSize) { batch ->
            batch.forEach { id ->
                try {
                    gmail.users().messages().delete("me", id).execute()
                    successes.add(id)
                } catch (e: Exception) {
                    failures.add(id to (e.message ?: "Unknown error"))
                }
            }
        }

        val builder = StringBuilder()
        builder.append("Batch delete operation complete.\n")
        builder.append("Successfully deleted: ${successes.size} messages\n")
        if (failures.isNotEmpty()) {
            builder.append("Failed to delete: ${failures.size} messages\n\n")
            builder.append("Failed message IDs:\n")
            builder.append(failures.joinToString("\n") { "- ${it.first.take(16)}... (${it.second})" })
        }
        return builder.toString()
    }

    fun createLabel(args: CreateLabelArgs): Label {
        val label = Label().apply {
            name = args.name
            messageListVisibility = args.messageListVisibility ?: "show"
            labelListVisibility = args.labelListVisibility ?: "labelShow"
        }
        return gmail.users().labels().create("me", label).execute()
    }

    fun updateLabel(args: UpdateLabelArgs): Label {
        val updates = Label().apply {
            if (args.name != null) name = args.name
            if (args.messageListVisibility != null) messageListVisibility = args.messageListVisibility
            if (args.labelListVisibility != null) labelListVisibility = args.labelListVisibility
        }
        return gmail.users().labels().update("me", args.id, updates).execute()
    }

    fun deleteLabel(id: String): String {
        val label = gmail.users().labels().get("me", id).execute()
        if (label.type == "system") {
            error("Cannot delete system label with ID '$id'.")
        }
        gmail.users().labels().delete("me", id).execute()
        return "Label '${label.name}' deleted successfully."
    }

    fun getOrCreateLabel(args: GetOrCreateLabelArgs): Label {
        val labels = gmail.users().labels().list("me").execute().labels ?: emptyList()
        val existing = labels.firstOrNull { it.name.equals(args.name, ignoreCase = true) }
        if (existing != null) return existing

        return createLabel(CreateLabelArgs(args.name, args.messageListVisibility, args.labelListVisibility))
    }

    fun createFilter(args: CreateFilterArgs): Filter {
        val filter = Filter().apply {
            criteria = com.google.api.services.gmail.model.FilterCriteria().apply {
                from = args.criteria.from
                to = args.criteria.to
                subject = args.criteria.subject
                query = args.criteria.query
                negatedQuery = args.criteria.negatedQuery
                hasAttachment = args.criteria.hasAttachment
                excludeChats = args.criteria.excludeChats
                args.criteria.size?.let { setSize(it.toInt()) }
                sizeComparison = args.criteria.sizeComparison
            }
            action = com.google.api.services.gmail.model.FilterAction().apply {
                addLabelIds = args.action.addLabelIds
                removeLabelIds = args.action.removeLabelIds
                forward = args.action.forward
            }
        }
        return gmail.users().settings().filters().create("me", filter).execute()
    }

    fun listFilters(): List<Filter> {
        val response = gmail.users().settings().filters().list("me").execute()
        return response.filter ?: emptyList()
    }

    fun getFilter(filterId: String): Filter {
        return gmail.users().settings().filters().get("me", filterId).execute()
    }

    fun deleteFilter(filterId: String): String {
        gmail.users().settings().filters().delete("me", filterId).execute()
        return "Filter '$filterId' deleted successfully."
    }

    fun createFilterFromTemplate(args: CreateFilterFromTemplateArgs): Filter {
        val template = args.template
        val p = args.parameters

        val (criteria, action) = when (template) {
            "fromSender" -> {
                val sender = p.senderEmail ?: error("senderEmail is required")
                GmailFilterCriteria(from = sender) to GmailFilterAction(
                    addLabelIds = p.labelIds,
                    removeLabelIds = if (p.archive == true) listOf("INBOX") else null
                )
            }
            "withSubject" -> {
                val subject = p.subjectText ?: error("subjectText is required")
                GmailFilterCriteria(subject = subject) to GmailFilterAction(
                    addLabelIds = p.labelIds,
                    removeLabelIds = if (p.markAsRead == true) listOf("UNREAD") else null
                )
            }
            "withAttachments" -> {
                GmailFilterCriteria(hasAttachment = true) to GmailFilterAction(addLabelIds = p.labelIds)
            }
            "largeEmails" -> {
                val size = p.sizeInBytes ?: error("sizeInBytes is required")
                GmailFilterCriteria(size = size, sizeComparison = "larger") to GmailFilterAction(addLabelIds = p.labelIds)
            }
            "containingText" -> {
                val search = p.searchText ?: error("searchText is required")
                GmailFilterCriteria(query = "\"$search\"") to GmailFilterAction(
                    addLabelIds = if (p.markImportant == true) (p.labelIds ?: emptyList()) + listOf("IMPORTANT") else p.labelIds
                )
            }
            "mailingList" -> {
                val listId = p.listIdentifier ?: error("listIdentifier is required")
                GmailFilterCriteria(query = "list:$listId OR subject:[$listId]") to GmailFilterAction(
                    addLabelIds = p.labelIds,
                    removeLabelIds = if (p.archive == true) listOf("INBOX") else null
                )
            }
            else -> error("Unknown template: $template")
        }

        return createFilter(CreateFilterArgs(criteria, action))
    }

    fun downloadAttachment(args: DownloadAttachmentArgs): String {
        val attachment = gmail.users().messages().attachments().get("me", args.messageId, args.attachmentId).execute()
        val data = attachment.data ?: error("No attachment data received")
        val buffer = Base64.getUrlDecoder().decode(data)

        val saveDir = if (args.savePath != null) Path.of(args.savePath) else Path.of(System.getProperty("user.dir"))
        if (!Files.exists(saveDir)) Files.createDirectories(saveDir)

        val filename = args.filename ?: findAttachmentFilename(args.messageId, args.attachmentId)
        val fullPath = saveDir.resolve(filename).normalize()

        // Prevent path traversal attacks
        require(fullPath.startsWith(saveDir.normalize())) {
            "Invalid filename: path traversal detected"
        }

        Files.write(fullPath, buffer)

        return "Attachment downloaded successfully:\nFile: $filename\nSize: ${buffer.size} bytes\nSaved to: $fullPath"
    }

    private fun findAttachmentFilename(messageId: String, attachmentId: String): String {
        val message = gmail.users().messages().get("me", messageId).setFormat("full").execute()
        val found = findAttachmentFilename(message.payload, attachmentId)
        return found ?: "attachment-$attachmentId"
    }

    private fun findAttachmentFilename(part: MessagePart?, attachmentId: String): String? {
        if (part == null) return null
        if (part.body?.attachmentId == attachmentId) return part.filename
        part.parts?.forEach { sub ->
            val found = findAttachmentFilename(sub, attachmentId)
            if (found != null) return found
        }
        return null
    }

    private data class EmailContent(val text: String, val html: String)

    private fun extractEmailContent(part: MessagePart?): EmailContent {
        if (part == null) return EmailContent("", "")

        var textContent = ""
        var htmlContent = ""

        if (part.body?.data != null) {
            val decoded = String(Base64.getUrlDecoder().decode(part.body.data), Charsets.UTF_8)
            when (part.mimeType) {
                "text/plain" -> textContent += decoded
                "text/html" -> htmlContent += decoded
            }
        }

        part.parts?.forEach { sub ->
            val child = extractEmailContent(sub)
            if (child.text.isNotBlank()) textContent += child.text
            if (child.html.isNotBlank()) htmlContent += child.html
        }

        return EmailContent(textContent, htmlContent)
    }

    private fun collectAttachments(part: MessagePart, attachments: MutableList<AttachmentInfo>) {
        if (part.body?.attachmentId != null) {
            val filename = part.filename ?: "attachment-${part.body.attachmentId}"
            attachments.add(
                AttachmentInfo(
                    id = part.body.attachmentId,
                    filename = filename,
                    mimeType = part.mimeType ?: "application/octet-stream",
                    size = part.body.size ?: 0
                )
            )
        }

        part.parts?.forEach { collectAttachments(it, attachments) }
    }

    private fun List<MessagePartHeader>.findHeader(name: String): String {
        return this.firstOrNull { it.name.equals(name, ignoreCase = true) }?.value ?: ""
    }

    private fun <T> processBatches(items: List<T>, batchSize: Int, process: (List<T>) -> Unit) {
        var i = 0
        while (i < items.size) {
            val batch = items.subList(i, kotlin.math.min(i + batchSize, items.size))
            process(batch)
            i += batchSize
        }
    }
}

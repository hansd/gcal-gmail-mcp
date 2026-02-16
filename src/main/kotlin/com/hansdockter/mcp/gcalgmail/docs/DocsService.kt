package com.hansdockter.mcp.gcalgmail.docs

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.Event
import com.google.api.services.docs.v1.Docs
import com.google.api.services.docs.v1.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class DocsService(private val credential: Credential) {
    private val transport = NetHttpTransport()
    private val jsonFactory = GsonFactory.getDefaultInstance()

    private val docs: Docs = Docs.Builder(transport, jsonFactory, credential)
        .setApplicationName("gcal-gmail-mcp")
        .build()

    private val calendar: Calendar = Calendar.Builder(transport, jsonFactory, credential)
        .setApplicationName("gcal-gmail-mcp")
        .build()

    fun getMeetingNotes(args: GetMeetingNotesArgs): String {
        val event = calendar.events().get(args.calendarId, args.eventId).execute()
        val attachments = event.attachments ?: emptyList()

        val googleDocs = attachments.filter {
            it.mimeType == "application/vnd.google-apps.document"
        }

        return when {
            googleDocs.isEmpty() -> "No Google Docs attached to event '${event.summary}'."
            googleDocs.size == 1 -> {
                val doc = googleDocs.first()
                buildString {
                    appendLine("Meeting notes found for '${event.summary}':")
                    appendLine("Title: ${doc.title ?: "Untitled"}")
                    appendLine("URL: ${doc.fileUrl}")
                    appendLine("Document ID: ${extractDocId(doc.fileUrl)}")
                }
            }
            else -> {
                buildString {
                    appendLine("Multiple Google Docs attached to '${event.summary}':")
                    appendLine("Please specify which document to use:\n")
                    googleDocs.forEachIndexed { index, doc ->
                        appendLine("${index + 1}. ${doc.title ?: "Untitled"}")
                        appendLine("   URL: ${doc.fileUrl}")
                        appendLine("   Document ID: ${extractDocId(doc.fileUrl)}")
                        appendLine()
                    }
                }
            }
        }
    }

    /**
     * Extract text content from a paragraph element, including richLink, dateElement, person chips,
     * and other non-textRun elements.
     *
     * Google Calendar meeting notes use dateElement chips for date headings. These are not typed
     * in the Java client library, so we extract them from the raw JSON via unknownKeys.
     */
    @Suppress("UNCHECKED_CAST")
    private fun extractParagraphElementText(element: ParagraphElement): String? {
        // Standard text runs
        element.textRun?.content?.let { return it }

        // Date element chips (Google Calendar meeting notes date-picker headings)
        // The Java client library doesn't have a typed field for dateElement, but GenericJson
        // stores it in the underlying map. Access via get() which inherits from AbstractMap.
        // Structure: { "dateElement": { "dateElementProperties": { "displayText": "Feb 23, 2026", ... } } }
        val dateElement = element["dateElement"] as? Map<String, Any?>
        if (dateElement != null) {
            val props = dateElement["dateElementProperties"] as? Map<String, Any?>
            val displayText = props?.get("displayText") as? String
            if (displayText != null) return displayText
        }

        // Rich links (calendar event chips, smart chips, etc.)
        element.richLink?.let { richLink ->
            val title = richLink.richLinkProperties?.title
            if (title != null) return title
            val uri = richLink.richLinkProperties?.uri
            if (uri != null) return uri
        }

        // Person chips
        element.person?.let { person ->
            val name = person.personProperties?.name
            val email = person.personProperties?.email
            return name ?: email
        }

        return null
    }

    /**
     * Extract the full text of a paragraph by combining all element types.
     */
    private fun extractParagraphText(paragraph: Paragraph): String {
        return paragraph.elements?.mapNotNull { extractParagraphElementText(it) }?.joinToString("") ?: ""
    }

    fun readDocContent(args: ReadDocContentArgs): String {
        val document = docs.documents().get(args.fileId).execute()
        val title = document.title ?: "Untitled"
        val content = document.body?.content ?: emptyList()

        val text = buildString {
            for (element in content) {
                val paragraph = element.paragraph ?: continue
                val paragraphText = extractParagraphText(paragraph)
                append(paragraphText)
            }
        }

        return buildString {
            appendLine("Document: $title")
            appendLine("Document ID: ${args.fileId}")
            appendLine("URL: https://docs.google.com/document/d/${args.fileId}/edit")
            appendLine()
            append(text)
        }
    }

    fun getAgendaItems(args: GetAgendaItemsArgs): String {
        val event = calendar.events().get(args.calendarId, args.eventId).execute()
        val attachments = event.attachments ?: emptyList()

        val googleDocs = attachments.filter {
            it.mimeType == "application/vnd.google-apps.document"
        }

        if (googleDocs.isEmpty()) {
            return "No Google Docs attached to event '${event.summary}'."
        }

        if (googleDocs.size > 1) {
            return buildString {
                appendLine("Multiple Google Docs attached to '${event.summary}'.")
                appendLine("Please use get_meeting_notes first to identify the correct document.")
            }
        }

        val docUrl = googleDocs.first().fileUrl
        val docId = extractDocId(docUrl)
            ?: return "Could not extract document ID from URL: $docUrl"

        val document = docs.documents().get(docId).execute()
        val content = document.body?.content ?: emptyList()

        val eventDate = extractEventDate(event)
        val dateString = formatDateForHeading(eventDate)
        val dateHeading = findDateHeadingIndex(content, eventDate)

        if (dateHeading == null) {
            return "No agenda section found for $dateString in '${event.summary}'."
        }

        val nextSection = findNextSectionHeading(content, dateHeading.endIndex)
        val sectionEnd = nextSection?.startIndex ?: Int.MAX_VALUE
        val notesIndex = findNotesHeadingAfterIndex(content, dateHeading.endIndex, sectionEnd)

        if (notesIndex == null) {
            return "No Notes section found for $dateString in '${event.summary}'."
        }

        // Find the boundary after Notes — either "Action items" text or the next HEADING_2
        val notesEnd = findSectionEndAfterNotes(content, notesIndex.endIndex, sectionEnd)

        // Extract bullet items between Notes heading and the boundary
        val items = mutableListOf<String>()
        for (element in content) {
            val startIdx = element.startIndex ?: continue
            if (startIdx < notesIndex.endIndex) continue
            if (startIdx >= notesEnd) break

            val paragraph = element.paragraph ?: continue
            val text = extractParagraphText(paragraph).trim()
            if (text.isNotEmpty()) {
                items.add(text)
            }
        }

        return buildString {
            appendLine("Agenda items for '${event.summary}' ($dateString):")
            appendLine("Document: ${googleDocs.first().title ?: "Untitled"}")
            appendLine("URL: $docUrl")
            appendLine()
            if (items.isEmpty()) {
                appendLine("No agenda items found.")
            } else {
                items.forEachIndexed { index, item ->
                    appendLine("${index + 1}. $item")
                }
            }
        }
    }

    fun addAgendaItem(args: AddAgendaItemArgs): String {
        val event = calendar.events().get(args.calendarId, args.eventId).execute()
        val attachments = event.attachments ?: emptyList()

        val googleDocs = attachments.filter {
            it.mimeType == "application/vnd.google-apps.document"
        }

        if (googleDocs.isEmpty()) {
            return "No Google Docs attached to event '${event.summary}'. Cannot add agenda item."
        }

        if (googleDocs.size > 1) {
            return buildString {
                appendLine("Multiple Google Docs attached to '${event.summary}'.")
                appendLine("Please use get_meeting_notes first to identify the correct document,")
                appendLine("then manually add the agenda item to the appropriate doc.")
            }
        }

        val docUrl = googleDocs.first().fileUrl
        val docId = extractDocId(docUrl)
            ?: return "Could not extract document ID from URL: $docUrl"

        val document = docs.documents().get(docId).execute()
        val content = document.body?.content ?: emptyList()

        // Extract event date for date-aware section handling
        val eventDate = extractEventDate(event)
        val dateString = formatDateForHeading(eventDate)

        val requests = mutableListOf<Request>()

        // Find the date section for this event
        val dateHeading = findDateHeadingIndex(content, eventDate)

        if (dateHeading != null) {
            // Date section exists, find Notes under it (agenda items go under Notes)
            // Bound the search to the current date section by finding the next HEADING_2
            val nextSection = findNextSectionHeading(content, dateHeading.endIndex)
            val sectionEnd = nextSection?.startIndex ?: Int.MAX_VALUE
            val notesIndex = findNotesHeadingAfterIndex(content, dateHeading.endIndex, sectionEnd)

            if (notesIndex != null) {
                // Insert bullet item after the Notes heading
                val insertIndex = notesIndex.endIndex
                requests.add(Request().setInsertText(
                    InsertTextRequest()
                        .setText("${args.item}\n")
                        .setLocation(Location().setIndex(insertIndex))
                ))
                requests.add(Request().setCreateParagraphBullets(
                    CreateParagraphBulletsRequest()
                        .setRange(Range()
                            .setStartIndex(insertIndex)
                            .setEndIndex(insertIndex + args.item.length + 1))
                        .setBulletPreset("BULLET_DISC_CIRCLE_SQUARE")
                ))
            } else {
                // Create Notes heading after date section, then insert item
                val insertIndex = dateHeading.endIndex
                val notesText = "Notes\n"
                val itemText = "${args.item}\n"

                requests.add(Request().setInsertText(
                    InsertTextRequest()
                        .setText(notesText)
                        .setLocation(Location().setIndex(insertIndex))
                ))
                val itemIndex = insertIndex + notesText.length
                requests.add(Request().setInsertText(
                    InsertTextRequest()
                        .setText(itemText)
                        .setLocation(Location().setIndex(itemIndex))
                ))
                requests.add(Request().setCreateParagraphBullets(
                    CreateParagraphBulletsRequest()
                        .setRange(Range()
                            .setStartIndex(itemIndex)
                            .setEndIndex(itemIndex + itemText.length))
                        .setBulletPreset("BULLET_DISC_CIRCLE_SQUARE")
                ))
            }
        } else {
            // Date section doesn't exist, create it at start of document
            // Follow Google Calendar meeting notes pattern:
            // [Date chip] (HEADING_2)
            // Attendees:
            //
            // Notes
            // • [item]
            //
            // Action items
            //

            // We use a two-phase approach:
            // Phase 1: Insert a newline at index 1, set it to HEADING_2, then insert the date chip into it.
            //          The insertDate request inserts a date-picker chip (smart chip) matching what
            //          Google Calendar creates natively in meeting notes.
            // Phase 2: After phase 1 executes, re-read the doc to get accurate indices, then insert
            //          the remaining content (Attendees, Notes, item, Action items).

            // Phase 1: Create the date heading with a date-picker chip
            val phase1Requests = mutableListOf<Request>()

            // Insert a newline to create the heading paragraph
            phase1Requests.add(Request().setInsertText(
                InsertTextRequest()
                    .setText("\n")
                    .setLocation(Location().setIndex(1))
            ))
            // Style it as HEADING_2
            phase1Requests.add(Request().setUpdateParagraphStyle(
                UpdateParagraphStyleRequest()
                    .setRange(Range()
                        .setStartIndex(1)
                        .setEndIndex(2))
                    .setParagraphStyle(ParagraphStyle().setNamedStyleType("HEADING_2"))
                    .setFields("namedStyleType")
            ))
            // Insert the date chip at position 1 (inside the heading paragraph)
            // Timestamp format: RFC 3339 at noon UTC, matching Google Calendar's convention
            val timestamp = eventDate.atStartOfDay(ZoneId.of("UTC"))
                .plusHours(12)
                .toInstant()
                .toString()
            phase1Requests.add(Request().setInsertDate(
                InsertDateRequest()
                    .setLocation(Location().setIndex(1))
                    .setDateElementProperties(
                        DateElementProperties()
                            .setTimestamp(timestamp)
                            .setDateFormat("DATE_FORMAT_MONTH_DAY_YEAR_ABBREVIATED")
                            .setTimeFormat("TIME_FORMAT_DISABLED")
                            .setLocale("en")
                    )
            ))

            try {
                docs.documents().batchUpdate(docId, BatchUpdateDocumentRequest().setRequests(phase1Requests)).execute()
            } catch (e: Exception) {
                return "Error creating date heading: ${e.message}"
            }

            // Phase 2: Re-read doc to get accurate indices after date chip insertion
            val updatedDocument = docs.documents().get(docId).execute()
            val updatedContent = updatedDocument.body?.content ?: emptyList()

            // Find the heading we just created (should be the first HEADING_2)
            val newHeading = findDateHeadingIndex(updatedContent, eventDate)
                ?: return "Error: could not find the date heading just created."

            val attendeesText = "Attendees:\n\n"
            val notesText = "Notes\n"
            val itemText = "${args.item}\n"
            val actionItemsText = "\nAction items\n\n"

            // Insert Attendees section (normal text)
            var currentIndex = newHeading.endIndex
            requests.add(Request().setInsertText(
                InsertTextRequest()
                    .setText(attendeesText)
                    .setLocation(Location().setIndex(currentIndex))
            ))
            requests.add(Request().setUpdateParagraphStyle(
                UpdateParagraphStyleRequest()
                    .setRange(Range()
                        .setStartIndex(currentIndex)
                        .setEndIndex(currentIndex + attendeesText.length))
                    .setParagraphStyle(ParagraphStyle().setNamedStyleType("NORMAL_TEXT"))
                    .setFields("namedStyleType")
            ))

            // Insert Notes section (normal text)
            currentIndex += attendeesText.length
            requests.add(Request().setInsertText(
                InsertTextRequest()
                    .setText(notesText)
                    .setLocation(Location().setIndex(currentIndex))
            ))
            requests.add(Request().setUpdateParagraphStyle(
                UpdateParagraphStyleRequest()
                    .setRange(Range()
                        .setStartIndex(currentIndex)
                        .setEndIndex(currentIndex + notesText.length))
                    .setParagraphStyle(ParagraphStyle().setNamedStyleType("NORMAL_TEXT"))
                    .setFields("namedStyleType")
            ))

            // Insert the agenda item with bullet under Notes
            currentIndex += notesText.length
            requests.add(Request().setInsertText(
                InsertTextRequest()
                    .setText(itemText)
                    .setLocation(Location().setIndex(currentIndex))
            ))
            requests.add(Request().setCreateParagraphBullets(
                CreateParagraphBulletsRequest()
                    .setRange(Range()
                        .setStartIndex(currentIndex)
                        .setEndIndex(currentIndex + itemText.length))
                    .setBulletPreset("BULLET_DISC_CIRCLE_SQUARE")
            ))
            requests.add(Request().setUpdateParagraphStyle(
                UpdateParagraphStyleRequest()
                    .setRange(Range()
                        .setStartIndex(currentIndex)
                        .setEndIndex(currentIndex + itemText.length))
                    .setParagraphStyle(ParagraphStyle().setNamedStyleType("NORMAL_TEXT"))
                    .setFields("namedStyleType")
            ))

            // Insert Action items section (normal text)
            currentIndex += itemText.length
            requests.add(Request().setInsertText(
                InsertTextRequest()
                    .setText(actionItemsText)
                    .setLocation(Location().setIndex(currentIndex))
            ))
            requests.add(Request().setUpdateParagraphStyle(
                UpdateParagraphStyleRequest()
                    .setRange(Range()
                        .setStartIndex(currentIndex)
                        .setEndIndex(currentIndex + actionItemsText.length))
                    .setParagraphStyle(ParagraphStyle().setNamedStyleType("NORMAL_TEXT"))
                    .setFields("namedStyleType")
            ))
        }

        try {
            docs.documents().batchUpdate(docId, BatchUpdateDocumentRequest().setRequests(requests)).execute()
        } catch (e: Exception) {
            return "Error adding agenda item: ${e.message}"
        }

        return buildString {
            appendLine("Agenda item added successfully!")
            appendLine("Document: ${googleDocs.first().title ?: "Untitled"}")
            appendLine("Date section: $dateString")
            appendLine("Item: ${args.item}")
            appendLine("URL: $docUrl")
        }
    }

    fun createDoc(args: CreateDocArgs): String {
        val doc = Document().setTitle(args.title)
        val createdDoc = docs.documents().create(doc).execute()
        val docId = createdDoc.documentId

        val converter = MarkdownToDocsConverter()
        val requests = converter.convert(args.content, startIndex = 1)

        if (requests.isNotEmpty()) {
            docs.documents().batchUpdate(docId, BatchUpdateDocumentRequest().setRequests(requests)).execute()
        }

        val docUrl = "https://docs.google.com/document/d/$docId/edit"
        return buildString {
            appendLine("Document created successfully!")
            appendLine("Title: ${args.title}")
            appendLine("URL: $docUrl")
            appendLine("Document ID: $docId")
        }
    }

    fun updateDoc(args: UpdateDocArgs): String {
        val document = docs.documents().get(args.fileId).execute()
        val body = document.body
        val endIndex = body.content.lastOrNull()?.endIndex ?: 1
        val insertAt = endIndex - 1

        val converter = MarkdownToDocsConverter()
        val requests = converter.convert(args.content, startIndex = insertAt)

        if (requests.isNotEmpty()) {
            docs.documents().batchUpdate(args.fileId, BatchUpdateDocumentRequest().setRequests(requests)).execute()
        }

        val docUrl = "https://docs.google.com/document/d/${args.fileId}/edit"
        return buildString {
            appendLine("Document updated successfully!")
            appendLine("Title: ${document.title}")
            appendLine("URL: $docUrl")
            appendLine("Document ID: ${args.fileId}")
        }
    }

    fun replaceDocText(args: ReplaceDocTextArgs): String {
        val document = docs.documents().get(args.fileId).execute()
        val title = document.title ?: "Untitled"
        val content = document.body?.content ?: emptyList()

        // Build a lookup from find -> replace, respecting case sensitivity
        val replacementMap = args.replacements.associateBy(
            { if (it.matchCase) it.find else it.find.lowercase() },
            { it }
        )

        // Find paragraphs whose full text matches a find string (exact paragraph match)
        // Collect matches with their indices, processing in reverse order to preserve indices
        data class ParagraphMatch(val startIndex: Int, val endIndex: Int, val replacement: TextReplacement)
        val matches = mutableListOf<ParagraphMatch>()

        for (element in content) {
            val paragraph = element.paragraph ?: continue
            val startIdx = element.startIndex ?: continue
            val endIdx = element.endIndex ?: continue
            val text = extractParagraphText(paragraph).trimEnd('\n')
            if (text.isBlank()) continue

            val lookupKey = replacementMap.keys.find { key ->
                val matchText = if (replacementMap[key]?.matchCase != false) text else text.lowercase()
                matchText == key
            }
            if (lookupKey != null) {
                matches.add(ParagraphMatch(startIdx, endIdx, replacementMap[lookupKey]!!))
            }
        }

        if (matches.isEmpty()) {
            // Debug: show first 30 paragraph texts to help diagnose matching issues
            val debugParagraphs = mutableListOf<String>()
            for (element in content) {
                val paragraph = element.paragraph ?: continue
                val text = extractParagraphText(paragraph).trimEnd('\n')
                if (text.isBlank()) continue
                val style = paragraph.paragraphStyle?.namedStyleType ?: "unknown"
                debugParagraphs.add("[$style] \"$text\"")
                if (debugParagraphs.size >= 30) break
            }
            return buildString {
                appendLine("No matching paragraphs found.")
                appendLine("Document: $title")
                appendLine("URL: https://docs.google.com/document/d/${args.fileId}/edit")
                appendLine()
                appendLine("First ${debugParagraphs.size} paragraphs in doc:")
                debugParagraphs.forEach { appendLine("  $it") }
                appendLine()
                appendLine("First 5 find strings: ${args.replacements.take(5).map { "\"${it.find}\"" }}")
            }
        }

        // Sort in reverse order so that index-based operations don't shift earlier positions
        matches.sortByDescending { it.startIndex }

        // Build requests: for each match, delete the text content (preserving the paragraph)
        // then insert the replacement text
        val requests = mutableListOf<Request>()
        for (match in matches) {
            // Delete content within the paragraph (exclude the trailing newline which is the paragraph break)
            val deleteEnd = match.endIndex - 1  // preserve the paragraph-ending newline
            if (deleteEnd > match.startIndex) {
                requests.add(Request().setDeleteContentRange(
                    DeleteContentRangeRequest().setRange(
                        Range()
                            .setStartIndex(match.startIndex)
                            .setEndIndex(deleteEnd)
                    )
                ))
            }
            // Insert the replacement text at the paragraph start
            requests.add(Request().setInsertText(
                InsertTextRequest()
                    .setText(match.replacement.replace)
                    .setLocation(Location().setIndex(match.startIndex))
            ))
        }

        docs.documents().batchUpdate(
            args.fileId,
            BatchUpdateDocumentRequest().setRequests(requests)
        ).execute()

        // Count replacements per find string
        val countByFind = matches.groupBy { it.replacement.find }.mapValues { it.value.size }

        return buildString {
            appendLine("Text replaced successfully!")
            appendLine("Document: $title")
            appendLine("URL: https://docs.google.com/document/d/${args.fileId}/edit")
            appendLine("Total replacements made: ${matches.size}")
            appendLine()
            args.replacements.forEach { replacement ->
                val count = countByFind[replacement.find] ?: 0
                appendLine("  \"${replacement.find}\" -> \"${replacement.replace}\": $count occurrences")
            }
        }
    }

    fun createEmailReviewDoc(args: CreateEmailReviewDocArgs): String {
        // Create the document
        val doc = Document().setTitle("Email Draft: ${args.subject}")
        val createdDoc = docs.documents().create(doc).execute()
        val docId = createdDoc.documentId

        // Build the email content
        val contentBuilder = StringBuilder()

        if (args.to != null && args.to.isNotEmpty()) {
            contentBuilder.appendLine("To: ${args.to.joinToString(", ")}")
        }
        if (args.cc != null && args.cc.isNotEmpty()) {
            contentBuilder.appendLine("Cc: ${args.cc.joinToString(", ")}")
        }
        if (args.bcc != null && args.bcc.isNotEmpty()) {
            contentBuilder.appendLine("Bcc: ${args.bcc.joinToString(", ")}")
        }
        contentBuilder.appendLine("Subject: ${args.subject}")
        contentBuilder.appendLine()
        contentBuilder.appendLine("---")
        contentBuilder.appendLine()
        contentBuilder.append(args.body)

        val content = contentBuilder.toString()

        val requests = mutableListOf<Request>()

        // Insert the content
        requests.add(Request().setInsertText(
            InsertTextRequest()
                .setText(content)
                .setLocation(Location().setIndex(1))
        ))

        // Bold the labels (To:, Cc:, Bcc:, Subject:)
        val labels = listOf("To:", "Cc:", "Bcc:", "Subject:")
        for (label in labels) {
            val labelIndex = content.indexOf(label)
            if (labelIndex >= 0) {
                val startIdx = 1 + labelIndex
                val endIdx = startIdx + label.length
                requests.add(Request().setUpdateTextStyle(
                    UpdateTextStyleRequest()
                        .setRange(Range()
                            .setStartIndex(startIdx)
                            .setEndIndex(endIdx))
                        .setTextStyle(TextStyle().setBold(true))
                        .setFields("bold")
                ))
            }
        }

        docs.documents().batchUpdate(docId, BatchUpdateDocumentRequest().setRequests(requests)).execute()

        val docUrl = "https://docs.google.com/document/d/$docId/edit"

        return buildString {
            appendLine("Email review document created successfully!")
            appendLine("Title: Email Draft: ${args.subject}")
            appendLine("URL: $docUrl")
            appendLine("Document ID: $docId")
        }
    }

    private fun extractDocId(fileUrl: String): String? {
        // URLs are typically: https://docs.google.com/document/d/{docId}/...
        // or https://drive.google.com/open?id={docId}
        val docIdPattern = Regex("/document/d/([a-zA-Z0-9_-]+)")
        val driveIdPattern = Regex("[?&]id=([a-zA-Z0-9_-]+)")

        return docIdPattern.find(fileUrl)?.groupValues?.get(1)
            ?: driveIdPattern.find(fileUrl)?.groupValues?.get(1)
    }

    private data class HeadingLocation(val startIndex: Int, val endIndex: Int)

    private fun extractEventDate(event: Event): LocalDate {
        val start = event.start
        return when {
            start.dateTime != null -> {
                val instant = Instant.ofEpochMilli(start.dateTime.value)
                val zoneId = if (start.timeZone != null) ZoneId.of(start.timeZone) else ZoneId.systemDefault()
                instant.atZone(zoneId).toLocalDate()
            }
            start.date != null -> {
                // All-day event - date is in format "yyyy-MM-dd"
                LocalDate.parse(start.date.toStringRfc3339().substringBefore("T"))
            }
            else -> error("Event has no start date")
        }
    }

    private fun getDateSearchPatterns(date: LocalDate): List<String> {
        return listOf(
            date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)),  // February 4, 2025
            date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH)),  // Tuesday, February 4, 2025
            date.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)),  // Feb 4, 2025
            date.format(DateTimeFormatter.ofPattern("M/d/yyyy", Locale.ENGLISH)),  // 2/4/2025
            date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)),  // 4 February 2025
        )
    }

    private fun formatDateForHeading(date: LocalDate): String {
        return date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH))
    }

    private fun findDateHeadingIndex(content: List<StructuralElement>, date: LocalDate): HeadingLocation? {
        val patterns = getDateSearchPatterns(date)

        for (element in content) {
            val paragraph = element.paragraph ?: continue
            val text = extractParagraphText(paragraph)

            for (pattern in patterns) {
                if (text.contains(pattern, ignoreCase = true)) {
                    val startIdx = element.startIndex ?: continue
                    val endIdx = element.endIndex ?: continue
                    return HeadingLocation(startIdx, endIdx)
                }
            }
        }
        return null
    }

    private fun findNextSectionHeading(content: List<StructuralElement>, afterIndex: Int): HeadingLocation? {
        for (element in content) {
            val startIdx = element.startIndex ?: continue
            if (startIdx < afterIndex) continue

            val paragraph = element.paragraph ?: continue
            val style = paragraph.paragraphStyle?.namedStyleType ?: continue
            if (style == "HEADING_2") {
                val endIdx = element.endIndex ?: continue
                return HeadingLocation(startIdx, endIdx)
            }
        }
        return null
    }

    private fun findSectionEndAfterNotes(content: List<StructuralElement>, afterIndex: Int, sectionEnd: Int): Int {
        for (element in content) {
            val startIdx = element.startIndex ?: continue
            if (startIdx < afterIndex) continue
            if (startIdx >= sectionEnd) break

            val paragraph = element.paragraph ?: continue
            val text = extractParagraphText(paragraph).trim()
            if (text.equals("Action items", ignoreCase = true)) {
                return startIdx
            }
        }
        return sectionEnd
    }

    private fun findNotesHeadingAfterIndex(content: List<StructuralElement>, afterIndex: Int, beforeIndex: Int = Int.MAX_VALUE): HeadingLocation? {
        for (element in content) {
            val startIdx = element.startIndex ?: continue
            if (startIdx < afterIndex) continue  // Skip elements before our date section
            if (startIdx >= beforeIndex) break    // Stop at next date section boundary

            val paragraph = element.paragraph ?: continue
            val text = extractParagraphText(paragraph)

            if (text.trim().equals("Notes", ignoreCase = true)) {
                val endIdx = element.endIndex ?: continue
                return HeadingLocation(startIdx, endIdx)
            }
        }
        return null
    }

}

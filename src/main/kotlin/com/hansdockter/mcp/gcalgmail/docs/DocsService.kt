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
            // [Date] (HEADING_2)
            // Attendees:
            //
            // Notes
            // • [item]
            //
            // Action items
            //

            val insertIndex = 1
            val dateText = "$dateString\n"
            val attendeesText = "Attendees:\n\n"
            val notesText = "Notes\n"
            val itemText = "${args.item}\n"
            val actionItemsText = "\nAction items\n\n"

            // Insert date heading (HEADING_2)
            requests.add(Request().setInsertText(
                InsertTextRequest()
                    .setText(dateText)
                    .setLocation(Location().setIndex(insertIndex))
            ))
            requests.add(Request().setUpdateParagraphStyle(
                UpdateParagraphStyleRequest()
                    .setRange(Range()
                        .setStartIndex(insertIndex)
                        .setEndIndex(insertIndex + dateText.length))
                    .setParagraphStyle(ParagraphStyle().setNamedStyleType("HEADING_2"))
                    .setFields("namedStyleType")
            ))

            // Insert Attendees section (normal text)
            var currentIndex = insertIndex + dateText.length
            requests.add(Request().setInsertText(
                InsertTextRequest()
                    .setText(attendeesText)
                    .setLocation(Location().setIndex(currentIndex))
            ))
            // Reset to normal text style for Attendees
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
            // Reset to normal text style for Notes
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
            // Reset to normal text style for agenda item
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
            // Reset to normal text style for Action items
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
            val text = paragraph.elements?.mapNotNull { it.textRun?.content }?.joinToString("") ?: ""

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

    private fun findNotesHeadingAfterIndex(content: List<StructuralElement>, afterIndex: Int, beforeIndex: Int = Int.MAX_VALUE): HeadingLocation? {
        for (element in content) {
            val startIdx = element.startIndex ?: continue
            if (startIdx < afterIndex) continue  // Skip elements before our date section
            if (startIdx >= beforeIndex) break    // Stop at next date section boundary

            val paragraph = element.paragraph ?: continue
            val text = paragraph.elements?.mapNotNull { it.textRun?.content }?.joinToString("") ?: ""

            if (text.trim().equals("Notes", ignoreCase = true)) {
                val endIdx = element.endIndex ?: continue
                return HeadingLocation(startIdx, endIdx)
            }
        }
        return null
    }

}

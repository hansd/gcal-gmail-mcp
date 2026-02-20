package com.hansdockter.mcp.gcalgmail.meet

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.meet.v2.Meet

class MeetService(private val credential: Credential) {
    private val transport = NetHttpTransport()
    private val jsonFactory = GsonFactory.getDefaultInstance()

    private val meet: Meet = Meet.Builder(transport, jsonFactory, credential)
        .setApplicationName("gcal-gmail-mcp")
        .build()

    fun listConferenceRecords(args: ListConferenceRecordsArgs): String {
        val request = meet.conferenceRecords().list()
            .setPageSize(args.pageSize)

        if (args.filter != null) request.setFilter(args.filter)
        if (args.pageToken != null) request.setPageToken(args.pageToken)

        val response = request.execute()
        val records = response.conferenceRecords ?: emptyList()

        if (records.isEmpty()) {
            return "No conference records found."
        }

        val entries = records.joinToString("\n\n") { record ->
            buildString {
                appendLine("Name: ${record.name}")
                appendLine("Space: ${record.space}")
                if (record.startTime != null) appendLine("Start: ${record.startTime}")
                if (record.endTime != null) appendLine("End: ${record.endTime}")
                if (record.expireTime != null) append("Expires: ${record.expireTime}")
            }.trimEnd()
        }

        val result = buildString {
            appendLine("Found ${records.size} conference record(s):")
            appendLine()
            append(entries)
            if (response.nextPageToken != null) {
                appendLine()
                appendLine()
                append("Next page token: ${response.nextPageToken}")
            }
        }

        return result
    }

    fun getTranscript(args: GetTranscriptArgs): String {
        val response = meet.conferenceRecords().transcripts()
            .list(args.conferenceRecordName)
            .execute()

        val transcripts = response.transcripts ?: emptyList()

        if (transcripts.isEmpty()) {
            return "No transcripts found for ${args.conferenceRecordName}."
        }

        val entries = transcripts.joinToString("\n\n") { transcript ->
            buildString {
                appendLine("Name: ${transcript.name}")
                if (transcript.state != null) appendLine("State: ${transcript.state}")
                if (transcript.startTime != null) appendLine("Start: ${transcript.startTime}")
                if (transcript.endTime != null) appendLine("End: ${transcript.endTime}")
                val docs = transcript.docsDestination
                if (docs != null) {
                    if (docs.document != null) appendLine("Document ID: ${docs.document}")
                    if (docs.exportUri != null) append("Export URI: ${docs.exportUri}")
                }
            }.trimEnd()
        }

        return buildString {
            appendLine("Found ${transcripts.size} transcript(s):")
            appendLine()
            append(entries)
        }
    }

    fun listTranscriptEntries(args: ListTranscriptEntriesArgs): String {
        val request = meet.conferenceRecords().transcripts().entries()
            .list(args.transcriptName)
            .setPageSize(args.pageSize)

        if (args.pageToken != null) request.setPageToken(args.pageToken)

        val response = request.execute()
        val entries = response.transcriptEntries ?: emptyList()

        if (entries.isEmpty()) {
            return "No transcript entries found. Entries are deleted 30 days after the meeting."
        }

        val formatted = entries.joinToString("\n\n") { entry ->
            buildString {
                if (entry.participant != null) appendLine("Participant: ${entry.participant}")
                if (entry.text != null) appendLine("Text: ${entry.text}")
                if (entry.startTime != null) appendLine("Start: ${entry.startTime}")
                if (entry.endTime != null) append("End: ${entry.endTime}")
                if (entry.languageCode != null) {
                    appendLine()
                    append("Language: ${entry.languageCode}")
                }
            }.trimEnd()
        }

        val result = buildString {
            appendLine("Found ${entries.size} transcript entry/entries:")
            appendLine()
            append(formatted)
            if (response.nextPageToken != null) {
                appendLine()
                appendLine()
                append("Next page token: ${response.nextPageToken}")
            }
        }

        return result
    }
}

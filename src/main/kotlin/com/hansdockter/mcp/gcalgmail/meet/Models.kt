package com.hansdockter.mcp.gcalgmail.meet

import kotlinx.serialization.Serializable

@Serializable
data class ListConferenceRecordsArgs(
    val filter: String? = null,
    val pageSize: Int = 25,
    val pageToken: String? = null
)

@Serializable
data class GetTranscriptArgs(
    val conferenceRecordName: String
)

@Serializable
data class ListTranscriptEntriesArgs(
    val transcriptName: String,
    val pageSize: Int = 100,
    val pageToken: String? = null
)

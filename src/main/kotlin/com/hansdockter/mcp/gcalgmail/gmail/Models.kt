package com.hansdockter.mcp.gcalgmail.gmail

import kotlinx.serialization.Serializable

@Serializable
data class SendEmailArgs(
    val to: List<String>,
    val subject: String,
    val body: String,
    val htmlBody: String? = null,
    val mimeType: String = "text/plain",
    val cc: List<String>? = null,
    val bcc: List<String>? = null,
    val threadId: String? = null,
    val inReplyTo: String? = null,
    val attachments: List<String>? = null
)

@Serializable
data class ReadEmailArgs(val messageId: String)

@Serializable
data class SearchEmailsArgs(val query: String, val maxResults: Int? = null)

@Serializable
data class ModifyEmailArgs(
    val messageId: String,
    val labelIds: List<String>? = null,
    val addLabelIds: List<String>? = null,
    val removeLabelIds: List<String>? = null
)

@Serializable
data class DeleteEmailArgs(val messageId: String)

@Serializable
object ListEmailLabelsArgs

@Serializable
data class BatchModifyEmailsArgs(
    val messageIds: List<String>,
    val addLabelIds: List<String>? = null,
    val removeLabelIds: List<String>? = null,
    val batchSize: Int = 50
)

@Serializable
data class BatchDeleteEmailsArgs(
    val messageIds: List<String>,
    val batchSize: Int = 50
)

@Serializable
data class CreateLabelArgs(
    val name: String,
    val messageListVisibility: String? = null,
    val labelListVisibility: String? = null
)

@Serializable
data class UpdateLabelArgs(
    val id: String,
    val name: String? = null,
    val messageListVisibility: String? = null,
    val labelListVisibility: String? = null
)

@Serializable
data class DeleteLabelArgs(val id: String)

@Serializable
data class GetOrCreateLabelArgs(
    val name: String,
    val messageListVisibility: String? = null,
    val labelListVisibility: String? = null
)

@Serializable
data class CreateFilterArgs(
    val criteria: GmailFilterCriteria,
    val action: GmailFilterAction
)

@Serializable
object ListFiltersArgs

@Serializable
data class GetFilterArgs(val filterId: String)

@Serializable
data class DeleteFilterArgs(val filterId: String)

@Serializable
data class CreateFilterFromTemplateArgs(
    val template: String,
    val parameters: FilterTemplateParams
)

@Serializable
data class DownloadAttachmentArgs(
    val messageId: String,
    val attachmentId: String,
    val filename: String? = null,
    val savePath: String? = null
)

@Serializable
data class GmailFilterCriteria(
    val from: String? = null,
    val to: String? = null,
    val subject: String? = null,
    val query: String? = null,
    val negatedQuery: String? = null,
    val hasAttachment: Boolean? = null,
    val excludeChats: Boolean? = null,
    val size: Long? = null,
    val sizeComparison: String? = null
)

@Serializable
data class GmailFilterAction(
    val addLabelIds: List<String>? = null,
    val removeLabelIds: List<String>? = null,
    val forward: String? = null
)

@Serializable
data class FilterTemplateParams(
    val senderEmail: String? = null,
    val subjectText: String? = null,
    val searchText: String? = null,
    val listIdentifier: String? = null,
    val sizeInBytes: Long? = null,
    val labelIds: List<String>? = null,
    val archive: Boolean? = null,
    val markAsRead: Boolean? = null,
    val markImportant: Boolean? = null
)

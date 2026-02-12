package com.hansdockter.mcp.gcalgmail.drive

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.Comment
import com.google.api.services.drive.model.Reply

class DriveCommentsService(private val credential: Credential) {
    private val transport = NetHttpTransport()
    private val jsonFactory = GsonFactory.getDefaultInstance()

    private val drive: Drive = Drive.Builder(transport, jsonFactory, credential)
        .setApplicationName("gcal-gmail-mcp")
        .build()

    fun listComments(args: ListDocCommentsArgs): String {
        val request = drive.comments().list(args.fileId)
            .setFields("comments(id,content,resolved,author(displayName,emailAddress),createdTime,modifiedTime,replies(id,content,author(displayName,emailAddress),createdTime)),nextPageToken")
            .setPageSize(args.pageSize ?: 20)

        if (args.pageToken != null) request.pageToken = args.pageToken
        if (args.includeDeleted == true) request.includeDeleted = true

        val result = request.execute()
        val comments = result.comments ?: emptyList()

        if (comments.isEmpty()) {
            return buildString {
                appendLine("No comments found on this document.")
                if (result.nextPageToken != null) {
                    appendLine("Next page token: ${result.nextPageToken}")
                }
            }
        }

        return buildString {
            appendLine("Found ${comments.size} comment(s):")
            appendLine()
            for (comment in comments) {
                appendLine("--- Comment ${comment.id} ---")
                appendLine("Author: ${comment.author?.displayName ?: "Unknown"} (${comment.author?.emailAddress ?: ""})")
                appendLine("Content: ${comment.content}")
                appendLine("Resolved: ${comment.resolved ?: false}")
                appendLine("Created: ${comment.createdTime}")
                if (comment.modifiedTime != null && comment.modifiedTime != comment.createdTime) {
                    appendLine("Modified: ${comment.modifiedTime}")
                }
                val replies = comment.replies ?: emptyList()
                if (replies.isNotEmpty()) {
                    appendLine("Replies (${replies.size}):")
                    for (reply in replies) {
                        appendLine("  - ${reply.author?.displayName ?: "Unknown"}: ${reply.content} (${reply.createdTime})")
                    }
                }
                appendLine()
            }
            if (result.nextPageToken != null) {
                appendLine("Next page token: ${result.nextPageToken}")
            }
        }
    }

    fun getComment(args: GetDocCommentArgs): String {
        val comment = drive.comments().get(args.fileId, args.commentId)
            .setFields("id,content,resolved,author(displayName,emailAddress),createdTime,modifiedTime,quotedFileContent,replies(id,content,author(displayName,emailAddress),createdTime,modifiedTime)")
            .setIncludeDeleted(true)
            .execute()

        return buildString {
            appendLine("Comment ${comment.id}:")
            appendLine("Author: ${comment.author?.displayName ?: "Unknown"} (${comment.author?.emailAddress ?: ""})")
            appendLine("Content: ${comment.content}")
            appendLine("Resolved: ${comment.resolved ?: false}")
            appendLine("Created: ${comment.createdTime}")
            if (comment.modifiedTime != null && comment.modifiedTime != comment.createdTime) {
                appendLine("Modified: ${comment.modifiedTime}")
            }
            if (comment.quotedFileContent != null) {
                appendLine("Quoted text: ${comment.quotedFileContent.value ?: ""}")
            }
            val replies = comment.replies ?: emptyList()
            if (replies.isNotEmpty()) {
                appendLine()
                appendLine("Replies (${replies.size}):")
                for (reply in replies) {
                    appendLine("  --- Reply ${reply.id} ---")
                    appendLine("  Author: ${reply.author?.displayName ?: "Unknown"} (${reply.author?.emailAddress ?: ""})")
                    appendLine("  Content: ${reply.content}")
                    appendLine("  Created: ${reply.createdTime}")
                    if (reply.modifiedTime != null && reply.modifiedTime != reply.createdTime) {
                        appendLine("  Modified: ${reply.modifiedTime}")
                    }
                }
            }
        }
    }

    fun createComment(args: CreateDocCommentArgs): String {
        val comment = Comment().setContent(args.content)

        if (args.quotedContent != null) {
            comment.quotedFileContent = Comment.QuotedFileContent()
                .setValue(args.quotedContent)
        }

        val created = drive.comments().create(args.fileId, comment)
            .setFields("id,content,author(displayName),createdTime")
            .execute()

        return buildString {
            appendLine("Comment created successfully!")
            appendLine("Comment ID: ${created.id}")
            appendLine("Author: ${created.author?.displayName ?: "Unknown"}")
            appendLine("Content: ${created.content}")
            appendLine("Created: ${created.createdTime}")
        }
    }

    fun replyToComment(args: ReplyToDocCommentArgs): String {
        val reply = Reply().setContent(args.content)

        val created = drive.replies().create(args.fileId, args.commentId, reply)
            .setFields("id,content,author(displayName),createdTime")
            .execute()

        return buildString {
            appendLine("Reply added successfully!")
            appendLine("Reply ID: ${created.id}")
            appendLine("Comment ID: ${args.commentId}")
            appendLine("Author: ${created.author?.displayName ?: "Unknown"}")
            appendLine("Content: ${created.content}")
            appendLine("Created: ${created.createdTime}")
        }
    }

    fun resolveComment(args: ResolveDocCommentArgs): String {
        val update = Comment().setResolved(true)

        val resolved = drive.comments().update(args.fileId, args.commentId, update)
            .setFields("id,content,resolved,author(displayName)")
            .execute()

        return buildString {
            appendLine("Comment resolved successfully!")
            appendLine("Comment ID: ${resolved.id}")
            appendLine("Content: ${resolved.content}")
            appendLine("Resolved: ${resolved.resolved}")
        }
    }

    fun deleteComment(args: DeleteDocCommentArgs): String {
        drive.comments().delete(args.fileId, args.commentId).execute()

        return "Comment ${args.commentId} deleted successfully."
    }
}

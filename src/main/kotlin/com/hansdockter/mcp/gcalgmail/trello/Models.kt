package com.hansdockter.mcp.gcalgmail.trello

import kotlinx.serialization.Serializable

@Serializable
data class TrelloCredentials(
    val apiKey: String,
    val token: String
)

// Boards

@Serializable
data class ListTrelloBoardsArgs(
    val filter: String = "open"
)

@Serializable
data class GetTrelloBoardArgs(
    val boardId: String
)

@Serializable
data class SearchTrelloArgs(
    val query: String,
    val modelTypes: String = "cards,boards",
    val boardIds: List<String>? = null,
    val limit: Int = 10
)

// Lists

@Serializable
data class ListTrelloListsArgs(
    val boardId: String,
    val filter: String = "open"
)

@Serializable
data class CreateTrelloListArgs(
    val boardId: String,
    val name: String,
    val pos: String? = null
)

@Serializable
data class UpdateTrelloListArgs(
    val listId: String,
    val name: String? = null,
    val pos: String? = null
)

@Serializable
data class ArchiveTrelloListArgs(
    val listId: String
)

// Cards

@Serializable
data class ListTrelloCardsArgs(
    val boardId: String? = null,
    val listId: String? = null,
    val filter: String = "open"
)

@Serializable
data class GetTrelloCardArgs(
    val cardId: String
)

@Serializable
data class CreateTrelloCardArgs(
    val listId: String,
    val name: String,
    val desc: String? = null,
    val pos: String? = null,
    val due: String? = null,
    val labelIds: List<String>? = null,
    val memberIds: List<String>? = null
)

@Serializable
data class UpdateTrelloCardArgs(
    val cardId: String,
    val name: String? = null,
    val desc: String? = null,
    val pos: String? = null,
    val due: String? = null,
    val dueComplete: Boolean? = null,
    val labelIds: List<String>? = null,
    val memberIds: List<String>? = null
)

@Serializable
data class MoveTrelloCardArgs(
    val cardId: String,
    val listId: String,
    val boardId: String? = null,
    val pos: String? = null
)

@Serializable
data class ArchiveTrelloCardArgs(
    val cardId: String
)

@Serializable
data class AddTrelloCardCommentArgs(
    val cardId: String,
    val text: String
)

// Labels

@Serializable
data class ListTrelloLabelsArgs(
    val boardId: String
)

@Serializable
data class CreateTrelloLabelArgs(
    val boardId: String,
    val name: String,
    val color: String
)

@Serializable
data class UpdateTrelloLabelArgs(
    val labelId: String,
    val name: String? = null,
    val color: String? = null
)

@Serializable
data class DeleteTrelloLabelArgs(
    val labelId: String
)

// Members

@Serializable
data class ListTrelloBoardMembersArgs(
    val boardId: String
)

@Serializable
data class AssignTrelloCardMembersArgs(
    val cardId: String,
    val memberIds: List<String>
)

// Checklists

@Serializable
data class CreateTrelloChecklistArgs(
    val cardId: String,
    val name: String
)

@Serializable
data class GetTrelloChecklistArgs(
    val checklistId: String
)

@Serializable
data class AddTrelloChecklistItemArgs(
    val checklistId: String,
    val name: String,
    val pos: String? = null
)

@Serializable
data class UpdateTrelloChecklistItemArgs(
    val cardId: String,
    val checklistItemId: String,
    val state: String? = null,
    val name: String? = null
)

@Serializable
data class DeleteTrelloChecklistArgs(
    val checklistId: String
)

// Custom Fields

@Serializable
data class ListTrelloCustomFieldsArgs(
    val boardId: String
)

@Serializable
data class GetTrelloCardCustomFieldsArgs(
    val cardId: String
)

@Serializable
data class SetTrelloCardCustomFieldArgs(
    val cardId: String,
    val customFieldId: String,
    val value: String? = null,
    val idValue: String? = null
)

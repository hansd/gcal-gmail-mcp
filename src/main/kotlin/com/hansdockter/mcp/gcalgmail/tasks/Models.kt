package com.hansdockter.mcp.gcalgmail.tasks

import kotlinx.serialization.Serializable

@Serializable
data class ListTaskListsArgs(
    val maxResults: Int = 20,
    val pageToken: String? = null
)

@Serializable
data class CreateTaskListArgs(
    val title: String
)

@Serializable
data class DeleteTaskListArgs(
    val taskListId: String
)

@Serializable
data class ListTasksArgs(
    val taskListId: String = "@default",
    val maxResults: Int = 100,
    val pageToken: String? = null,
    val showCompleted: Boolean = true,
    val showHidden: Boolean = false,
    val dueMin: String? = null,
    val dueMax: String? = null
)

@Serializable
data class GetTaskArgs(
    val taskListId: String = "@default",
    val taskId: String
)

@Serializable
data class CreateTaskArgs(
    val taskListId: String = "@default",
    val title: String,
    val notes: String? = null,
    val due: String? = null,
    val parent: String? = null,
    val previous: String? = null
)

@Serializable
data class UpdateTaskArgs(
    val taskListId: String = "@default",
    val taskId: String,
    val title: String? = null,
    val notes: String? = null,
    val due: String? = null,
    val status: String? = null,
    val completed: String? = null
)

@Serializable
data class DeleteTaskArgs(
    val taskListId: String = "@default",
    val taskId: String
)

@Serializable
data class MoveTaskArgs(
    val taskListId: String = "@default",
    val taskId: String,
    val parent: String? = null,
    val previous: String? = null
)

@Serializable
data class ClearCompletedTasksArgs(
    val taskListId: String = "@default"
)

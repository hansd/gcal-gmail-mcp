package com.hansdockter.mcp.gcalgmail.tasks

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.tasks.Tasks
import com.google.api.services.tasks.model.Task
import com.google.api.services.tasks.model.TaskList

class TasksService(private val credential: Credential) {
    private val transport = NetHttpTransport()
    private val jsonFactory = GsonFactory.getDefaultInstance()

    private val tasks: Tasks = Tasks.Builder(transport, jsonFactory, credential)
        .setApplicationName("gcal-gmail-mcp")
        .build()

    fun listTaskLists(args: ListTaskListsArgs): String {
        val request = tasks.tasklists().list()
            .setMaxResults(args.maxResults)
        if (args.pageToken != null) request.setPageToken(args.pageToken)

        val result = request.execute()
        val items = result.items ?: emptyList()

        if (items.isEmpty()) return "No task lists found."

        return buildString {
            appendLine("Found ${items.size} task lists:\n")
            items.forEach { tl ->
                appendLine("Title: ${tl.title}")
                appendLine("ID: ${tl.id}")
                if (tl.updated != null) appendLine("Updated: ${tl.updated}")
                appendLine()
            }
            if (result.nextPageToken != null) {
                appendLine("Next page token: ${result.nextPageToken}")
            }
        }
    }

    fun createTaskList(args: CreateTaskListArgs): String {
        val taskList = TaskList().setTitle(args.title)
        val created = tasks.tasklists().insert(taskList).execute()
        return "Task list created successfully:\nTitle: ${created.title}\nID: ${created.id}"
    }

    fun deleteTaskList(args: DeleteTaskListArgs): String {
        tasks.tasklists().delete(args.taskListId).execute()
        return "Task list deleted successfully."
    }

    fun listTasks(args: ListTasksArgs): String {
        val request = tasks.tasks().list(args.taskListId)
            .setMaxResults(args.maxResults)
            .setShowCompleted(args.showCompleted)
            .setShowHidden(args.showHidden)
        if (args.pageToken != null) request.setPageToken(args.pageToken)
        if (args.dueMin != null) request.setDueMin(args.dueMin)
        if (args.dueMax != null) request.setDueMax(args.dueMax)

        val result = request.execute()
        val items = result.items ?: emptyList()

        if (items.isEmpty()) return "No tasks found."

        return buildString {
            appendLine("Found ${items.size} tasks:\n")
            items.forEach { task ->
                appendLine(formatTaskSummary(task))
                appendLine()
            }
            if (result.nextPageToken != null) {
                appendLine("Next page token: ${result.nextPageToken}")
            }
        }
    }

    fun getTask(args: GetTaskArgs): String {
        val task = tasks.tasks().get(args.taskListId, args.taskId).execute()
        return formatTaskDetails(task)
    }

    fun createTask(args: CreateTaskArgs): String {
        val task = Task().setTitle(args.title)
        if (args.notes != null) task.notes = args.notes
        if (args.due != null) task.due = args.due

        val request = tasks.tasks().insert(args.taskListId, task)
        if (args.parent != null) request.setParent(args.parent)
        if (args.previous != null) request.setPrevious(args.previous)

        val created = request.execute()
        return "Task created successfully:\n${formatTaskDetails(created)}"
    }

    fun updateTask(args: UpdateTaskArgs): String {
        val existing = tasks.tasks().get(args.taskListId, args.taskId).execute()

        if (args.title != null) existing.title = args.title
        if (args.notes != null) existing.notes = args.notes
        if (args.due != null) existing.due = args.due
        if (args.status != null) existing.status = args.status
        if (args.completed != null) existing.completed = args.completed

        val updated = tasks.tasks().update(args.taskListId, args.taskId, existing).execute()
        return "Task updated successfully:\n${formatTaskDetails(updated)}"
    }

    fun deleteTask(args: DeleteTaskArgs): String {
        tasks.tasks().delete(args.taskListId, args.taskId).execute()
        return "Task deleted successfully."
    }

    fun moveTask(args: MoveTaskArgs): String {
        val request = tasks.tasks().move(args.taskListId, args.taskId)
        if (args.parent != null) request.setParent(args.parent)
        if (args.previous != null) request.setPrevious(args.previous)

        val moved = request.execute()
        return "Task moved successfully:\n${formatTaskDetails(moved)}"
    }

    fun clearCompletedTasks(args: ClearCompletedTasksArgs): String {
        tasks.tasks().clear(args.taskListId).execute()
        return "Completed tasks cleared successfully."
    }

    private fun formatTaskSummary(task: Task): String = buildString {
        val status = if (task.status == "completed") "[x]" else "[ ]"
        append("$status ${task.title ?: "(no title)"}")
        append("  (ID: ${task.id})")
        if (task.due != null) append("  Due: ${task.due}")
    }

    private fun formatTaskDetails(task: Task): String = buildString {
        appendLine("Title: ${task.title ?: "(no title)"}")
        appendLine("ID: ${task.id}")
        appendLine("Status: ${task.status}")
        if (task.notes != null) appendLine("Notes: ${task.notes}")
        if (task.due != null) appendLine("Due: ${task.due}")
        if (task.completed != null) appendLine("Completed: ${task.completed}")
        if (task.parent != null) appendLine("Parent: ${task.parent}")
        if (task.position != null) appendLine("Position: ${task.position}")
        if (task.updated != null) appendLine("Updated: ${task.updated}")
        if (task.links != null && task.links.isNotEmpty()) {
            appendLine("Links:")
            task.links.forEach { link ->
                appendLine("  - ${link.description ?: link.type}: ${link.link}")
            }
        }
    }

}

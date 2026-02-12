package com.hansdockter.mcp.gcalgmail.trello

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

class TrelloService(credentials: TrelloCredentials) {

    private val client = TrelloClient(credentials)

    // ─── Boards ─────────────────────────────────────────────

    fun listBoards(args: ListTrelloBoardsArgs): String = runBlocking {
        val boards = client.get("/members/me/boards", mapOf(
            "filter" to args.filter,
            "fields" to "name,desc,url,closed,dateLastActivity,shortUrl"
        )).jsonArray

        if (boards.isEmpty()) return@runBlocking "No boards found."

        buildString {
            appendLine("Found ${boards.size} board(s):")
            appendLine()
            for (board in boards) {
                val b = board.jsonObject
                appendLine("Board: ${b.str("name")}")
                appendLine("  ID: ${b.str("id")}")
                appendLine("  URL: ${b.str("shortUrl")}")
                val desc = b.str("desc")
                if (desc.isNotBlank()) appendLine("  Description: $desc")
                appendLine("  Last Activity: ${b.str("dateLastActivity")}")
                appendLine()
            }
        }
    }

    fun getBoard(args: GetTrelloBoardArgs): String = runBlocking {
        val b = client.get("/boards/${args.boardId}", mapOf(
            "fields" to "name,desc,url,closed,dateLastActivity,shortUrl,memberships",
            "lists" to "open",
            "list_fields" to "name,pos",
            "members" to "all",
            "member_fields" to "fullName,username"
        )).jsonObject

        buildString {
            appendLine("Board: ${b.str("name")}")
            appendLine("  ID: ${b.str("id")}")
            appendLine("  URL: ${b.str("shortUrl")}")
            val desc = b.str("desc")
            if (desc.isNotBlank()) appendLine("  Description: $desc")
            appendLine("  Closed: ${b.str("closed")}")
            appendLine("  Last Activity: ${b.str("dateLastActivity")}")

            val lists = b["lists"]?.jsonArray
            if (lists != null && lists.isNotEmpty()) {
                appendLine()
                appendLine("Lists (${lists.size}):")
                for (list in lists) {
                    val l = list.jsonObject
                    appendLine("  - ${l.str("name")} (ID: ${l.str("id")})")
                }
            }

            val members = b["members"]?.jsonArray
            if (members != null && members.isNotEmpty()) {
                appendLine()
                appendLine("Members (${members.size}):")
                for (member in members) {
                    val m = member.jsonObject
                    appendLine("  - ${m.str("fullName")} (@${m.str("username")}, ID: ${m.str("id")})")
                }
            }
        }
    }

    fun search(args: SearchTrelloArgs): String = runBlocking {
        val params = mutableMapOf(
            "query" to args.query,
            "modelTypes" to args.modelTypes,
            "cards_limit" to args.limit.toString(),
            "boards_limit" to args.limit.toString(),
            "card_fields" to "name,desc,url,due,closed,idList,idBoard,shortUrl",
            "board_fields" to "name,url,closed,shortUrl"
        )
        args.boardIds?.let { params["idBoards"] = it.joinToString(",") }

        val result = client.get("/search", params).jsonObject

        buildString {
            appendLine("Search results for: \"${args.query}\"")
            appendLine()

            val cards = result["cards"]?.jsonArray
            if (cards != null && cards.isNotEmpty()) {
                appendLine("Cards (${cards.size}):")
                for (card in cards) {
                    val c = card.jsonObject
                    appendLine("  - ${c.str("name")}")
                    appendLine("    ID: ${c.str("id")}")
                    appendLine("    URL: ${c.str("shortUrl")}")
                    val desc = c.str("desc")
                    if (desc.isNotBlank()) appendLine("    Description: ${desc.take(100)}${if (desc.length > 100) "..." else ""}")
                    val due = c.strOrNull("due")
                    if (due != null) appendLine("    Due: $due")
                    appendLine()
                }
            }

            val boards = result["boards"]?.jsonArray
            if (boards != null && boards.isNotEmpty()) {
                appendLine("Boards (${boards.size}):")
                for (board in boards) {
                    val b = board.jsonObject
                    appendLine("  - ${b.str("name")} (ID: ${b.str("id")})")
                    appendLine("    URL: ${b.str("shortUrl")}")
                    appendLine()
                }
            }

            if ((cards == null || cards.isEmpty()) && (boards == null || boards.isEmpty())) {
                appendLine("No results found.")
            }
        }
    }

    // ─── Lists ──────────────────────────────────────────────

    fun listLists(args: ListTrelloListsArgs): String = runBlocking {
        val lists = client.get("/boards/${args.boardId}/lists", mapOf(
            "filter" to args.filter,
            "fields" to "name,pos,closed"
        )).jsonArray

        if (lists.isEmpty()) return@runBlocking "No lists found on this board."

        buildString {
            appendLine("Lists on board (${lists.size}):")
            appendLine()
            for (list in lists) {
                val l = list.jsonObject
                appendLine("  - ${l.str("name")}")
                appendLine("    ID: ${l.str("id")}")
                appendLine()
            }
        }
    }

    fun createList(args: CreateTrelloListArgs): String = runBlocking {
        val params = mutableMapOf("name" to args.name)
        args.pos?.let { params["pos"] = it }

        val l = client.post("/boards/${args.boardId}/lists", params).jsonObject

        buildString {
            appendLine("List created successfully!")
            appendLine("  Name: ${l.str("name")}")
            appendLine("  ID: ${l.str("id")}")
        }
    }

    fun updateList(args: UpdateTrelloListArgs): String = runBlocking {
        val params = mutableMapOf<String, String>()
        args.name?.let { params["name"] = it }
        args.pos?.let { params["pos"] = it }

        val l = client.put("/lists/${args.listId}", params).jsonObject

        buildString {
            appendLine("List updated successfully!")
            appendLine("  Name: ${l.str("name")}")
            appendLine("  ID: ${l.str("id")}")
        }
    }

    fun archiveList(args: ArchiveTrelloListArgs): String = runBlocking {
        val l = client.put("/lists/${args.listId}", mapOf("closed" to "true")).jsonObject

        "List '${l.str("name")}' archived successfully."
    }

    // ─── Cards ──────────────────────────────────────────────

    fun listCards(args: ListTrelloCardsArgs): String = runBlocking {
        val path = when {
            args.listId != null -> "/lists/${args.listId}/cards"
            args.boardId != null -> "/boards/${args.boardId}/cards"
            else -> error("Either boardId or listId must be provided")
        }

        val cards = client.get(path, mapOf(
            "filter" to args.filter,
            "fields" to "name,desc,url,due,dueComplete,closed,idList,labels,shortUrl",
            "members" to "true",
            "member_fields" to "fullName,username"
        )).jsonArray

        if (cards.isEmpty()) return@runBlocking "No cards found."

        buildString {
            appendLine("Found ${cards.size} card(s):")
            appendLine()
            for (card in cards) {
                val c = card.jsonObject
                appendLine("Card: ${c.str("name")}")
                appendLine("  ID: ${c.str("id")}")
                appendLine("  URL: ${c.str("shortUrl")}")
                val desc = c.str("desc")
                if (desc.isNotBlank()) appendLine("  Description: ${desc.take(200)}${if (desc.length > 200) "..." else ""}")
                val due = c.strOrNull("due")
                if (due != null) {
                    val complete = c["dueComplete"]?.jsonPrimitive?.booleanOrNull ?: false
                    appendLine("  Due: $due${if (complete) " (complete)" else ""}")
                }
                val labels = c["labels"]?.jsonArray
                if (labels != null && labels.isNotEmpty()) {
                    val labelNames = labels.map { it.jsonObject.let { lb -> "${lb.str("name")}(${lb.str("color")})" } }
                    appendLine("  Labels: ${labelNames.joinToString(", ")}")
                }
                val members = c["members"]?.jsonArray
                if (members != null && members.isNotEmpty()) {
                    val memberNames = members.map { it.jsonObject.str("fullName") }
                    appendLine("  Members: ${memberNames.joinToString(", ")}")
                }
                appendLine()
            }
        }
    }

    fun getCard(args: GetTrelloCardArgs): String = runBlocking {
        val c = client.get("/cards/${args.cardId}", mapOf(
            "fields" to "name,desc,url,due,dueComplete,closed,idList,idBoard,labels,shortUrl,dateLastActivity",
            "members" to "true",
            "member_fields" to "fullName,username",
            "checklists" to "all",
            "checklist_fields" to "name",
            "actions" to "commentCard",
            "actions_limit" to "10"
        )).jsonObject

        buildString {
            appendLine("Card: ${c.str("name")}")
            appendLine("  ID: ${c.str("id")}")
            appendLine("  URL: ${c.str("shortUrl")}")
            appendLine("  Board ID: ${c.str("idBoard")}")
            appendLine("  List ID: ${c.str("idList")}")
            appendLine("  Closed: ${c.str("closed")}")
            appendLine("  Last Activity: ${c.str("dateLastActivity")}")

            val desc = c.str("desc")
            if (desc.isNotBlank()) {
                appendLine()
                appendLine("Description:")
                appendLine(desc)
            }

            val due = c.strOrNull("due")
            if (due != null) {
                val complete = c["dueComplete"]?.jsonPrimitive?.booleanOrNull ?: false
                appendLine()
                appendLine("Due: $due${if (complete) " (complete)" else ""}")
            }

            val labels = c["labels"]?.jsonArray
            if (labels != null && labels.isNotEmpty()) {
                appendLine()
                appendLine("Labels:")
                for (label in labels) {
                    val lb = label.jsonObject
                    appendLine("  - ${lb.str("name")} (${lb.str("color")}, ID: ${lb.str("id")})")
                }
            }

            val members = c["members"]?.jsonArray
            if (members != null && members.isNotEmpty()) {
                appendLine()
                appendLine("Members:")
                for (member in members) {
                    val m = member.jsonObject
                    appendLine("  - ${m.str("fullName")} (@${m.str("username")}, ID: ${m.str("id")})")
                }
            }

            val checklists = c["checklists"]?.jsonArray
            if (checklists != null && checklists.isNotEmpty()) {
                appendLine()
                appendLine("Checklists:")
                for (checklist in checklists) {
                    val cl = checklist.jsonObject
                    appendLine("  - ${cl.str("name")} (ID: ${cl.str("id")})")
                    val items = cl["checkItems"]?.jsonArray
                    if (items != null) {
                        for (item in items) {
                            val it = item.jsonObject
                            val state = if (it.str("state") == "complete") "[x]" else "[ ]"
                            appendLine("    $state ${it.str("name")} (ID: ${it.str("id")})")
                        }
                    }
                }
            }

            val actions = c["actions"]?.jsonArray
            if (actions != null && actions.isNotEmpty()) {
                appendLine()
                appendLine("Recent Comments:")
                for (action in actions) {
                    val a = action.jsonObject
                    val creator = a["memberCreator"]?.jsonObject?.str("fullName") ?: "Unknown"
                    val text = a["data"]?.jsonObject?.get("text")?.jsonPrimitive?.content ?: ""
                    val date = a.str("date")
                    appendLine("  [$date] $creator: ${text.take(200)}${if (text.length > 200) "..." else ""}")
                }
            }
        }
    }

    fun createCard(args: CreateTrelloCardArgs): String = runBlocking {
        val params = mutableMapOf(
            "idList" to args.listId,
            "name" to args.name
        )
        args.desc?.let { params["desc"] = it }
        args.pos?.let { params["pos"] = it }
        args.due?.let { params["due"] = it }
        args.labelIds?.let { params["idLabels"] = it.joinToString(",") }
        args.memberIds?.let { params["idMembers"] = it.joinToString(",") }

        val c = client.post("/cards", params).jsonObject

        buildString {
            appendLine("Card created successfully!")
            appendLine("  Name: ${c.str("name")}")
            appendLine("  ID: ${c.str("id")}")
            appendLine("  URL: ${c.str("shortUrl")}")
        }
    }

    fun updateCard(args: UpdateTrelloCardArgs): String = runBlocking {
        val params = mutableMapOf<String, String>()
        args.name?.let { params["name"] = it }
        args.desc?.let { params["desc"] = it }
        args.pos?.let { params["pos"] = it }
        args.due?.let { params["due"] = it }
        args.dueComplete?.let { params["dueComplete"] = it.toString() }
        args.labelIds?.let { params["idLabels"] = it.joinToString(",") }
        args.memberIds?.let { params["idMembers"] = it.joinToString(",") }

        val c = client.put("/cards/${args.cardId}", params).jsonObject

        buildString {
            appendLine("Card updated successfully!")
            appendLine("  Name: ${c.str("name")}")
            appendLine("  ID: ${c.str("id")}")
            appendLine("  URL: ${c.str("shortUrl")}")
        }
    }

    fun moveCard(args: MoveTrelloCardArgs): String = runBlocking {
        val params = mutableMapOf("idList" to args.listId)
        args.boardId?.let { params["idBoard"] = it }
        args.pos?.let { params["pos"] = it }

        val c = client.put("/cards/${args.cardId}", params).jsonObject

        buildString {
            appendLine("Card moved successfully!")
            appendLine("  Name: ${c.str("name")}")
            appendLine("  ID: ${c.str("id")}")
            appendLine("  New List ID: ${c.str("idList")}")
        }
    }

    fun archiveCard(args: ArchiveTrelloCardArgs): String = runBlocking {
        val c = client.put("/cards/${args.cardId}", mapOf("closed" to "true")).jsonObject

        "Card '${c.str("name")}' archived successfully."
    }

    fun addCardComment(args: AddTrelloCardCommentArgs): String = runBlocking {
        val a = client.post("/cards/${args.cardId}/actions/comments", mapOf(
            "text" to args.text
        )).jsonObject

        buildString {
            appendLine("Comment added successfully!")
            appendLine("  Card ID: ${args.cardId}")
            appendLine("  Comment ID: ${a.str("id")}")
        }
    }

    // ─── Labels ─────────────────────────────────────────────

    fun listLabels(args: ListTrelloLabelsArgs): String = runBlocking {
        val labels = client.get("/boards/${args.boardId}/labels", mapOf(
            "fields" to "name,color"
        )).jsonArray

        if (labels.isEmpty()) return@runBlocking "No labels found on this board."

        buildString {
            appendLine("Labels on board (${labels.size}):")
            appendLine()
            for (label in labels) {
                val lb = label.jsonObject
                val name = lb.str("name")
                val displayName = if (name.isBlank()) "(unnamed)" else name
                appendLine("  - $displayName (${lb.str("color")}, ID: ${lb.str("id")})")
            }
        }
    }

    fun createLabel(args: CreateTrelloLabelArgs): String = runBlocking {
        val lb = client.post("/boards/${args.boardId}/labels", mapOf(
            "name" to args.name,
            "color" to args.color
        )).jsonObject

        buildString {
            appendLine("Label created successfully!")
            appendLine("  Name: ${lb.str("name")}")
            appendLine("  Color: ${lb.str("color")}")
            appendLine("  ID: ${lb.str("id")}")
        }
    }

    fun updateLabel(args: UpdateTrelloLabelArgs): String = runBlocking {
        val params = mutableMapOf<String, String>()
        args.name?.let { params["name"] = it }
        args.color?.let { params["color"] = it }

        val lb = client.put("/labels/${args.labelId}", params).jsonObject

        buildString {
            appendLine("Label updated successfully!")
            appendLine("  Name: ${lb.str("name")}")
            appendLine("  Color: ${lb.str("color")}")
            appendLine("  ID: ${lb.str("id")}")
        }
    }

    fun deleteLabel(args: DeleteTrelloLabelArgs): String = runBlocking {
        client.delete("/labels/${args.labelId}")
        "Label ${args.labelId} deleted successfully."
    }

    // ─── Members ────────────────────────────────────────────

    fun listBoardMembers(args: ListTrelloBoardMembersArgs): String = runBlocking {
        val members = client.get("/boards/${args.boardId}/members", mapOf(
            "fields" to "fullName,username"
        )).jsonArray

        if (members.isEmpty()) return@runBlocking "No members found on this board."

        buildString {
            appendLine("Board members (${members.size}):")
            appendLine()
            for (member in members) {
                val m = member.jsonObject
                appendLine("  - ${m.str("fullName")} (@${m.str("username")}, ID: ${m.str("id")})")
            }
        }
    }

    fun assignCardMembers(args: AssignTrelloCardMembersArgs): String = runBlocking {
        val c = client.put("/cards/${args.cardId}", mapOf(
            "idMembers" to args.memberIds.joinToString(",")
        )).jsonObject

        buildString {
            appendLine("Card members updated!")
            appendLine("  Card: ${c.str("name")}")
            appendLine("  Member IDs: ${args.memberIds.joinToString(", ")}")
        }
    }

    // ─── Checklists ─────────────────────────────────────────

    fun createChecklist(args: CreateTrelloChecklistArgs): String = runBlocking {
        val cl = client.post("/cards/${args.cardId}/checklists", mapOf(
            "name" to args.name
        )).jsonObject

        buildString {
            appendLine("Checklist created successfully!")
            appendLine("  Name: ${cl.str("name")}")
            appendLine("  ID: ${cl.str("id")}")
            appendLine("  Card ID: ${args.cardId}")
        }
    }

    fun getChecklist(args: GetTrelloChecklistArgs): String = runBlocking {
        val cl = client.get("/checklists/${args.checklistId}", mapOf(
            "fields" to "name,idCard",
            "checkItems" to "all",
            "checkItem_fields" to "name,state,pos"
        )).jsonObject

        buildString {
            appendLine("Checklist: ${cl.str("name")}")
            appendLine("  ID: ${cl.str("id")}")
            appendLine("  Card ID: ${cl.str("idCard")}")

            val items = cl["checkItems"]?.jsonArray
            if (items != null && items.isNotEmpty()) {
                appendLine()
                appendLine("Items (${items.size}):")
                for (item in items) {
                    val it = item.jsonObject
                    val state = if (it.str("state") == "complete") "[x]" else "[ ]"
                    appendLine("  $state ${it.str("name")} (ID: ${it.str("id")})")
                }
            } else {
                appendLine()
                appendLine("No items in this checklist.")
            }
        }
    }

    fun addChecklistItem(args: AddTrelloChecklistItemArgs): String = runBlocking {
        val params = mutableMapOf("name" to args.name)
        args.pos?.let { params["pos"] = it }

        val item = client.post("/checklists/${args.checklistId}/checkItems", params).jsonObject

        buildString {
            appendLine("Checklist item added!")
            appendLine("  Name: ${item.str("name")}")
            appendLine("  ID: ${item.str("id")}")
            appendLine("  State: ${item.str("state")}")
        }
    }

    fun updateChecklistItem(args: UpdateTrelloChecklistItemArgs): String = runBlocking {
        val params = mutableMapOf<String, String>()
        args.state?.let { params["state"] = it }
        args.name?.let { params["name"] = it }

        val item = client.put("/cards/${args.cardId}/checkItem/${args.checklistItemId}", params).jsonObject

        buildString {
            appendLine("Checklist item updated!")
            appendLine("  Name: ${item.str("name")}")
            appendLine("  ID: ${item.str("id")}")
            appendLine("  State: ${item.str("state")}")
        }
    }

    fun deleteChecklist(args: DeleteTrelloChecklistArgs): String = runBlocking {
        client.delete("/checklists/${args.checklistId}")
        "Checklist ${args.checklistId} deleted successfully."
    }

    // ─── Helpers ─────────────────────────────────────────────

    private fun JsonObject.str(key: String): String =
        this[key]?.jsonPrimitive?.content ?: ""

    private fun JsonObject.strOrNull(key: String): String? =
        this[key]?.let { if (it is JsonNull) null else it.jsonPrimitive.content }
}

package com.hansdockter.mcp.gcalgmail

import com.hansdockter.mcp.gcalgmail.auth.OAuthManager
import com.hansdockter.mcp.gcalgmail.calendar.CalendarService
import com.hansdockter.mcp.gcalgmail.docs.DocsService
import com.hansdockter.mcp.gcalgmail.drive.DriveCommentsService
import com.hansdockter.mcp.gcalgmail.gmail.GmailService
import com.hansdockter.mcp.gcalgmail.meet.MeetService
import com.hansdockter.mcp.gcalgmail.sheets.SheetsService
import com.hansdockter.mcp.gcalgmail.tasks.TasksService
import com.hansdockter.mcp.gcalgmail.server.McpServer
import com.hansdockter.mcp.gcalgmail.trello.TrelloAuthManager
import com.hansdockter.mcp.gcalgmail.trello.TrelloService
import com.hansdockter.mcp.gcalgmail.util.AutoUpdater

fun main(args: Array<String>) {
    // Check for updates in the background
    Thread { AutoUpdater.checkAndUpdate() }.apply { isDaemon = true }.start()

    val oauth = OAuthManager()

    if (args.isNotEmpty() && args[0] == "auth") {
        val callback = if (args.size > 1) args[1] else "http://localhost:3000/oauth2callback"
        oauth.authenticate(callback)
        println("Authentication completed successfully")
        return
    }

    if (args.isNotEmpty() && args[0] == "trello-auth") {
        TrelloAuthManager.authenticate()
        println("Trello authentication completed successfully")
        return
    }

    val credential = oauth.loadCredential()
        ?: error("No credentials found. Run with 'auth' first.")

    val trelloService: TrelloService? = try {
        val trelloCreds = TrelloAuthManager.loadCredentials()
        if (trelloCreds != null) TrelloService(trelloCreds) else null
    } catch (e: Exception) {
        System.err.println("Warning: Failed to load Trello credentials: ${e.message}")
        null
    }

    val gmailService = GmailService(credential)
    val calendarService = CalendarService(credential)
    val docsService = DocsService(credential)
    val driveCommentsService = DriveCommentsService(credential)
    val meetService = MeetService(credential)
    val tasksService = TasksService(credential)
    val sheetsService = SheetsService(credential)
    val server = McpServer(gmailService, calendarService, docsService, driveCommentsService, meetService, tasksService, sheetsService, trelloService)
    server.run()
}

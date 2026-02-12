package com.hansdockter.mcp.gcalgmail

import com.hansdockter.mcp.gcalgmail.auth.OAuthManager
import com.hansdockter.mcp.gcalgmail.calendar.CalendarService
import com.hansdockter.mcp.gcalgmail.docs.DocsService
import com.hansdockter.mcp.gcalgmail.drive.DriveCommentsService
import com.hansdockter.mcp.gcalgmail.gmail.GmailService
import com.hansdockter.mcp.gcalgmail.server.McpServer

fun main(args: Array<String>) {
    val oauth = OAuthManager()

    if (args.isNotEmpty() && args[0] == "auth") {
        val callback = if (args.size > 1) args[1] else "http://localhost:3000/oauth2callback"
        oauth.authenticate(callback)
        println("Authentication completed successfully")
        return
    }

    val credential = oauth.loadCredential()
        ?: error("No credentials found. Run with 'auth' first.")

    val gmailService = GmailService(credential)
    val calendarService = CalendarService(credential)
    val docsService = DocsService(credential)
    val driveCommentsService = DriveCommentsService(credential)
    val server = McpServer(gmailService, calendarService, docsService, driveCommentsService)
    server.run()
}

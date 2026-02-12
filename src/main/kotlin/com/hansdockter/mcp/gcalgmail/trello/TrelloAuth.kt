package com.hansdockter.mcp.gcalgmail.trello

import com.hansdockter.mcp.gcalgmail.util.AppConfig
import kotlinx.serialization.json.Json
import java.awt.Desktop
import java.net.URI
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.Scanner

object TrelloAuthManager {

    private val json = Json { ignoreUnknownKeys = true }

    fun loadCredentials(): TrelloCredentials? {
        val path = AppConfig.trelloCredentialsPath
        if (!Files.exists(path)) return null
        val content = Files.readString(path)
        return json.decodeFromString(TrelloCredentials.serializer(), content)
    }

    fun authenticate() {
        AppConfig.ensureConfigDir()
        val scanner = Scanner(System.`in`)

        println()
        println("Trello Authentication Setup")
        println("===========================")
        println()
        println("Step 1: Get your API key")
        println("  Visit: https://trello.com/power-ups/admin")
        println("  Select your Power-Up (or create one), then copy the API key.")
        println()
        print("Enter your Trello API key: ")
        val apiKey = scanner.nextLine().trim()

        if (apiKey.isBlank()) {
            error("API key cannot be empty")
        }

        val tokenUrl = "https://trello.com/1/authorize?expiration=never&scope=read,write&response_type=token&key=$apiKey&name=gcal-gmail-mcp"

        println()
        println("Step 2: Authorize and get your token")
        println("  A browser window will open (or visit the URL below):")
        println("  $tokenUrl")
        println()

        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI(tokenUrl))
            }
        } catch (_: Exception) {
            // Browser open failed, user can use the URL above
        }

        print("Enter your Trello token: ")
        val token = scanner.nextLine().trim()

        if (token.isBlank()) {
            error("Token cannot be empty")
        }

        val credentials = TrelloCredentials(apiKey, token)
        val jsonContent = json.encodeToString(TrelloCredentials.serializer(), credentials)
        Files.writeString(AppConfig.trelloCredentialsPath, jsonContent)

        try {
            Files.setPosixFilePermissions(AppConfig.trelloCredentialsPath, PosixFilePermissions.fromString("rw-------"))
        } catch (_: UnsupportedOperationException) {
            // POSIX permissions not supported on this OS
        }

        println()
        println("Trello credentials saved to ${AppConfig.trelloCredentialsPath}")
    }
}

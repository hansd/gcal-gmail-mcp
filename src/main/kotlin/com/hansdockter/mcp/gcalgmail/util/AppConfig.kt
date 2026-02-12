package com.hansdockter.mcp.gcalgmail.util

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object AppConfig {
    private val homeDir: Path = Paths.get(System.getProperty("user.home"))
    val configDir: Path = homeDir.resolve(".gcal-gmail-mcp")

    val oauthPath: Path = Paths.get(
        System.getenv("GMAIL_OAUTH_PATH")
            ?: System.getenv("GOOGLE_OAUTH_PATH")
            ?: configDir.resolve("gcp-oauth.keys.json").toString()
    )

    val credentialsPath: Path = Paths.get(
        System.getenv("GMAIL_CREDENTIALS_PATH") ?: configDir.resolve("credentials.json").toString()
    )

    val trelloCredentialsPath: Path = Paths.get(
        System.getenv("TRELLO_CREDENTIALS_PATH") ?: configDir.resolve("trello-credentials.json").toString()
    )

    fun ensureConfigDir() {
        if (!Files.exists(configDir)) {
            Files.createDirectories(configDir)
        }
    }
}

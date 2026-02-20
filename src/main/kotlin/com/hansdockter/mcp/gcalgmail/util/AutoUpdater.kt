package com.hansdockter.mcp.gcalgmail.util

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration

object AutoUpdater {
    private const val REPO = "hansd/gcal-gmail-mcp"
    private const val JAR_NAME = "gcal-gmail-mcp.jar"
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class Release(val tag_name: String, val assets: List<Asset>)

    @Serializable
    data class Asset(val name: String, val browser_download_url: String)

    fun checkAndUpdate() {
        try {
            val currentVersion = currentVersion() ?: return
            val jarPath = jarPath() ?: return

            val client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()

            val request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/repos/$REPO/releases/latest"))
                .header("Accept", "application/vnd.github.v3+json")
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) return

            val release = json.decodeFromString<Release>(response.body())
            if (release.tag_name == currentVersion) return

            val asset = release.assets.find { it.name.endsWith(".jar") } ?: return

            System.err.println("Updating from $currentVersion to ${release.tag_name}...")

            val tempFile = jarPath.resolveSibling("$JAR_NAME.new")
            val downloadRequest = HttpRequest.newBuilder()
                .uri(URI.create(asset.browser_download_url))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build()

            val downloadResponse = client.send(downloadRequest, HttpResponse.BodyHandlers.ofFile(tempFile))
            if (downloadResponse.statusCode() == 200) {
                Files.move(tempFile, jarPath, StandardCopyOption.REPLACE_EXISTING)
                System.err.println("Updated to ${release.tag_name}. New version active on next restart.")
            } else {
                Files.deleteIfExists(tempFile)
            }
        } catch (_: Exception) {
            // Silent failure — update is best-effort
        }
    }

    private fun currentVersion(): String? {
        return AutoUpdater::class.java.`package`?.implementationVersion
    }

    private fun jarPath(): Path? {
        val jarLocation = AppConfig.configDir.resolve(JAR_NAME)
        return if (Files.exists(jarLocation)) jarLocation else null
    }
}

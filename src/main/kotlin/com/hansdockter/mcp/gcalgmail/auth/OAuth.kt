package com.hansdockter.mcp.gcalgmail.auth

import com.hansdockter.mcp.gcalgmail.util.AppConfig
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.auth.oauth2.TokenResponse
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.auth.oauth2.BearerToken
import com.google.api.client.auth.oauth2.ClientParametersAuthentication
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.awt.Desktop
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.TimeUnit

private val JSON = Json { ignoreUnknownKeys = true }

@Serializable
private data class OAuthKeysFile(
    val installed: OAuthClient? = null,
    val web: OAuthClient? = null
)

@Serializable
private data class OAuthClient(
    val client_id: String,
    val client_secret: String,
    val redirect_uris: List<String> = emptyList()
)

class OAuthManager {
    private val transport = NetHttpTransport()
    private val jsonFactory = GsonFactory.getDefaultInstance()

    private data class ClientSecrets(val clientId: String, val clientSecret: String)

    private fun loadClientSecrets(oauthPath: Path): ClientSecrets {
        val content = Files.readString(oauthPath)
        val keys = JSON.decodeFromString(OAuthKeysFile.serializer(), content)
        val client = keys.installed ?: keys.web
            ?: error("OAuth keys file must include 'installed' or 'web' credentials")

        return ClientSecrets(client.client_id, client.client_secret)
    }

    private fun loadTokenResponse(credentialsPath: Path): TokenResponse? {
        if (!Files.exists(credentialsPath)) return null
        val content = Files.readString(credentialsPath)
        return jsonFactory.fromString(content, TokenResponse::class.java)
    }

    private fun saveTokenResponse(credentialsPath: Path, tokenResponse: TokenResponse) {
        val json = jsonFactory.toPrettyString(tokenResponse)
        Files.writeString(credentialsPath, json)

        // Set restrictive permissions (owner read/write only) on Unix systems
        try {
            Files.setPosixFilePermissions(credentialsPath, PosixFilePermissions.fromString("rw-------"))
        } catch (_: UnsupportedOperationException) {
            // Not a POSIX filesystem (e.g., Windows) - skip
        }
    }

    fun loadCredential(): Credential? {
        val secrets = loadClientSecrets(AppConfig.oauthPath)
        val tokenResponse = loadTokenResponse(AppConfig.credentialsPath) ?: return null

        return Credential.Builder(BearerToken.authorizationHeaderAccessMethod())
            .setTransport(transport)
            .setJsonFactory(jsonFactory)
            .setTokenServerEncodedUrl("https://oauth2.googleapis.com/token")
            .setClientAuthentication(ClientParametersAuthentication(secrets.clientId, secrets.clientSecret))
            .build()
            .setFromTokenResponse(tokenResponse)
    }

    fun authenticate(callbackUrl: String): Credential {
        AppConfig.ensureConfigDir()

        val localOAuthPath = Path.of(System.getProperty("user.dir"), "gcp-oauth.keys.json")
        if (Files.exists(localOAuthPath)) {
            Files.copy(localOAuthPath, AppConfig.oauthPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            println("OAuth keys found in current directory, copied to global config.")
        }

        if (!Files.exists(AppConfig.oauthPath)) {
            error("OAuth keys file not found. Place gcp-oauth.keys.json in current directory or ${AppConfig.configDir}")
        }

        val secrets = loadClientSecrets(AppConfig.oauthPath)
        val flow = GoogleAuthorizationCodeFlow.Builder(
            transport,
            jsonFactory,
            secrets.clientId,
            secrets.clientSecret,
            listOf(
                "https://www.googleapis.com/auth/gmail.modify",
                "https://www.googleapis.com/auth/gmail.settings.basic",
                "https://www.googleapis.com/auth/calendar",
                "https://www.googleapis.com/auth/documents",
                "https://www.googleapis.com/auth/drive",
                "https://www.googleapis.com/auth/meetings.space.readonly",
                "https://www.googleapis.com/auth/tasks",
                "https://www.googleapis.com/auth/spreadsheets"
            )
        ).setAccessType("offline").build()

        val authUrl = flow.newAuthorizationUrl().setRedirectUri(callbackUrl).build()
        println("Please visit this URL to authenticate: $authUrl")
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(URI(authUrl))
        }

        val code = runBlocking { waitForAuthCode(callbackUrl) }
        val tokenResponse = flow.newTokenRequest(code).setRedirectUri(callbackUrl).execute()
        saveTokenResponse(AppConfig.credentialsPath, tokenResponse)

        return Credential.Builder(BearerToken.authorizationHeaderAccessMethod())
            .setTransport(transport)
            .setJsonFactory(jsonFactory)
            .setTokenServerEncodedUrl("https://oauth2.googleapis.com/token")
            .setClientAuthentication(ClientParametersAuthentication(secrets.clientId, secrets.clientSecret))
            .build()
            .setFromTokenResponse(tokenResponse)
    }

    private suspend fun waitForAuthCode(callbackUrl: String): String {
        val uri = URI(callbackUrl)
        val port = if (uri.port == -1) 3000 else uri.port
        val path = uri.path?.ifBlank { "/oauth2callback" } ?: "/oauth2callback"
        val codeDeferred = CompletableDeferred<String>()

        val server = embeddedServer(Netty, port = port) {
            routing {
                get(path) {
                    val code = call.request.queryParameters["code"]
                    if (code == null) {
                        call.respondText("No code provided")
                        return@get
                    }
                    call.respondText("Authentication successful! You can close this window.")
                    codeDeferred.complete(code)
                }
            }
        }

        server.start(wait = false)

        val code = codeDeferred.await()
        server.stop(1000, 5000)
        return code
    }
}

package com.hansdockter.mcp.gcalgmail.gmail

import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Properties

object EmailComposer {
    private val emailRegex = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun createRawMessage(args: SendEmailArgs, fromEmail: String): String {
        require(args.to.isNotEmpty()) { "At least one recipient is required" }
        args.to.forEach { require(emailRegex.matches(it)) { "Invalid recipient email: $it" } }

        val session = Session.getInstance(Properties())
        val message = MimeMessage(session)

        message.setFrom(InternetAddress(fromEmail))
        message.setRecipients(MimeMessage.RecipientType.TO, args.to.joinToString(",") { it })
        args.cc?.let { message.setRecipients(MimeMessage.RecipientType.CC, it.joinToString(",") { addr -> addr }) }
        args.bcc?.let { message.setRecipients(MimeMessage.RecipientType.BCC, it.joinToString(",") { addr -> addr }) }
        message.subject = args.subject

        if (args.inReplyTo != null) {
            message.setHeader("In-Reply-To", args.inReplyTo)
            message.setHeader("References", args.inReplyTo)
        }

        val hasAttachments = !args.attachments.isNullOrEmpty()
        val wantsHtml = !args.htmlBody.isNullOrBlank() && args.mimeType != "text/plain"

        val bodyPart = MimeBodyPart()
        if (wantsHtml) {
            val alternative = MimeMultipart("alternative")

            val textPart = MimeBodyPart()
            textPart.setText(args.body, "UTF-8")
            alternative.addBodyPart(textPart)

            val htmlPart = MimeBodyPart()
            htmlPart.setContent(args.htmlBody, "text/html; charset=UTF-8")
            alternative.addBodyPart(htmlPart)

            bodyPart.setContent(alternative)
        } else if (args.mimeType == "text/html") {
            bodyPart.setContent(args.htmlBody ?: args.body, "text/html; charset=UTF-8")
        } else {
            bodyPart.setText(args.body, "UTF-8")
        }

        if (hasAttachments) {
            val mixed = MimeMultipart("mixed")
            mixed.addBodyPart(bodyPart)

            args.attachments?.forEach { path ->
                val file = File(path)
                require(file.exists()) { "Attachment does not exist: $path" }

                val attachmentPart = MimeBodyPart()
                attachmentPart.attachFile(file)
                mixed.addBodyPart(attachmentPart)
            }

            message.setContent(mixed)
        } else {
            message.setContent(bodyPart.content, bodyPart.contentType)
        }

        val output = ByteArrayOutputStream()
        message.writeTo(output)
        return output.toString("UTF-8")
    }
}

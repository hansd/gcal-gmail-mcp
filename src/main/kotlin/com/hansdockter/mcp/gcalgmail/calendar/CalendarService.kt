package com.hansdockter.mcp.gcalgmail.calendar

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class CalendarService(private val credential: Credential) {
    private val transport = NetHttpTransport()
    private val jsonFactory = GsonFactory.getDefaultInstance()

    private val calendar: Calendar = Calendar.Builder(transport, jsonFactory, credential)
        .setApplicationName("gcal-gmail-mcp")
        .build()

    fun listEvents(args: ListEventsArgs): String {
        val request = calendar.events().list(args.calendarId)
            .setMaxResults(args.maxResults)
            .setSingleEvents(args.singleEvents)
            .setOrderBy(args.orderBy)

        if (args.timeMin != null) request.setTimeMin(DateTime(args.timeMin))
        if (args.timeMax != null) request.setTimeMax(DateTime(args.timeMax))
        if (args.query != null) request.setQ(args.query)

        val events = request.execute()
        val items = events.items ?: emptyList()

        if (items.isEmpty()) {
            return "No events found."
        }

        return buildString {
            appendLine("Found ${items.size} events:\n")
            items.forEach { event ->
                appendLine(formatEventSummary(event))
                appendLine()
            }
        }
    }

    fun getEvent(args: GetEventArgs): String {
        val event = calendar.events().get(args.calendarId, args.eventId).execute()
        return formatEventDetails(event)
    }

    fun createEvent(args: CreateEventArgs): String {
        val event = Event().apply {
            summary = args.summary
            description = args.description
            location = args.location
            start = args.start.toGoogleEventDateTime()
            end = args.end.toGoogleEventDateTime()

            if (args.attendees != null) {
                attendees = args.attendees.map { email ->
                    EventAttendee().setEmail(email)
                }
            }

            if (args.recurrence != null) {
                recurrence = args.recurrence
            }

            if (args.reminders != null) {
                reminders = Event.Reminders().apply {
                    useDefault = args.reminders.useDefault
                    if (args.reminders.overrides != null) {
                        overrides = args.reminders.overrides.map { o ->
                            EventReminder().setMethod(o.method).setMinutes(o.minutes)
                        }
                    }
                }
            }

            if (args.visibility != null) {
                visibility = args.visibility
            }

            if (args.colorId != null) {
                colorId = args.colorId
            }
        }

        val request = calendar.events().insert(args.calendarId, event)

        if (args.conferenceData) {
            request.setConferenceDataVersion(1)
            event.conferenceData = ConferenceData().apply {
                createRequest = CreateConferenceRequest().apply {
                    requestId = java.util.UUID.randomUUID().toString()
                    conferenceSolutionKey = ConferenceSolutionKey().setType("hangoutsMeet")
                }
            }
        }

        if (args.attendees != null) {
            request.setSendUpdates("all")
        }

        val created = request.execute()
        return buildString {
            appendLine("Event created successfully!")
            appendLine("ID: ${created.id}")
            appendLine("Summary: ${created.summary}")
            appendLine("Link: ${created.htmlLink}")
            if (created.conferenceData?.entryPoints != null) {
                val meetLink = created.conferenceData.entryPoints.firstOrNull { it.entryPointType == "video" }
                if (meetLink != null) {
                    appendLine("Meet Link: ${meetLink.uri}")
                }
            }
        }
    }

    fun updateEvent(args: UpdateEventArgs): String {
        val existing = calendar.events().get(args.calendarId, args.eventId).execute()

        if (args.summary != null) existing.summary = args.summary
        if (args.description != null) existing.description = args.description
        if (args.location != null) existing.location = args.location
        if (args.start != null) existing.start = args.start.toGoogleEventDateTime()
        if (args.end != null) existing.end = args.end.toGoogleEventDateTime()
        if (args.visibility != null) existing.visibility = args.visibility
        if (args.colorId != null) existing.colorId = args.colorId

        if (args.attendees != null) {
            existing.attendees = args.attendees.map { email ->
                EventAttendee().setEmail(email)
            }
        }

        if (args.recurrence != null) {
            existing.recurrence = args.recurrence
        }

        if (args.reminders != null) {
            existing.reminders = Event.Reminders().apply {
                useDefault = args.reminders.useDefault
                if (args.reminders.overrides != null) {
                    overrides = args.reminders.overrides.map { o ->
                        EventReminder().setMethod(o.method).setMinutes(o.minutes)
                    }
                }
            }
        }

        val updated = calendar.events().update(args.calendarId, args.eventId, existing)
            .setSendUpdates("all")
            .execute()

        return buildString {
            appendLine("Event updated successfully!")
            appendLine("ID: ${updated.id}")
            appendLine("Summary: ${updated.summary}")
            appendLine("Link: ${updated.htmlLink}")
        }
    }

    fun deleteEvent(args: DeleteEventArgs): String {
        calendar.events().delete(args.calendarId, args.eventId)
            .setSendUpdates(args.sendUpdates)
            .execute()
        return "Event '${args.eventId}' deleted successfully."
    }

    fun quickAddEvent(args: QuickAddEventArgs): String {
        val event = calendar.events().quickAdd(args.calendarId, args.text)
            .setSendUpdates(args.sendUpdates)
            .execute()

        return buildString {
            appendLine("Event created from text!")
            appendLine("ID: ${event.id}")
            appendLine("Summary: ${event.summary}")
            appendLine("Start: ${formatDateTime(event.start)}")
            appendLine("End: ${formatDateTime(event.end)}")
            appendLine("Link: ${event.htmlLink}")
        }
    }

    fun respondToEvent(args: RespondToEventArgs): String {
        val event = calendar.events().get(args.calendarId, args.eventId).execute()
        val myEmail = calendar.calendarList().get("primary").execute().id
            ?: error("Failed to get calendar email address")

        val attendees = event.attendees ?: mutableListOf()
        val myAttendee = attendees.find { it.email.equals(myEmail, ignoreCase = true) }
            ?: EventAttendee().setEmail(myEmail).also { attendees.add(it) }

        myAttendee.responseStatus = when (args.response.lowercase()) {
            "accept", "accepted", "yes" -> "accepted"
            "decline", "declined", "no" -> "declined"
            "tentative", "maybe" -> "tentative"
            else -> error("Invalid response. Use: accept, decline, or tentative")
        }

        if (args.comment != null) {
            myAttendee.comment = args.comment
        }

        event.attendees = attendees
        calendar.events().update(args.calendarId, args.eventId, event)
            .setSendUpdates("all")
            .execute()

        return "Response '${myAttendee.responseStatus}' recorded for event '${event.summary}'."
    }

    fun listCalendars(args: ListCalendarsArgs): String {
        val request = calendar.calendarList().list()
            .setShowHidden(args.showHidden)
            .setShowDeleted(args.showDeleted)

        val calendars = request.execute()
        val items = calendars.items ?: emptyList()

        if (items.isEmpty()) {
            return "No calendars found."
        }

        return buildString {
            appendLine("Found ${items.size} calendars:\n")
            items.forEach { cal ->
                appendLine("ID: ${cal.id}")
                appendLine("Name: ${cal.summary}")
                if (cal.description != null) appendLine("Description: ${cal.description}")
                appendLine("Access Role: ${cal.accessRole}")
                appendLine("Primary: ${cal.primary ?: false}")
                if (cal.timeZone != null) appendLine("Timezone: ${cal.timeZone}")
                appendLine()
            }
        }
    }

    fun getFreeBusy(args: GetFreeBusyArgs): String {
        val request = FreeBusyRequest().apply {
            timeMin = DateTime(args.timeMin)
            timeMax = DateTime(args.timeMax)
            items = args.calendarIds.map { FreeBusyRequestItem().setId(it) }
            if (args.timeZone != null) timeZone = args.timeZone
        }

        val response = calendar.freebusy().query(request).execute()
        val calendarsInfo = response.calendars ?: emptyMap()

        return buildString {
            appendLine("Free/Busy information from ${args.timeMin} to ${args.timeMax}:\n")
            calendarsInfo.forEach { (calId, info) ->
                appendLine("Calendar: $calId")
                val busy = info.busy ?: emptyList()
                if (busy.isEmpty()) {
                    appendLine("  Status: Free during entire period")
                } else {
                    appendLine("  Busy periods (${busy.size}):")
                    busy.forEach { period ->
                        appendLine("    - ${period.start} to ${period.end}")
                    }
                }
                appendLine()
            }
        }
    }

    fun listEventInstances(args: ListEventInstancesArgs): String {
        val request = calendar.events().instances(args.calendarId, args.eventId)
            .setMaxResults(args.maxResults)

        if (args.timeMin != null) request.setTimeMin(DateTime(args.timeMin))
        if (args.timeMax != null) request.setTimeMax(DateTime(args.timeMax))

        val instances = request.execute()
        val items = instances.items ?: emptyList()

        if (items.isEmpty()) {
            return "No instances found for this recurring event."
        }

        return buildString {
            appendLine("Found ${items.size} instances:\n")
            items.forEach { event ->
                appendLine(formatEventSummary(event))
                appendLine()
            }
        }
    }

    fun listEventAttachments(args: ListEventAttachmentsArgs): String {
        val event = calendar.events().get(args.calendarId, args.eventId).execute()
        val attachments = event.attachments ?: emptyList()

        if (attachments.isEmpty()) {
            return "No attachments found for event '${event.summary}'."
        }

        return buildString {
            appendLine("Attachments for '${event.summary}' (${attachments.size}):\n")
            attachments.forEach { att ->
                appendLine("Title: ${att.title ?: "Untitled"}")
                appendLine("MIME Type: ${att.mimeType}")
                appendLine("File URL: ${att.fileUrl}")
                if (att.iconLink != null) appendLine("Icon: ${att.iconLink}")
                appendLine()
            }
        }
    }

    private fun formatEventSummary(event: Event): String {
        return buildString {
            appendLine("ID: ${event.id}")
            appendLine("Summary: ${event.summary ?: "(No title)"}")
            appendLine("Start: ${formatDateTime(event.start)}")
            appendLine("End: ${formatDateTime(event.end)}")
            if (event.location != null) appendLine("Location: ${event.location}")
            if (event.status != null) appendLine("Status: ${event.status}")
        }
    }

    private fun formatEventDetails(event: Event): String {
        return buildString {
            appendLine("Event Details:")
            appendLine("==============")
            appendLine("ID: ${event.id}")
            appendLine("Summary: ${event.summary ?: "(No title)"}")
            appendLine("Start: ${formatDateTime(event.start)}")
            appendLine("End: ${formatDateTime(event.end)}")
            if (event.description != null) appendLine("Description: ${event.description}")
            if (event.location != null) appendLine("Location: ${event.location}")
            appendLine("Status: ${event.status ?: "confirmed"}")
            if (event.visibility != null) appendLine("Visibility: ${event.visibility}")
            appendLine("Link: ${event.htmlLink}")

            if (event.organizer != null) {
                appendLine("\nOrganizer: ${event.organizer.displayName ?: event.organizer.email}")
            }

            if (event.attendees != null && event.attendees.isNotEmpty()) {
                appendLine("\nAttendees (${event.attendees.size}):")
                event.attendees.forEach { att ->
                    val name = att.displayName ?: att.email
                    val response = att.responseStatus ?: "needsAction"
                    val organizer = if (att.organizer == true) " (organizer)" else ""
                    appendLine("  - $name: $response$organizer")
                }
            }

            if (event.conferenceData?.entryPoints != null) {
                appendLine("\nConference:")
                event.conferenceData.entryPoints.forEach { entry ->
                    appendLine("  - ${entry.entryPointType}: ${entry.uri}")
                }
            }

            if (event.attachments != null && event.attachments.isNotEmpty()) {
                appendLine("\nAttachments (${event.attachments.size}):")
                event.attachments.forEach { att ->
                    appendLine("  - ${att.title ?: "Untitled"}: ${att.fileUrl}")
                }
            }

            if (event.recurrence != null && event.recurrence.isNotEmpty()) {
                appendLine("\nRecurrence:")
                event.recurrence.forEach { rule ->
                    appendLine("  $rule")
                }
            }

            if (event.reminders != null) {
                appendLine("\nReminders:")
                if (event.reminders.useDefault == true) {
                    appendLine("  Using default reminders")
                } else if (event.reminders.overrides != null) {
                    event.reminders.overrides.forEach { r ->
                        appendLine("  - ${r.method}: ${r.minutes} minutes before")
                    }
                }
            }
        }
    }

    private fun formatDateTime(edt: com.google.api.services.calendar.model.EventDateTime?): String {
        if (edt == null) return "Not specified"
        return when {
            edt.dateTime != null -> {
                val instant = Instant.ofEpochMilli(edt.dateTime.value)
                val zoneId = if (edt.timeZone != null) ZoneId.of(edt.timeZone) else ZoneId.systemDefault()
                instant.atZone(zoneId).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm (z)"))
            }
            edt.date != null -> edt.date.toString() + " (all day)"
            else -> "Not specified"
        }
    }

    private fun EventDateTime.toGoogleEventDateTime(): com.google.api.services.calendar.model.EventDateTime {
        return com.google.api.services.calendar.model.EventDateTime().apply {
            if (this@toGoogleEventDateTime.dateTime != null) {
                dateTime = DateTime(this@toGoogleEventDateTime.dateTime)
            }
            if (this@toGoogleEventDateTime.date != null) {
                date = DateTime(this@toGoogleEventDateTime.date)
            }
            if (this@toGoogleEventDateTime.timeZone != null) {
                timeZone = this@toGoogleEventDateTime.timeZone
            }
        }
    }
}

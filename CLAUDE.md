# CLAUDE.md

This file provides context for Claude Code sessions working on this project.

## Project Overview

MCP (Model Context Protocol) server that bridges Google Gmail, Google Calendar, Google Docs, Google Meet, Google Tasks, and Google Sheets with AI assistants. Exposes 53 tools via JSON-RPC 2.0 over stdin/stdout.

## Tech Stack

- Kotlin 1.9.24, Java 21
- Ktor 2.3.7 (OAuth callback server)
- Google API Client libraries (Gmail API v1, Calendar API v3, Docs API v1, Meet API v2, Tasks API v1, Sheets API v4)
- Jakarta Mail 2.0.2 (MIME handling)
- Kotlinx Serialization & Coroutines

## Key Files

```
src/main/kotlin/com/hansdockter/mcp/gcalgmail/
├── Main.kt                    # Entry point, CLI argument handling
├── auth/OAuth.kt              # Google OAuth 2.0 flow, credential storage
├── gmail/
│   ├── GmailService.kt        # Email operations, labels, filters
│   ├── EmailComposer.kt       # MIME message creation
│   └── Models.kt              # Gmail data classes
├── calendar/
│   ├── CalendarService.kt     # Event and calendar operations
│   └── Models.kt              # Calendar data classes
├── docs/
│   ├── DocsService.kt         # Meeting notes and email review docs
│   └── Models.kt              # Docs data classes
├── meet/
│   ├── MeetService.kt         # Conference records, transcripts
│   └── Models.kt              # Meet data classes
├── tasks/
│   ├── TasksService.kt        # Task lists and task operations
│   └── Models.kt              # Tasks data classes
├── sheets/
│   ├── SheetsService.kt       # Spreadsheet read/write operations
│   └── Models.kt              # Sheets data classes
├── server/
│   ├── McpServer.kt           # JSON-RPC protocol handler
│   ├── Protocol.kt            # MCP protocol data structures
│   └── ToolSchemas.kt         # JSON Schema definitions for tools
└── util/AppConfig.kt          # Configuration, paths, env vars
```

## Build & Run

```bash
./gradlew install                                    # Build fat JAR to ~/.gcal-gmail-mcp/
java -jar ~/.gcal-gmail-mcp/gcal-gmail-mcp.jar auth  # Run OAuth flow
java -jar ~/.gcal-gmail-mcp/gcal-gmail-mcp.jar       # Start MCP server
```

## Tools Exposed

**Gmail (19):** send_email, draft_email, read_email, search_emails, modify_email, delete_email, list_email_labels, batch_modify_emails, batch_delete_emails, create_label, update_label, delete_label, get_or_create_label, create_filter, list_filters, get_filter, delete_filter, create_filter_from_template, download_attachment

**Calendar (11):** list_calendar_events, get_calendar_event, create_calendar_event, update_calendar_event, delete_calendar_event, quick_add_calendar_event, respond_to_calendar_event, list_calendars, get_free_busy, list_event_instances, list_event_attachments

**Meet (3):** list_conference_records, get_transcript, list_transcript_entries

**Docs (3):** get_meeting_notes, add_agenda_item, create_email_review_doc

**Tasks (10):** list_task_lists, create_task_list, delete_task_list, list_tasks, get_task, create_task, update_task, delete_task, move_task, clear_completed_tasks

**Sheets (7):** get_spreadsheet, read_sheet_values, read_sheet_multiple_ranges, update_sheet_values, append_sheet_values, create_spreadsheet, create_sheet

## Environment Variables

- `GOOGLE_OAUTH_PATH` - Path to OAuth credentials file (default: `~/.gcal-gmail-mcp/oauth_credentials.json`)
- `GMAIL_CREDENTIALS_PATH` - Path to user tokens (default: `~/.gcal-gmail-mcp/credentials.json`)

## OAuth Scopes

- `gmail.modify` - Read, send, manage emails
- `gmail.settings.basic` - Manage filters and labels
- `calendar` - Full calendar access
- `documents` - Create and edit Google Docs
- `meetings.space.readonly` - Read conference records and transcripts
- `tasks` - Full Google Tasks access
- `spreadsheets` - Read and write Google Sheets

## Architecture Notes

- MCP server reads JSON-RPC requests from stdin, writes responses to stdout
- OAuth uses embedded Ktor server on localhost for callback
- Credentials stored with POSIX 600 permissions
- Batch API used for email searches to reduce N+1 queries

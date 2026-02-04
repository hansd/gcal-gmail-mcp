# gcal-gmail-mcp

A Model Context Protocol (MCP) server that provides Gmail and Google Calendar access for AI assistants like Claude.

## Features

### Gmail
- Send and draft emails (with attachments, HTML, CC/BCC)
- Read and search emails
- Manage labels (create, update, delete)
- Create email filters (with templates)
- Batch operations for modifying/deleting multiple emails
- Download attachments

### Google Calendar
- List, create, update, and delete events
- Quick add events from natural language
- Respond to event invitations (accept/decline/tentative)
- List calendars and check free/busy status
- Support for recurring events, attendees, and Google Meet links

## Prerequisites

- Java 21 or later
- A Google Cloud project with Gmail and Calendar APIs enabled
- OAuth 2.0 credentials (Desktop app type)

## Setup

### 1. Create Google Cloud credentials

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select an existing one
3. Enable the Gmail API and Google Calendar API
4. Go to "Credentials" and create an OAuth 2.0 Client ID (Desktop app type)
5. Download the credentials JSON file

### 2. Build and install

```bash
./gradlew install
```

This builds the fat JAR and installs it to `~/.gcal-gmail-mcp/gcal-gmail-mcp.jar`.

To check the installed version:
```bash
unzip -p ~/.gcal-gmail-mcp/gcal-gmail-mcp.jar META-INF/MANIFEST.MF | grep Implementation-Version
```

### 3. Authenticate

Place your OAuth credentials file as `gcp-oauth.keys.json` in `~/.gcal-gmail-mcp/`, or set the `GOOGLE_OAUTH_PATH` environment variable to point to a shared location (e.g., `~/.google-oauth/gcp-oauth.keys.json`).

Then run:

```bash
java -jar ~/.gcal-gmail-mcp/gcal-gmail-mcp.jar auth
```

Or with a custom OAuth path:

```bash
GOOGLE_OAUTH_PATH=/Users/yourusername/.google-oauth/gcp-oauth.keys.json java -jar ~/.gcal-gmail-mcp/gcal-gmail-mcp.jar auth
```

This will:
1. Open a browser for Google OAuth consent
2. Store your credentials in `~/.gcal-gmail-mcp/credentials.json`

#### Environment Variables

- `GOOGLE_OAUTH_PATH` - Path to OAuth client credentials JSON (default: `~/.gcal-gmail-mcp/gcp-oauth.keys.json`)
- `GMAIL_CREDENTIALS_PATH` - Path to store user credentials (default: `~/.gcal-gmail-mcp/credentials.json`)

**Note:** Environment variables must use absolute paths (e.g., `/Users/yourusername/...`). Tilde (`~`) expansion is not supported.

### 4. Configure your MCP client

Add to your MCP client configuration (e.g., `~/.claude/mcp.json` for Claude Code):

```json
{
  "mcpServers": {
    "gcal-gmail": {
      "command": "java",
      "args": [
        "-jar",
        "/Users/yourusername/.gcal-gmail-mcp/gcal-gmail-mcp.jar"
      ],
      "env": {
        "GOOGLE_OAUTH_PATH": "/Users/yourusername/.google-oauth/gcp-oauth.keys.json"
      }
    }
  }
}
```

**Note:** Use absolute paths. Replace `/Users/yourusername` with your actual home directory path.

## Available Tools

### Gmail Tools
| Tool | Description |
|------|-------------|
| `send_email` | Send a new email |
| `draft_email` | Create an email draft |
| `read_email` | Read a specific email by ID |
| `search_emails` | Search emails using Gmail query syntax |
| `modify_email` | Add/remove labels from an email |
| `delete_email` | Permanently delete an email |
| `list_email_labels` | List all Gmail labels |
| `batch_modify_emails` | Modify labels on multiple emails |
| `batch_delete_emails` | Delete multiple emails |
| `create_label` | Create a new label |
| `update_label` | Update a label |
| `delete_label` | Delete a label |
| `get_or_create_label` | Get existing label or create it |
| `create_filter` | Create an email filter |
| `list_filters` | List all filters |
| `get_filter` | Get filter details |
| `delete_filter` | Delete a filter |
| `create_filter_from_template` | Create filter from predefined template |
| `download_attachment` | Download an email attachment |

### Calendar Tools
| Tool | Description |
|------|-------------|
| `list_calendar_events` | List events with optional filters |
| `get_calendar_event` | Get event details |
| `create_calendar_event` | Create a new event |
| `update_calendar_event` | Update an existing event |
| `delete_calendar_event` | Delete an event |
| `quick_add_calendar_event` | Create event from natural language |
| `respond_to_calendar_event` | Accept/decline/tentative response |
| `list_calendars` | List accessible calendars |
| `get_free_busy` | Check availability |
| `list_event_instances` | List instances of recurring event |
| `list_event_attachments` | List event attachments |

## OAuth Scopes

This server requests the following OAuth scopes:
- `gmail.modify` - Read, send, and manage emails
- `gmail.settings.basic` - Manage filters and labels
- `calendar` - Full calendar access

## Notes

- The `From` header is automatically set to the authenticated Gmail address
- UI-launched processes (like MCP clients) may not inherit shell environment variables - set `GOOGLE_OAUTH_PATH` explicitly in the MCP server config if needed

## License

MIT

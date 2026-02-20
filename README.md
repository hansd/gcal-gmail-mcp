# gcal-gmail-mcp

A Model Context Protocol (MCP) server that provides Gmail, Google Calendar, Google Docs, Google Meet, Google Tasks, Google Sheets, and Trello access for AI assistants like Claude.

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

### Google Docs
- Get and add meeting notes and agenda items
- Create email review documents

### Google Meet
- List conference records
- Get transcripts and transcript entries

### Google Tasks
- Full CRUD for task lists and tasks
- Move, reorder, and clear completed tasks

### Google Sheets
- Read and write cell values
- Append rows, create spreadsheets and sheets

### Trello
- Boards, lists, cards, labels, checklists
- Custom fields, comments, member assignment

## Quick Install

```bash
curl -fsSL https://raw.githubusercontent.com/hansd/gcal-gmail-mcp/main/install.sh | bash
```

This will:
1. Download the latest JAR to `~/.gcal-gmail-mcp/`
2. Check for Java 21+
3. Run authentication (if OAuth credentials are in place)
4. Configure Claude Code automatically

**Before running**, get the `gcp-oauth.keys.json` file from your admin and place it at:
```
~/.gcal-gmail-mcp/gcp-oauth.keys.json
```

### Updating

Run the same install command again to get the latest version.

## Manual Setup

<details>
<summary>Click to expand manual installation steps</summary>

### Prerequisites

- Java 21 or later ([download](https://adoptium.net/))
- OAuth credentials file (`gcp-oauth.keys.json`) from your admin

### Step-by-step

1. **Download the JAR:**
   ```bash
   mkdir -p ~/.gcal-gmail-mcp
   curl -L -o ~/.gcal-gmail-mcp/gcal-gmail-mcp.jar \
     $(curl -s https://api.github.com/repos/hansd/gcal-gmail-mcp/releases/latest \
       | grep browser_download_url | cut -d '"' -f 4)
   ```

2. **Place OAuth credentials:**
   ```bash
   cp /path/to/gcp-oauth.keys.json ~/.gcal-gmail-mcp/gcp-oauth.keys.json
   ```

3. **Authenticate:**
   ```bash
   java -jar ~/.gcal-gmail-mcp/gcal-gmail-mcp.jar auth
   ```

4. **Configure Claude Code:**
   ```bash
   claude mcp add gmail-kotlin --scope user -- java -jar ~/.gcal-gmail-mcp/gcal-gmail-mcp.jar
   ```

</details>

## Building from Source

<details>
<summary>Click to expand (for developers only)</summary>

```bash
git clone https://github.com/hansd/gcal-gmail-mcp.git
cd gcal-gmail-mcp
./gradlew install
```

### Environment Variables

- `GOOGLE_OAUTH_PATH` - Path to OAuth client credentials JSON (default: `~/.gcal-gmail-mcp/gcp-oauth.keys.json`)
- `GMAIL_CREDENTIALS_PATH` - Path to store user credentials (default: `~/.gcal-gmail-mcp/credentials.json`)

</details>

## Available Tools

### Gmail (19 tools)
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

### Calendar (11 tools)
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

### Docs (5 tools)
| Tool | Description |
|------|-------------|
| `get_meeting_notes` | Get meeting notes from a calendar event |
| `get_agenda_items` | Get agenda items from meeting notes |
| `add_agenda_item` | Add an agenda item to meeting notes |
| `create_email_review_doc` | Create a Google Doc for email review |
| `read_doc_content` | Read full text content of a Google Doc |

### Meet (3 tools)
| Tool | Description |
|------|-------------|
| `list_conference_records` | List recent meeting records |
| `get_transcript` | Get transcript metadata and Doc ID |
| `list_transcript_entries` | List raw transcript entries |

### Tasks (10 tools)
| Tool | Description |
|------|-------------|
| `list_task_lists` | List all task lists |
| `create_task_list` | Create a new task list |
| `delete_task_list` | Delete a task list |
| `list_tasks` | List tasks in a task list |
| `get_task` | Get a specific task |
| `create_task` | Create a new task |
| `update_task` | Update a task |
| `delete_task` | Delete a task |
| `move_task` | Move/reorder a task |
| `clear_completed_tasks` | Clear completed tasks from a list |

### Sheets (7 tools)
| Tool | Description |
|------|-------------|
| `get_spreadsheet` | Get spreadsheet metadata |
| `read_sheet_values` | Read cell values from a range |
| `read_sheet_multiple_ranges` | Read from multiple ranges |
| `update_sheet_values` | Update cell values |
| `append_sheet_values` | Append rows after existing data |
| `create_spreadsheet` | Create a new spreadsheet |
| `create_sheet` | Add a new sheet/tab |

### Trello (25 tools)
| Tool | Description |
|------|-------------|
| `list_trello_boards` | List boards |
| `get_trello_board` | Get board details |
| `create_trello_board` | Create a board |
| `search_trello` | Search boards and cards |
| `list_trello_lists` | List lists on a board |
| `create_trello_list` | Create a list |
| `update_trello_list` | Update a list |
| `archive_trello_list` | Archive a list |
| `list_trello_cards` | List cards |
| `get_trello_card` | Get card details |
| `create_trello_card` | Create a card |
| `update_trello_card` | Update a card |
| `move_trello_card` | Move a card |
| `archive_trello_card` | Archive a card |
| `add_trello_card_comment` | Add a comment |
| `list_trello_labels` | List labels |
| `create_trello_label` | Create a label |
| `update_trello_label` | Update a label |
| `delete_trello_label` | Delete a label |
| `list_trello_board_members` | List board members |
| `assign_trello_card_members` | Assign members to a card |
| `create_trello_checklist` | Create a checklist |
| `get_trello_checklist` | Get checklist details |
| `add_trello_checklist_item` | Add a checklist item |
| `update_trello_checklist_item` | Update a checklist item |

## OAuth Scopes

- `gmail.modify` - Read, send, and manage emails
- `gmail.settings.basic` - Manage filters and labels
- `calendar` - Full calendar access
- `documents` - Create and edit Google Docs
- `meetings.space.readonly` - Read conference records and transcripts
- `tasks` - Full Google Tasks access
- `spreadsheets` - Read and write Google Sheets

## Notes

- The `From` header is automatically set to the authenticated Gmail address
- UI-launched processes (like MCP clients) may not inherit shell environment variables - set `GOOGLE_OAUTH_PATH` explicitly in the MCP server config if needed
- Trello integration requires a separate API key and token (set via `TRELLO_API_KEY` and `TRELLO_TOKEN` environment variables)

## License

MIT

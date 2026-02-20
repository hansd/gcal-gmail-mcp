package com.hansdockter.mcp.gcalgmail.sheets

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.AddSheetRequest
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest
import com.google.api.services.sheets.v4.model.Request
import com.google.api.services.sheets.v4.model.SheetProperties
import com.google.api.services.sheets.v4.model.Spreadsheet
import com.google.api.services.sheets.v4.model.SpreadsheetProperties
import com.google.api.services.sheets.v4.model.ValueRange

class SheetsService(private val credential: Credential) {
    private val transport = NetHttpTransport()
    private val jsonFactory = GsonFactory.getDefaultInstance()

    private val sheets: Sheets = Sheets.Builder(transport, jsonFactory, credential)
        .setApplicationName("gcal-gmail-mcp")
        .build()

    fun getSpreadsheet(args: GetSpreadsheetArgs): String {
        val spreadsheet = sheets.spreadsheets().get(args.spreadsheetId).execute()

        return buildString {
            appendLine("Title: ${spreadsheet.properties.title}")
            appendLine("Spreadsheet ID: ${spreadsheet.spreadsheetId}")
            appendLine("URL: ${spreadsheet.spreadsheetUrl}")
            appendLine()

            val sheetList = spreadsheet.sheets ?: emptyList()
            appendLine("Sheets (${sheetList.size}):")
            sheetList.forEach { sheet ->
                val props = sheet.properties
                appendLine("  - ${props.title} (ID: ${props.sheetId}, Index: ${props.index})")
                if (props.gridProperties != null) {
                    appendLine("    Rows: ${props.gridProperties.rowCount}, Columns: ${props.gridProperties.columnCount}")
                }
            }

            val namedRanges = spreadsheet.namedRanges
            if (namedRanges != null && namedRanges.isNotEmpty()) {
                appendLine()
                appendLine("Named Ranges (${namedRanges.size}):")
                namedRanges.forEach { nr ->
                    appendLine("  - ${nr.name}: ${nr.range}")
                }
            }
        }
    }

    fun readSheetValues(args: ReadSheetValuesArgs): String {
        val result = sheets.spreadsheets().values().get(args.spreadsheetId, args.range)
            .setValueRenderOption(args.valueRenderOption)
            .setDateTimeRenderOption(args.dateTimeRenderOption)
            .execute()

        return formatValueRange(result)
    }

    fun readSheetMultipleRanges(args: ReadSheetMultipleRangesArgs): String {
        val result = sheets.spreadsheets().values().batchGet(args.spreadsheetId)
            .setRanges(args.ranges)
            .setValueRenderOption(args.valueRenderOption)
            .setDateTimeRenderOption(args.dateTimeRenderOption)
            .execute()

        val ranges = result.valueRanges ?: emptyList()
        if (ranges.isEmpty()) return "No data found in the specified ranges."

        return buildString {
            ranges.forEach { vr ->
                appendLine("--- ${vr.range} ---")
                appendLine(formatValueRange(vr))
                appendLine()
            }
        }.trimEnd()
    }

    fun updateSheetValues(args: UpdateSheetValuesArgs): String {
        val body = ValueRange().setValues(args.values)

        val result = sheets.spreadsheets().values()
            .update(args.spreadsheetId, args.range, body)
            .setValueInputOption(args.valueInputOption)
            .execute()

        return "Updated ${result.updatedCells} cells in range ${result.updatedRange}."
    }

    fun appendSheetValues(args: AppendSheetValuesArgs): String {
        val body = ValueRange().setValues(args.values)

        val result = sheets.spreadsheets().values()
            .append(args.spreadsheetId, args.range, body)
            .setValueInputOption(args.valueInputOption)
            .setInsertDataOption(args.insertDataOption)
            .execute()

        val updates = result.updates
        return "Appended ${updates.updatedCells} cells. Updated range: ${updates.updatedRange}."
    }

    fun createSpreadsheet(args: CreateSpreadsheetArgs): String {
        val spreadsheet = Spreadsheet()
            .setProperties(SpreadsheetProperties().setTitle(args.title))

        if (args.sheets != null && args.sheets.isNotEmpty()) {
            spreadsheet.sheets = args.sheets.mapIndexed { index, name ->
                com.google.api.services.sheets.v4.model.Sheet()
                    .setProperties(SheetProperties().setTitle(name).setIndex(index))
            }
        }

        val created = sheets.spreadsheets().create(spreadsheet).execute()

        return buildString {
            appendLine("Spreadsheet created successfully.")
            appendLine("Title: ${created.properties.title}")
            appendLine("Spreadsheet ID: ${created.spreadsheetId}")
            appendLine("URL: ${created.spreadsheetUrl}")
            val sheetList = created.sheets ?: emptyList()
            if (sheetList.isNotEmpty()) {
                appendLine("Sheets:")
                sheetList.forEach { s ->
                    appendLine("  - ${s.properties.title} (ID: ${s.properties.sheetId})")
                }
            }
        }
    }

    fun createSheet(args: CreateSheetArgs): String {
        val request = BatchUpdateSpreadsheetRequest()
            .setRequests(listOf(
                Request().setAddSheet(
                    AddSheetRequest().setProperties(
                        SheetProperties().setTitle(args.title)
                    )
                )
            ))

        val result = sheets.spreadsheets().batchUpdate(args.spreadsheetId, request).execute()
        val addedSheet = result.replies?.firstOrNull()?.addSheet

        return if (addedSheet != null) {
            "Sheet '${addedSheet.properties.title}' created (ID: ${addedSheet.properties.sheetId})."
        } else {
            "Sheet '${args.title}' created successfully."
        }
    }

    private fun formatValueRange(vr: ValueRange): String {
        val rows = vr.getValues() ?: return "Range: ${vr.range}\nNo data found."

        return buildString {
            appendLine("Range: ${vr.range}")
            appendLine("Rows: ${rows.size}")
            appendLine()
            rows.forEachIndexed { i, row ->
                appendLine("Row ${i + 1}: ${row.joinToString("\t")}")
            }
        }.trimEnd()
    }
}

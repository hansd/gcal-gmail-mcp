package com.hansdockter.mcp.gcalgmail.sheets

import kotlinx.serialization.Serializable

@Serializable
data class GetSpreadsheetArgs(
    val spreadsheetId: String
)

@Serializable
data class ReadSheetValuesArgs(
    val spreadsheetId: String,
    val range: String,
    val valueRenderOption: String = "FORMATTED_VALUE",
    val dateTimeRenderOption: String = "FORMATTED_STRING"
)

@Serializable
data class ReadSheetMultipleRangesArgs(
    val spreadsheetId: String,
    val ranges: List<String>,
    val valueRenderOption: String = "FORMATTED_VALUE",
    val dateTimeRenderOption: String = "FORMATTED_STRING"
)

@Serializable
data class UpdateSheetValuesArgs(
    val spreadsheetId: String,
    val range: String,
    val values: List<List<String>>,
    val valueInputOption: String = "USER_ENTERED"
)

@Serializable
data class AppendSheetValuesArgs(
    val spreadsheetId: String,
    val range: String,
    val values: List<List<String>>,
    val valueInputOption: String = "USER_ENTERED",
    val insertDataOption: String = "INSERT_ROWS"
)

@Serializable
data class CreateSpreadsheetArgs(
    val title: String,
    val sheets: List<String>? = null
)

@Serializable
data class CreateSheetArgs(
    val spreadsheetId: String,
    val title: String
)

package com.hansdockter.mcp.gcalgmail.docs

import com.google.api.services.docs.v1.model.BatchUpdateDocumentRequest
import com.google.api.services.docs.v1.model.Color
import com.google.api.services.docs.v1.model.CreateParagraphBulletsRequest
import com.google.api.services.docs.v1.model.Dimension
import com.google.api.services.docs.v1.model.InsertInlineImageRequest
import com.google.api.services.docs.v1.model.InsertTableRequest
import com.google.api.services.docs.v1.model.InsertTextRequest
import com.google.api.services.docs.v1.model.Location
import com.google.api.services.docs.v1.model.OptionalColor
import com.google.api.services.docs.v1.model.ParagraphBorder
import com.google.api.services.docs.v1.model.ParagraphStyle
import com.google.api.services.docs.v1.model.Range
import com.google.api.services.docs.v1.model.Request
import com.google.api.services.docs.v1.model.RgbColor
import com.google.api.services.docs.v1.model.Shading
import com.google.api.services.docs.v1.model.Size
import com.google.api.services.docs.v1.model.TextStyle
import com.google.api.services.docs.v1.model.UpdateParagraphStyleRequest
import com.google.api.services.docs.v1.model.UpdateTextStyleRequest
import com.google.api.services.docs.v1.model.WeightedFontFamily
import com.google.api.services.docs.v1.model.Link as DocsLink
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.*
import org.commonmark.node.Document as MdDocument
import org.commonmark.node.Image as MdImage
import org.commonmark.node.Link as MdLink
import org.commonmark.node.Paragraph as MdParagraph
import org.commonmark.parser.Parser

class MarkdownToDocsConverter {

    private val requests = mutableListOf<Request>()
    private var currentIndex: Int = 1

    private val styleStack = ArrayDeque<InlineStyle>()
    private val pendingTextStyles = mutableListOf<PendingTextStyle>()
    private var listDepth = 0
    private val listTypeStack = ArrayDeque<ListType>()

    private sealed class InlineStyle {
        data object BOLD : InlineStyle()
        data object ITALIC : InlineStyle()
        data object CODE : InlineStyle()
        data class LINK(val url: String) : InlineStyle()
    }

    private data class PendingTextStyle(val startIndex: Int, val endIndex: Int, val styles: List<InlineStyle>)

    private enum class ListType { BULLET, ORDERED }

    fun convert(markdown: String, startIndex: Int = 1): List<Request> {
        currentIndex = startIndex
        requests.clear()
        styleStack.clear()
        pendingTextStyles.clear()
        listDepth = 0
        listTypeStack.clear()

        val extensions = listOf(TablesExtension.create())
        val parser = Parser.builder().extensions(extensions).build()
        val document = parser.parse(markdown)

        processNode(document)

        return requests.toList()
    }

    private fun processNode(node: Node) {
        when (node) {
            is MdDocument -> processChildren(node)
            is Heading -> processHeading(node)
            is MdParagraph -> processParagraph(node)
            is BulletList -> processBulletList(node)
            is OrderedList -> processOrderedList(node)
            is ListItem -> processListItem(node)
            is FencedCodeBlock -> processFencedCodeBlock(node)
            is IndentedCodeBlock -> processIndentedCodeBlock(node)
            is BlockQuote -> processBlockQuote(node)
            is ThematicBreak -> processThematicBreak(node)
            is Text -> processText(node)
            is StrongEmphasis -> processStrongEmphasis(node)
            is Emphasis -> processEmphasis(node)
            is Code -> processCode(node)
            is MdLink -> processLink(node)
            is MdImage -> processImage(node)
            is SoftLineBreak -> processSoftLineBreak()
            is HardLineBreak -> processHardLineBreak()
            is TableBlock -> processTable(node)
            else -> processChildren(node)
        }
    }

    private fun processChildren(node: Node) {
        var child = node.firstChild
        while (child != null) {
            processNode(child)
            child = child.next
        }
    }

    // --- Block-level elements ---

    private fun processHeading(node: Heading) {
        val startIdx = currentIndex
        processChildren(node)
        applyPendingTextStyles()
        insertNewline()
        val endIdx = currentIndex

        val level = node.level.coerceIn(1, 6)
        requests.add(Request().setUpdateParagraphStyle(
            UpdateParagraphStyleRequest()
                .setRange(Range().setStartIndex(startIdx).setEndIndex(endIdx))
                .setParagraphStyle(ParagraphStyle().setNamedStyleType("HEADING_$level"))
                .setFields("namedStyleType")
        ))
    }

    private fun processParagraph(node: MdParagraph) {
        val startIdx = currentIndex
        processChildren(node)
        insertNewline()
        val endIdx = currentIndex

        // Apply bullet/ordered list formatting if inside a list item
        if (listTypeStack.isNotEmpty()) {
            val preset = when (listTypeStack.last()) {
                ListType.BULLET -> "BULLET_DISC_CIRCLE_SQUARE"
                ListType.ORDERED -> "NUMBERED_DECIMAL_ALPHA_ROMAN"
            }
            requests.add(Request().setCreateParagraphBullets(
                CreateParagraphBulletsRequest()
                    .setRange(Range().setStartIndex(startIdx).setEndIndex(endIdx))
                    .setBulletPreset(preset)
            ))
            // Reset bold/italic for the full paragraph to prevent style bleeding
            // between consecutive list items, then re-apply intentional styles after.
            requests.add(Request().setUpdateTextStyle(
                UpdateTextStyleRequest()
                    .setRange(Range().setStartIndex(startIdx).setEndIndex(endIdx))
                    .setTextStyle(TextStyle().setBold(false).setItalic(false))
                    .setFields("bold,italic")
            ))
            if (listDepth > 1) {
                requests.add(Request().setUpdateParagraphStyle(
                    UpdateParagraphStyleRequest()
                        .setRange(Range().setStartIndex(startIdx).setEndIndex(endIdx))
                        .setParagraphStyle(ParagraphStyle()
                            .setIndentStart(Dimension().setMagnitude(36.0 * listDepth).setUnit("PT")))
                        .setFields("indentStart")
                ))
            }
        }

        // Apply inline styles after bullet creation + reset so they take precedence
        applyPendingTextStyles()
    }

    private fun processBulletList(node: BulletList) {
        listTypeStack.addLast(ListType.BULLET)
        listDepth++
        processChildren(node)
        listDepth--
        listTypeStack.removeLast()
    }

    private fun processOrderedList(node: OrderedList) {
        listTypeStack.addLast(ListType.ORDERED)
        listDepth++
        processChildren(node)
        listDepth--
        listTypeStack.removeLast()
    }

    private fun processListItem(node: ListItem) {
        processChildren(node)
    }

    private fun processFencedCodeBlock(node: FencedCodeBlock) {
        val code = node.literal ?: return
        val startIdx = currentIndex

        insertText(code)
        // Code blocks from commonmark include a trailing newline in the literal.
        // Ensure we have exactly one trailing newline.
        if (!code.endsWith("\n")) {
            insertNewline()
        }
        val endIdx = currentIndex

        // Monospace font for the code text (exclude trailing newline for cleaner styling)
        val textEnd = if (endIdx > startIdx + 1) endIdx - 1 else endIdx
        requests.add(Request().setUpdateTextStyle(
            UpdateTextStyleRequest()
                .setRange(Range().setStartIndex(startIdx).setEndIndex(textEnd))
                .setTextStyle(TextStyle()
                    .setWeightedFontFamily(WeightedFontFamily().setFontFamily("Courier New"))
                    .setFontSize(Dimension().setMagnitude(10.0).setUnit("PT")))
                .setFields("weightedFontFamily,fontSize")
        ))

        // Background shading on paragraph
        requests.add(Request().setUpdateParagraphStyle(
            UpdateParagraphStyleRequest()
                .setRange(Range().setStartIndex(startIdx).setEndIndex(endIdx))
                .setParagraphStyle(ParagraphStyle()
                    .setShading(Shading().setBackgroundColor(
                        OptionalColor().setColor(Color().setRgbColor(
                            RgbColor().setRed(0.96f).setGreen(0.96f).setBlue(0.96f)
                        ))
                    )))
                .setFields("shading")
        ))
    }

    private fun processIndentedCodeBlock(node: IndentedCodeBlock) {
        val code = node.literal ?: return
        val startIdx = currentIndex

        insertText(code)
        if (!code.endsWith("\n")) {
            insertNewline()
        }
        val endIdx = currentIndex

        val textEnd = if (endIdx > startIdx + 1) endIdx - 1 else endIdx
        requests.add(Request().setUpdateTextStyle(
            UpdateTextStyleRequest()
                .setRange(Range().setStartIndex(startIdx).setEndIndex(textEnd))
                .setTextStyle(TextStyle()
                    .setWeightedFontFamily(WeightedFontFamily().setFontFamily("Courier New"))
                    .setFontSize(Dimension().setMagnitude(10.0).setUnit("PT")))
                .setFields("weightedFontFamily,fontSize")
        ))

        requests.add(Request().setUpdateParagraphStyle(
            UpdateParagraphStyleRequest()
                .setRange(Range().setStartIndex(startIdx).setEndIndex(endIdx))
                .setParagraphStyle(ParagraphStyle()
                    .setShading(Shading().setBackgroundColor(
                        OptionalColor().setColor(Color().setRgbColor(
                            RgbColor().setRed(0.96f).setGreen(0.96f).setBlue(0.96f)
                        ))
                    )))
                .setFields("shading")
        ))
    }

    private fun processBlockQuote(node: BlockQuote) {
        val startIdx = currentIndex
        processChildren(node)
        val endIdx = currentIndex

        requests.add(Request().setUpdateParagraphStyle(
            UpdateParagraphStyleRequest()
                .setRange(Range().setStartIndex(startIdx).setEndIndex(endIdx))
                .setParagraphStyle(ParagraphStyle()
                    .setIndentStart(Dimension().setMagnitude(36.0).setUnit("PT"))
                    .setBorderLeft(ParagraphBorder()
                        .setColor(OptionalColor().setColor(Color().setRgbColor(
                            RgbColor().setRed(0.8f).setGreen(0.8f).setBlue(0.8f)
                        )))
                        .setWidth(Dimension().setMagnitude(2.0).setUnit("PT"))
                        .setPadding(Dimension().setMagnitude(8.0).setUnit("PT"))
                        .setDashStyle("SOLID")
                    ))
                .setFields("indentStart,borderLeft")
        ))
    }

    private fun processThematicBreak(node: ThematicBreak) {
        val startIdx = currentIndex
        insertText("\n")
        val endIdx = currentIndex

        requests.add(Request().setUpdateParagraphStyle(
            UpdateParagraphStyleRequest()
                .setRange(Range().setStartIndex(startIdx).setEndIndex(endIdx))
                .setParagraphStyle(ParagraphStyle()
                    .setBorderBottom(ParagraphBorder()
                        .setColor(OptionalColor().setColor(Color().setRgbColor(
                            RgbColor().setRed(0.8f).setGreen(0.8f).setBlue(0.8f)
                        )))
                        .setWidth(Dimension().setMagnitude(1.0).setUnit("PT"))
                        .setPadding(Dimension().setMagnitude(6.0).setUnit("PT"))
                        .setDashStyle("SOLID")
                    ))
                .setFields("borderBottom")
        ))
    }

    // --- Table handling ---

    private fun processTable(node: Node) {
        // Count rows and columns
        var rowCount = 0
        var colCount = 0
        var child = node.firstChild
        while (child != null) {
            var row = child.firstChild
            while (row != null) {
                if (row is TableRow) {
                    rowCount++
                    var cellCount = 0
                    var cell = row.firstChild
                    while (cell != null) {
                        if (cell is TableCell) cellCount++
                        cell = cell.next
                    }
                    if (cellCount > colCount) colCount = cellCount
                }
                row = row.next
            }
            child = child.next
        }

        if (rowCount == 0 || colCount == 0) return

        val tableStart = currentIndex

        // Insert the table structure
        requests.add(Request().setInsertTable(
            InsertTableRequest()
                .setRows(rowCount)
                .setColumns(colCount)
                .setLocation(Location().setIndex(tableStart))
        ))

        // Table index footprint: 3 + rows * (1 + cols * 2)
        val tableSize = 3 + rowCount * (1 + colCount * 2)
        currentIndex += tableSize

        // Collect cell texts
        val cellTexts = mutableListOf<MutableList<String>>()
        child = node.firstChild
        while (child != null) {
            var row = child.firstChild
            while (row != null) {
                if (row is TableRow) {
                    val rowTexts = mutableListOf<String>()
                    var cell = row.firstChild
                    while (cell != null) {
                        if (cell is TableCell) {
                            rowTexts.add(extractPlainText(cell))
                        }
                        cell = cell.next
                    }
                    while (rowTexts.size < colCount) rowTexts.add("")
                    cellTexts.add(rowTexts)
                }
                row = row.next
            }
            child = child.next
        }

        // Insert cell text forwards, tracking cumulative offset from prior insertions.
        // Base cell index for (r, c) in an empty table: tableStart + 4 + r * (cols * 2 + 1) + c * 2
        // Each text insertion shifts all subsequent base indices by text.length.
        var cumulativeTextInserted = 0
        val cellRanges = mutableListOf<Triple<Int, Int, Boolean>>() // start, end, isHeader

        for (r in cellTexts.indices) {
            for (c in 0 until colCount) {
                val text = cellTexts[r].getOrElse(c) { "" }
                if (text.isEmpty()) continue

                val baseCellIndex = tableStart + 4 + r * (colCount * 2 + 1) + c * 2
                val adjustedIndex = baseCellIndex + cumulativeTextInserted

                requests.add(Request().setInsertText(
                    InsertTextRequest()
                        .setText(text)
                        .setLocation(Location().setIndex(adjustedIndex))
                ))

                cellRanges.add(Triple(adjustedIndex, adjustedIndex + text.length, r == 0))
                cumulativeTextInserted += text.length
                currentIndex += text.length
            }
        }

        // Bold header row cells
        for ((start, end, isHeader) in cellRanges) {
            if (!isHeader) continue
            requests.add(Request().setUpdateTextStyle(
                UpdateTextStyleRequest()
                    .setRange(Range().setStartIndex(start).setEndIndex(end))
                    .setTextStyle(TextStyle().setBold(true))
                    .setFields("bold")
            ))
        }
    }

    private fun extractPlainText(node: Node): String {
        return buildString {
            var child = node.firstChild
            while (child != null) {
                when (child) {
                    is Text -> append(child.literal)
                    is Code -> append(child.literal)
                    is SoftLineBreak -> append(" ")
                    is HardLineBreak -> append("\n")
                    else -> append(extractPlainText(child))
                }
                child = child.next
            }
        }
    }

    // --- Inline elements ---

    private fun processText(node: Text) {
        val content = node.literal ?: return
        val startIdx = currentIndex
        insertText(content)
        val endIdx = currentIndex

        if (styleStack.isNotEmpty()) {
            pendingTextStyles.add(PendingTextStyle(startIdx, endIdx, styleStack.toList()))
        }
    }

    private fun processStrongEmphasis(node: StrongEmphasis) {
        styleStack.addLast(InlineStyle.BOLD)
        processChildren(node)
        styleStack.removeLast()
    }

    private fun processEmphasis(node: Emphasis) {
        styleStack.addLast(InlineStyle.ITALIC)
        processChildren(node)
        styleStack.removeLast()
    }

    private fun processCode(node: Code) {
        val content = node.literal ?: return
        val startIdx = currentIndex
        insertText(content)
        val endIdx = currentIndex

        // Apply code style immediately since Code is a leaf node
        requests.add(Request().setUpdateTextStyle(
            UpdateTextStyleRequest()
                .setRange(Range().setStartIndex(startIdx).setEndIndex(endIdx))
                .setTextStyle(TextStyle()
                    .setWeightedFontFamily(WeightedFontFamily().setFontFamily("Courier New")))
                .setFields("weightedFontFamily")
        ))

        // Also apply any parent inline styles
        if (styleStack.isNotEmpty()) {
            pendingTextStyles.add(PendingTextStyle(startIdx, endIdx, styleStack.toList()))
        }
    }

    private fun processLink(node: MdLink) {
        styleStack.addLast(InlineStyle.LINK(node.destination ?: ""))
        processChildren(node)
        styleStack.removeLast()
    }

    private fun processImage(node: MdImage) {
        val url = node.destination ?: return
        requests.add(Request().setInsertInlineImage(
            InsertInlineImageRequest()
                .setUri(url)
                .setLocation(Location().setIndex(currentIndex))
                .setObjectSize(Size()
                    .setWidth(Dimension().setMagnitude(400.0).setUnit("PT"))
                    .setHeight(Dimension().setMagnitude(300.0).setUnit("PT")))
        ))
        currentIndex += 1
    }

    private fun processSoftLineBreak() {
        val startIdx = currentIndex
        insertText(" ")
        if (styleStack.isNotEmpty()) {
            pendingTextStyles.add(PendingTextStyle(startIdx, currentIndex, styleStack.toList()))
        }
    }

    private fun processHardLineBreak() {
        insertNewline()
    }

    // --- Helpers ---

    private fun insertText(text: String) {
        if (text.isEmpty()) return
        requests.add(Request().setInsertText(
            InsertTextRequest()
                .setText(text)
                .setLocation(Location().setIndex(currentIndex))
        ))
        currentIndex += text.length
    }

    private fun insertNewline() {
        insertText("\n")
    }

    private fun applyPendingTextStyles() {
        for (pending in pendingTextStyles) {
            if (pending.startIndex >= pending.endIndex) continue

            val textStyle = TextStyle()
            val fields = mutableListOf<String>()

            for (style in pending.styles) {
                when (style) {
                    is InlineStyle.BOLD -> {
                        textStyle.bold = true
                        fields.add("bold")
                    }
                    is InlineStyle.ITALIC -> {
                        textStyle.italic = true
                        fields.add("italic")
                    }
                    is InlineStyle.CODE -> {
                        textStyle.weightedFontFamily = WeightedFontFamily().setFontFamily("Courier New")
                        fields.add("weightedFontFamily")
                    }
                    is InlineStyle.LINK -> {
                        textStyle.link = DocsLink().setUrl(style.url)
                        fields.add("link")
                    }
                }
            }

            if (fields.isNotEmpty()) {
                requests.add(Request().setUpdateTextStyle(
                    UpdateTextStyleRequest()
                        .setRange(Range().setStartIndex(pending.startIndex).setEndIndex(pending.endIndex))
                        .setTextStyle(textStyle)
                        .setFields(fields.joinToString(","))
                ))
            }
        }
        pendingTextStyles.clear()
    }
}

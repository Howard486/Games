package com.howardaistudio.subscriptionhub.domain.export

/**
 * RFC 4180 CSV writer with spreadsheet formula-injection protection (SA FR-027).
 *
 * Cells starting with a formula trigger (`= + - @`, tab, CR, or their full-width forms) are
 * prefixed with `'` so spreadsheet apps treat them as text. Cells containing a comma, quote or
 * line break are quoted, with embedded quotes doubled. Rows end with CRLF.
 */
object CsvEncoder {
    const val BOM: Char = '﻿'
    private const val LINE_END = "\r\n"

    private val FORMULA_TRIGGERS = setOf(
        '=', '+', '-', '@', '\t', '\r',
        '＝', '＋', '－', '＠',
    )

    fun escape(value: String): String {
        val guarded = if (value.isNotEmpty() && value[0] in FORMULA_TRIGGERS) "'$value" else value
        val needsQuotes = guarded.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + guarded.replace("\"", "\"\"") + "\"" else guarded
    }

    fun writeRow(fields: List<String>, out: Appendable) {
        fields.forEachIndexed { index, field ->
            if (index > 0) out.append(',')
            out.append(escape(field))
        }
        out.append(LINE_END)
    }
}

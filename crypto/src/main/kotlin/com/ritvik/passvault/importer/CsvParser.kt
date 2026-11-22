package com.ritvik.passvault.importer

/** Small RFC 4180 CSV reader: quoted fields, doubled quotes, line breaks inside quotes, CRLF, BOM. */
object CsvParser {
    fun parse(text: String): List<List<String>> {
        val input = text.removePrefix("﻿")
        val delimiter = detectDelimiter(input)
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        var fieldStarted = false

        fun endField() {
            row.add(field.toString())
            field.setLength(0)
            fieldStarted = false
        }

        fun endRow() {
            endField()
            if (!(row.size == 1 && row[0].isEmpty())) rows.add(row) // skip blank lines
            row = ArrayList()
        }

        while (i < input.length) {
            val c = input[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < input.length && input[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    field.append(c)
                }
            } else {
                when {
                    c == '"' && !fieldStarted -> { inQuotes = true; fieldStarted = true }
                    c == delimiter -> endField()
                    c == '\r' -> {
                        if (i + 1 < input.length && input[i + 1] == '\n') i++
                        endRow()
                    }
                    c == '\n' -> endRow()
                    else -> { field.append(c); fieldStarted = true }
                }
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty() || fieldStarted) endRow()
        return rows
    }

    /** Looks at the first line (outside quotes) and picks comma or semicolon. */
    private fun detectDelimiter(input: String): Char {
        var commas = 0
        var semis = 0
        var inQuotes = false
        for (c in input) {
            if (c == '"') inQuotes = !inQuotes
            if (!inQuotes) {
                if (c == '\n' || c == '\r') break
                if (c == ',') commas++
                if (c == ';') semis++
            }
        }
        return if (semis > commas) ';' else ','
    }
}

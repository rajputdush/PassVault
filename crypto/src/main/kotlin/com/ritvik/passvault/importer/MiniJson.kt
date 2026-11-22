package com.ritvik.passvault.importer

/**
 * A small, strict JSON reader for import files. Objects become Map, arrays List, numbers Double, plus String,
 * Boolean and null. It exists so the importer can be tested on a plain JVM and never trusts huge or deeply nested input.
 */
internal object MiniJson {
    private const val MAX_DEPTH = 64

    fun parse(text: String): Any? {
        val p = Parser(text)
        val value = p.value(0)
        p.skipWhitespace()
        if (!p.atEnd()) throw ImportFormatException("The file is not valid JSON.")
        return value
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun atEnd() = i >= s.length

        fun skipWhitespace() {
            while (i < s.length && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t')) i++
        }

        private fun fail(): Nothing = throw ImportFormatException("The file is not valid JSON.")

        private fun peek(): Char {
            if (i >= s.length) fail()
            return s[i]
        }

        fun value(depth: Int): Any? {
            if (depth > MAX_DEPTH) throw ImportFormatException("The file is nested too deeply.")
            skipWhitespace()
            return when (peek()) {
                '{' -> obj(depth)
                '[' -> array(depth)
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> number()
            }
        }

        private fun literal(word: String, result: Any?): Any? {
            if (!s.startsWith(word, i)) fail()
            i += word.length
            return result
        }

        private fun obj(depth: Int): Map<String, Any?> {
            i++ // {
            val map = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') {
                i++
                return map
            }
            while (true) {
                skipWhitespace()
                if (peek() != '"') fail()
                val key = string()
                skipWhitespace()
                if (peek() != ':') fail()
                i++
                map[key] = value(depth + 1)
                skipWhitespace()
                when (peek()) {
                    ',' -> i++
                    '}' -> {
                        i++
                        return map
                    }
                    else -> fail()
                }
            }
        }

        private fun array(depth: Int): List<Any?> {
            i++ // [
            val list = ArrayList<Any?>()
            skipWhitespace()
            if (peek() == ']') {
                i++
                return list
            }
            while (true) {
                list.add(value(depth + 1))
                skipWhitespace()
                when (peek()) {
                    ',' -> i++
                    ']' -> {
                        i++
                        return list
                    }
                    else -> fail()
                }
            }
        }

        private fun string(): String {
            i++ // opening quote
            val b = StringBuilder()
            while (true) {
                val c = peek()
                i++
                when {
                    c == '"' -> return b.toString()
                    c == '\\' -> {
                        val e = peek()
                        i++
                        when (e) {
                            '"', '\\', '/' -> b.append(e)
                            'b' -> b.append('\b')
                            'f' -> b.append('\u000C')
                            'n' -> b.append('\n')
                            'r' -> b.append('\r')
                            't' -> b.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) fail()
                                val code = s.substring(i, i + 4).toIntOrNull(16) ?: fail()
                                b.append(code.toChar())
                                i += 4
                            }
                            else -> fail()
                        }
                    }
                    c < ' ' -> fail()
                    else -> b.append(c)
                }
            }
        }

        private fun number(): Double {
            val start = i
            while (i < s.length && s[i] in "+-0123456789.eE") i++
            if (start == i) fail()
            return s.substring(start, i).toDoubleOrNull() ?: fail()
        }
    }
}

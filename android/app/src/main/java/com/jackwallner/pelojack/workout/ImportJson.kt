package com.jackwallner.pelojack.workout

/** Strict JSON gate shared by imported programs and workouts, before permissive org.json parsers.
 * Recursion is bounded BEFORE descending into a container, including ignored fields.
 */
internal object ImportJson {
    fun objectOf(text: String): org.json.JSONObject {
        Syntax(text).validate()
        return org.json.JSONObject(text)
    }

    private class Syntax(private val text: String) {
        private var at = 0

        fun validate() {
            whitespace()
            require(at < text.length && text[at] == '{') { "Expected JSON object" }
            value(0)
            whitespace()
            require(at == text.length) { "Trailing JSON content" }
        }

        private fun whitespace() {
            while (at < text.length && text[at] in " \t\r\n") at++
        }

        private fun take(char: Char): Boolean {
            whitespace()
            return (at < text.length && text[at] == char).also { if (it) at++ }
        }

        private fun expect(char: Char) { require(take(char)) { "Expected $char in JSON" } }

        private fun value(depth: Int) {
            whitespace()
            require(at < text.length) { "Missing JSON value" }
            when (text[at]) {
                '{', '[' -> {
                    require(depth < 32) { "JSON nesting exceeds 32" }
                    val objectValue = text[at++] == '{'
                    val close = if (objectValue) '}' else ']'
                    if (take(close)) return
                    do {
                        if (objectValue) { whitespace(); string(); expect(':') }
                        value(depth + 1)
                        if (take(close)) return
                        expect(',')
                    } while (true)
                }
                '"' -> string()
                't' -> literal("true")
                'f' -> literal("false")
                'n' -> literal("null")
                else -> {
                    val number = NUMBER.find(text, at)
                    require(number != null && number.range.first == at) { "Invalid JSON value" }
                    at = number.range.last + 1
                }
            }
        }

        private fun literal(token: String) {
            require(text.startsWith(token, at)) { "Invalid JSON literal" }
            at += token.length
        }

        private fun string() {
            require(at < text.length && text[at++] == '"') { "Expected double-quoted JSON string" }
            while (at < text.length) {
                val char = text[at++]
                if (char == '"') return
                require(char >= ' ') { "Control character in JSON string" }
                if (char == '\\') {
                    require(at < text.length) { "Incomplete JSON escape" }
                    when (text[at++]) {
                        '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> Unit
                        'u' -> {
                            require(at + 4 <= text.length && text.substring(at, at + 4).all { it in "0123456789abcdefABCDEF" }) { "Invalid Unicode escape" }
                            at += 4
                        }
                        else -> error("Invalid JSON escape")
                    }
                }
            }
            error("Unterminated JSON string")
        }
    }

    private val NUMBER = Regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?")
}

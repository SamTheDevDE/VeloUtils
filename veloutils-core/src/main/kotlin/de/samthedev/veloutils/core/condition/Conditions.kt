// SPDX-License-Identifier: GPL-3.0-only
package de.samthedev.veloutils.core.condition

public data class SelectionContext(
    val server: String? = null,
    val world: String? = null,
    val group: String? = null,
    val permissions: Set<String> = emptySet(),
)

public fun interface Condition {
    public fun matches(context: SelectionContext): Boolean
}

public data class BasicSelectors(
    val servers: Set<String> = emptySet(),
    val worlds: Set<String> = emptySet(),
    val groups: Set<String> = emptySet(),
    val permissions: Set<String> = emptySet(),
) {
    public fun compile(advanced: String? = null): Condition {
        val normalizedServers = servers.mapTo(mutableSetOf(), String::lowercase)
        val normalizedWorlds = worlds.mapTo(mutableSetOf(), String::lowercase)
        val normalizedGroups = groups.mapTo(mutableSetOf(), String::lowercase)
        val expression = advanced?.takeIf(String::isNotBlank)?.let(ConditionParser::parse)
        return Condition { context ->
            (normalizedServers.isEmpty() || context.server?.lowercase() in normalizedServers) &&
                (normalizedWorlds.isEmpty() || context.world?.lowercase() in normalizedWorlds) &&
                (normalizedGroups.isEmpty() || context.group?.lowercase() in normalizedGroups) &&
                permissions.all(context.permissions::contains) &&
                (expression?.matches(context) != false)
        }
    }
}

public object ConditionParser {
    public fun parse(source: String): Condition = Parser(source).parse()

    private enum class TokenType { IDENTIFIER, STRING, EQUALS, NOT_EQUALS, AND, OR, NOT, LEFT, RIGHT, COMMA, END }
    private data class Token(val type: TokenType, val text: String, val offset: Int)

    private class Parser(private val source: String) {
        private val tokens = tokenize(source)
        private var current = 0

        fun parse(): Condition {
            require(source.isNotBlank()) { "Condition must not be blank" }
            val result = or()
            expect(TokenType.END, "Unexpected token")
            return result
        }

        private fun or(): Condition {
            var left = and()
            while (match(TokenType.OR)) {
                val previous = left
                val right = and()
                left = Condition { previous.matches(it) || right.matches(it) }
            }
            return left
        }

        private fun and(): Condition {
            var left = unary()
            while (match(TokenType.AND)) {
                val previous = left
                val right = unary()
                left = Condition { previous.matches(it) && right.matches(it) }
            }
            return left
        }

        private fun unary(): Condition {
            if (match(TokenType.NOT)) {
                val nested = unary()
                return Condition { !nested.matches(it) }
            }
            if (match(TokenType.LEFT)) {
                val nested = or()
                expect(TokenType.RIGHT, "Expected ')'")
                return nested
            }
            return predicate()
        }

        private fun predicate(): Condition {
            val key = expect(TokenType.IDENTIFIER, "Expected server, world, group, or permission").text.lowercase()
            if (key == "permission" && match(TokenType.LEFT)) {
                val permission = expect(TokenType.STRING, "Expected permission string").text
                expect(TokenType.RIGHT, "Expected ')' after permission")
                return Condition { permission in it.permissions }
            }
            require(key in setOf("server", "world", "group")) { error("Unknown condition field '$key'", previous().offset) }
            val equals = when {
                match(TokenType.EQUALS) -> true
                match(TokenType.NOT_EQUALS) -> false
                else -> throw IllegalArgumentException(error("Expected == or !=", peek().offset))
            }
            val expected = expect(TokenType.STRING, "Expected quoted value").text
            return Condition { context ->
                val actual = when (key) {
                    "server" -> context.server
                    "world" -> context.world
                    else -> context.group
                }
                actual.equals(expected, ignoreCase = true) == equals
            }
        }

        private fun match(type: TokenType): Boolean {
            if (peek().type != type) return false
            current++
            return true
        }

        private fun expect(type: TokenType, message: String): Token {
            val token = peek()
            require(token.type == type) { error(message, token.offset) }
            current++
            return token
        }

        private fun peek(): Token = tokens[current]
        private fun previous(): Token = tokens[current - 1]
        private fun error(message: String, offset: Int): String = "$message at offset $offset in '$source'"
    }

    private fun tokenize(source: String): List<Token> {
        val result = mutableListOf<Token>()
        var index = 0
        while (index < source.length) {
            val start = index
            when {
                source[index].isWhitespace() -> index++
                source.startsWith("&&", index) -> { result += Token(TokenType.AND, "&&", start); index += 2 }
                source.startsWith("||", index) -> { result += Token(TokenType.OR, "||", start); index += 2 }
                source.startsWith("==", index) -> { result += Token(TokenType.EQUALS, "==", start); index += 2 }
                source.startsWith("!=", index) -> { result += Token(TokenType.NOT_EQUALS, "!=", start); index += 2 }
                source[index] == '!' -> { result += Token(TokenType.NOT, "!", start); index++ }
                source[index] == '(' -> { result += Token(TokenType.LEFT, "(", start); index++ }
                source[index] == ')' -> { result += Token(TokenType.RIGHT, ")", start); index++ }
                source[index] == ',' -> { result += Token(TokenType.COMMA, ",", start); index++ }
                source[index] == '\'' || source[index] == '"' -> {
                    val quote = source[index++]
                    val text = buildString {
                        while (index < source.length && source[index] != quote) {
                            if (source[index] == '\\' && index + 1 < source.length) index++
                            append(source[index++])
                        }
                    }
                    require(index < source.length) { "Unterminated string at offset $start in '$source'" }
                    index++
                    result += Token(TokenType.STRING, text, start)
                }
                source[index].isLetter() || source[index] == '_' -> {
                    while (index < source.length && (source[index].isLetterOrDigit() || source[index] in "_.:-")) index++
                    result += Token(TokenType.IDENTIFIER, source.substring(start, index), start)
                }
                else -> throw IllegalArgumentException("Unexpected character '${source[index]}' at offset $index in '$source'")
            }
        }
        result += Token(TokenType.END, "", source.length)
        return result
    }
}

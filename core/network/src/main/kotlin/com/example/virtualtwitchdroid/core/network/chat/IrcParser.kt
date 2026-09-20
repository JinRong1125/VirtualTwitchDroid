package com.example.virtualtwitchdroid.core.network.chat

import com.example.virtualtwitchdroid.core.model.ChatEmote
import com.example.virtualtwitchdroid.core.model.ChatMessage

/** A parsed IRCv3 line: tags, optional prefix, command and params (last param is the trailing body). */
internal data class IrcMessage(
    val tags: Map<String, String>,
    val prefix: String?,
    val command: String?,
    val params: List<String>,
)

/**
 * Minimal IRCv3 parser for the Twitch chat subset. Handles `@tag=value;...` tags (with the
 * standard value-escaping), an optional `:prefix`, the command word, and a trailing `:message`.
 * Mirrors the approach of Xtra's `ChatUtils.parseIRCMessage` / `parseChatMessage`.
 */
internal object IrcParser {

    fun parseLine(line: String): IrcMessage {
        var rest = line
        var tags = emptyMap<String, String>()

        if (rest.startsWith("@")) {
            val space = rest.indexOf(' ')
            tags = parseTags(rest.substring(1, space))
            rest = rest.substring(space + 1)
        }

        var prefix: String? = null
        if (rest.startsWith(":")) {
            val space = rest.indexOf(' ')
            prefix = rest.substring(1, space)
            rest = rest.substring(space + 1)
        }

        // A trailing parameter is introduced by " :" and runs to end of line.
        var trailing: String? = null
        val trailingIndex = rest.indexOf(" :")
        if (rest.startsWith(":")) {
            trailing = rest.substring(1)
            rest = ""
        } else if (trailingIndex >= 0) {
            trailing = rest.substring(trailingIndex + 2)
            rest = rest.substring(0, trailingIndex)
        }

        val parts = rest.split(" ").filter { it.isNotEmpty() }
        val command = parts.firstOrNull()
        val params = parts.drop(1).toMutableList()
        if (trailing != null) params.add(trailing)

        return IrcMessage(tags, prefix, command, params)
    }

    /** Converts a parsed PRIVMSG/USERNOTICE line into a domain [ChatMessage]. */
    fun toChatMessage(irc: IrcMessage): ChatMessage {
        val tags = irc.tags
        val userLogin = irc.prefix?.substringBefore("!")
        val userName = tags["display-name"]?.takeIf { it.isNotBlank() }
            ?: userLogin
            ?: "unknown"

        val raw = irc.params.lastOrNull().orEmpty()
        var text = raw
        var isAction = false
        // "/me" messages are wrapped as ACTION <text>. Emote positions in the tag are
        // relative to the full trailing param, so track code points stripped from the front.
        var offset = 0
        if (raw.startsWith(ACTION_PREFIX) && raw.endsWith(ACTION_SUFFIX)) {
            text = raw.substring(ACTION_PREFIX.length, raw.length - 1)
            isAction = true
            offset = ACTION_PREFIX.length
        }

        val badges = tags["badges"]
            ?.split(",")
            ?.mapNotNull { it.substringBefore("/").takeIf(String::isNotBlank) }
            ?: emptyList()

        return ChatMessage(
            id = tags["id"]?.takeIf { it.isNotBlank() }
                ?: "${System.nanoTime()}-$userName",
            userName = userName,
            color = tags["color"]?.takeIf { it.isNotBlank() },
            message = text,
            isAction = isAction,
            badges = badges,
            emotes = parseEmotes(tags["emotes"], offset),
        )
    }

    private const val ACTION_PREFIX = "ACTION "
    private const val ACTION_SUFFIX = ""

    /**
     * Parses the IRCv3 `emotes` tag: `id:start-end,start-end/id:start-end`. Indices are
     * Unicode code-point offsets into the message; [offset] shifts them for a stripped
     * ACTION prefix.
     */
    private fun parseEmotes(raw: String?, offset: Int): List<ChatEmote> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split("/").flatMap { group ->
            val colon = group.indexOf(':')
            if (colon < 0) return@flatMap emptyList()
            val id = group.substring(0, colon)
            group.substring(colon + 1).split(",").mapNotNull { range ->
                val dash = range.indexOf('-')
                if (dash < 0) return@mapNotNull null
                val begin = range.substring(0, dash).toIntOrNull() ?: return@mapNotNull null
                val end = range.substring(dash + 1).toIntOrNull() ?: return@mapNotNull null
                ChatEmote(id = id, begin = begin - offset, end = end - offset)
            }
        }.filter { it.begin >= 0 && it.end >= it.begin }
    }

    private fun parseTags(raw: String): Map<String, String> = raw.split(";").mapNotNull { pair ->
        val eq = pair.indexOf('=')
        if (eq < 0) return@mapNotNull null
        val key = pair.substring(0, eq)
        val value = unescapeTagValue(pair.substring(eq + 1))
        key to value
    }.toMap()

    private fun unescapeTagValue(value: String): String {
        if (!value.contains('\\')) return value
        val sb = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (value[i + 1]) {
                    ':' -> sb.append(';')
                    's' -> sb.append(' ')
                    '\\' -> sb.append('\\')
                    'r' -> sb.append('\r')
                    'n' -> sb.append('\n')
                    else -> sb.append(value[i + 1])
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }
}

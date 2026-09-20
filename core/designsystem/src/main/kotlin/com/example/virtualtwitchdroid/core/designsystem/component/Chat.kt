package com.example.virtualtwitchdroid.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.virtualtwitchdroid.core.designsystem.R
import com.example.virtualtwitchdroid.core.designsystem.theme.ChatMessageNotice
import com.example.virtualtwitchdroid.core.designsystem.theme.Elevation
import com.example.virtualtwitchdroid.core.designsystem.theme.EmojiGold
import com.example.virtualtwitchdroid.core.designsystem.theme.IconSize
import com.example.virtualtwitchdroid.core.designsystem.theme.Spacing
import com.example.virtualtwitchdroid.core.model.ChatMessage

// Twitch's 15 default username colors (matches Xtra's `twitchColors` palette).
private val FallbackColors = listOf(
    "#FF0000", "#0000FF", "#008000", "#B22222", "#FF7F50", "#9ACD32", "#FF4500",
    "#2E8B57", "#DAA520", "#D2691E", "#5F9EA0", "#1E90FF", "#FF69B4", "#8A2BE2", "#00FF7F",
)

private fun badgePrefix(badges: List<String>): String = buildString {
    if (badges.any { it.contains("broadcaster") }) append("🎥 ")
    if (badges.any { it.contains("moderator") }) append("🗡 ")
    if (badges.any { it.contains("vip") }) append("💎 ")
    if (badges.any { it.contains("sub") }) append("⭐ ")
}

/** One line of chat: optional badges, a colored bold username, then the message (clone style). */
@Composable
fun ChatMessageRow(message: ChatMessage, modifier: Modifier = Modifier, isSystem: Boolean = false) {
    if (isSystem) {
        // Xtra tints notice/system rows purple across the full width.
        Text(
            modifier = modifier
                .fillMaxWidth()
                .background(ChatMessageNotice)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            text = message.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        return
    }
    val nameColor = remember(message.userName, message.color) {
        parseColor(message.color) ?: FallbackColors[
            (message.userName.hashCode() and 0x7fffffff) % FallbackColors.size,
        ].let { parseColor(it)!! }
    }
    // Build the line once, replacing Twitch emote ranges (e.g. Kappa) with inline CDN images.
    val (annotated, inlineContent) = remember(message.id, message.color, message.emotes, nameColor) {
        buildChatLine(message, nameColor)
    }
    Text(
        modifier = modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        text = annotated,
        inlineContent = inlineContent,
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyMedium,
    )
}

private const val EMOTE_CDN = "https://static-cdn.jtvnw.net/emoticons/v2/"

/** Twitch emote image URL for [id] (2x dark theme), same source Xtra loads emotes from. */
private fun emoteUrl(id: String): String = "$EMOTE_CDN$id/default/dark/2.0"

/** Reconstructs a String from a code-point slice [start, endExclusive). */
private fun codePointSlice(cps: IntArray, start: Int, endExclusive: Int): String =
    buildString { for (i in start until endExclusive) appendCodePoint(cps[i]) }

/**
 * Builds the chat line's [AnnotatedString] plus the inline-content map that renders each Twitch
 * emote range as an image. Emote positions from the IRC tag are code-point offsets, so the body is
 * walked by code point to stay correct for messages containing surrogate pairs.
 */
private fun buildChatLine(
    message: ChatMessage,
    nameColor: Color,
): Pair<AnnotatedString, Map<String, InlineTextContent>> {
    val inline = LinkedHashMap<String, InlineTextContent>()
    val emoteSize = 24.sp
    val annotated = buildAnnotatedString {
        val prefix = badgePrefix(message.badges)
        if (prefix.isNotEmpty()) append(prefix)
        withStyle(SpanStyle(color = nameColor, fontWeight = FontWeight.Bold)) {
            append(message.userName)
        }
        append(if (message.isAction) " " else ": ")
        withStyle(SpanStyle(fontStyle = if (message.isAction) FontStyle.Italic else FontStyle.Normal)) {
            val cps = message.message.codePoints().toArray()
            val emotes = message.emotes
                .filter { it.begin in cps.indices && it.end in cps.indices && it.end >= it.begin }
                .sortedBy { it.begin }
            var cursor = 0
            emotes.forEachIndexed { index, emote ->
                if (emote.begin > cursor) append(codePointSlice(cps, cursor, emote.begin))
                val key = "e$index-${emote.id}"
                appendInlineContent(key, codePointSlice(cps, emote.begin, emote.end + 1))
                inline[key] = InlineTextContent(
                    Placeholder(emoteSize, emoteSize, PlaceholderVerticalAlign.Center),
                ) {
                    AsyncImage(
                        model = emoteUrl(emote.id),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                cursor = emote.end + 1
            }
            if (cursor < cps.size) append(codePointSlice(cps, cursor, cps.size))
        }
    }
    return annotated to inline
}

/** Bottom message-input bar: trophy + count, rounded text field, gold emoji, send / overflow. */
@Composable
fun MessageInputBar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onRewardsClick: () -> Unit,
    onEmotesClick: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    rewardCount: Int = 1,
    hint: String = stringResource(R.string.send_a_message),
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = Elevation.low,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onRewardsClick) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.EmojiEvents,
                        contentDescription = stringResource(R.string.channel_points),
                        tint = EmojiGold,
                        modifier = Modifier.size(IconSize.sm),
                    )
                    Text(
                        text = "  $rewardCount",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { inner ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            inner()
                            if (value.text.isEmpty()) {
                                Text(
                                    hint,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        Icon(
                            Icons.Filled.EmojiEmotions,
                            contentDescription = stringResource(R.string.emotes),
                            tint = EmojiGold,
                            modifier = Modifier
                                .size(IconSize.md)
                                .clickable { onEmotesClick() },
                        )
                    }
                },
            )

            if (value.text.isNotBlank()) {
                IconButton(onClick = onSend) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.send),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                IconButton(onClick = {}) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.more),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

internal fun parseColor(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    return runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
}

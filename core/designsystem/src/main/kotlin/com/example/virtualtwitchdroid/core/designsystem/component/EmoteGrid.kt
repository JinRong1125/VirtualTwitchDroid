package com.example.virtualtwitchdroid.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.virtualtwitchdroid.core.designsystem.theme.IconSize
import com.example.virtualtwitchdroid.core.designsystem.theme.Spacing

/** A Twitch global emote: display [name] (as typed in chat) and its CDN image [url]. */
data class TwitchEmote(val name: String, val url: String)

private fun emote(name: String, id: String) =
    TwitchEmote(name, "https://static-cdn.jtvnw.net/emoticons/v2/$id/default/dark/2.0")

/** A selection of Twitch global emotes for the picker grid. */
val GlobalEmotes: List<TwitchEmote> = listOf(
    emote("Kappa", "25"),
    emote("4Head", "354"),
    emote("BibleThump", "86"),
    emote("Kreygasm", "41"),
    emote("DansGame", "33"),
    emote("SwiftRage", "34"),
    emote("TriHard", "171"),
    emote("SeemsGood", "64138"),
    emote("Jebaited", "90129"),
    emote("VoHiYo", "81274"),
    emote("EleGiggle", "28"),
    emote("NotLikeThis", "58765"),
    emote("PJSalt", "36"),
    emote("ResidentSleeper", "245"),
    emote("BabyRage", "22639"),
    emote("WutFace", "28087"),
    emote("cmonBruh", "84608"),
    emote("PogChamp", "305954156"),
    emote("FailFish", "360"),
    emote("Kippa", "30259"),
    emote("PogChampClassic", "88"),
)

/** The 7-column emote picker grid shown above the input; selecting inserts ":name: " into it. */
@Composable
fun EmoteGrid(onEmoteSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        items(items = GlobalEmotes, key = { it.name }) { e ->
            Box(Modifier.fillMaxWidth()) {
                NetworkImage(
                    model = e.url,
                    contentDescription = e.name,
                    modifier = Modifier
                        .padding(Spacing.xs)
                        .size(IconSize.md)
                        .align(Alignment.Center)
                        .clickable { onEmoteSelected(":${e.name}: ") },
                )
            }
        }
    }
}

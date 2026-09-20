package com.example.virtualtwitchdroid.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TagFaces
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.virtualtwitchdroid.core.designsystem.R
import com.example.virtualtwitchdroid.core.designsystem.theme.EmojiGold
import com.example.virtualtwitchdroid.core.designsystem.theme.IconSize
import com.example.virtualtwitchdroid.core.designsystem.theme.Spacing

/** A channel-points reward (matches the clone's TwitchCloneRewards.json). */
data class Reward(val name: String, val color: Color, val icon: ImageVector, val tokenAmount: Int)

/** The clone's exact reward set (names come from string resources). */
@Composable
fun defaultRewards(): List<Reward> = listOf(
    Reward(stringResource(R.string.reward_random_sub_emote), Color(0xFFF97B2A), Icons.Filled.LockOpen, 100),
    Reward(stringResource(R.string.reward_modify_emote), Color(0xFF7E27B4), Icons.Filled.TagFaces, 500),
    Reward(stringResource(R.string.reward_become_fan), Color(0xFF69E046), Icons.AutoMirrored.Filled.Message, 5000),
    Reward(stringResource(R.string.reward_monthly_subscriber), Color(0xFF53BAB1), Icons.Filled.StarBorder, 10000),
    Reward(stringResource(R.string.reward_become_vip), Color(0xFF6AE64A), Icons.Filled.AirplanemodeActive, 50000),
    Reward(stringResource(R.string.reward_super_vip), Color(0xFFFA411E), Icons.Filled.RocketLaunch, 100000),
)

/** The expandable channel-points panel: header + "1.2X Multiplier" pill + a 3-column reward grid. */
@Composable
fun RewardsPanel(
    modifier: Modifier = Modifier,
    channelName: String = "",
    rewards: List<Reward> = defaultRewards(),
    onRewardClick: (Reward) -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                modifier = Modifier.padding(Spacing.sm).weight(0.7f),
                text = if (channelName.isBlank()) {
                    stringResource(R.string.rewards)
                } else {
                    stringResource(R.string.rewards_of, channelName)
                },
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                modifier = Modifier
                    .padding(Spacing.sm)
                    .background(Color.LightGray.copy(alpha = 0.5f), MaterialTheme.shapes.large)
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                text = stringResource(R.string.multiplier),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxWidth().height(200.dp),
        ) {
            items(items = rewards, key = { it.name }) { reward ->
                RewardCard(reward = reward, onClick = { onRewardClick(reward) })
            }
        }
    }
}

@Composable
private fun RewardCard(reward: Reward, onClick: () -> Unit) {
    Column(modifier = Modifier.padding(Spacing.md)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.9f)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(reward.color)
                .clickable { onClick() },
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = reward.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.padding(Spacing.sm).size(IconSize.lg),
            )
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .padding(Spacing.sm)
                    .background(Color(0xCC000000), RoundedCornerShape(5.dp))
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.EmojiEvents,
                    contentDescription = null,
                    tint = EmojiGold,
                    modifier = Modifier.size(IconSize.xs),
                )
                Text(
                    text = "  ${reward.tokenAmount}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = reward.name,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

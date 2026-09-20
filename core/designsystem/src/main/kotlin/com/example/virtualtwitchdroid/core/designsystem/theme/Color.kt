package com.example.virtualtwitchdroid.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// Twitch-clone palette (matches skydoves/twitch-clone-compose + Stream SDK defaults).
val TwitchPurple = Color(0xFF6441A5) // primaryAccent

// Live/status accents
val LiveRed = Color(0xFFFF3742) // errorAccent — channel-list "Live" badge
val LiveBlue = Color(0xFF1D8CE0) // livestream-overlay "Live" badge
val InfoGreen = Color(0xFF20E070) // infoAccent
val EmojiGold = Color(0xFFF4BC04)

// Dark surfaces / text (Stream defaultDarkColors)
val DarkAppBackground = Color(0xFF101418)
val DarkBarsBackground = Color(0xFF121416)
val DarkInputBackground = Color(0xFF23272B)
val DarkBorders = Color(0xFF1C1E22)
val DarkDisabled = Color(0xFF2E3033)
val TextHighEmphasisDark = Color(0xFFFFFFFF)
val TextLowEmphasis = Color(0xFF7A7A7A)

// Light surfaces / text (Stream defaultColors)
val LightAppBackground = Color(0xFFFCFCFC)
val LightBarsBackground = Color(0xFFFFFFFF)
val LightInputBackground = Color(0xFFF7F7F8)
val LightBorders = Color(0xFFECEBEB)
val LightDisabled = Color(0xFFDBDDE1)
val TextHighEmphasisLight = Color(0xFF000000)

// Image placeholder shimmer
val ShimmerBase = Color(0xFF25282B)
val ShimmerHighlight = Color(0xFFDFDEDE)

// Channel-points reward card colors (from the clone's rewards row)
val RewardOrange = Color(0xFFE8843C)
val RewardPurple = Color(0xFF9147FF)
val RewardGreen = Color(0xFF57C93C)

// Xtra player + chat palette (skydoves → andreyasadchy/Xtra)
val XtraLiveRed = Color(0xFFDD0000) // liveStreamRed — the uptime dot
val XtraAccent = Color(0xFF007DCA)
val PlayerScrim = Color(0x66000000) // ~40% black controls scrim
val ChatMessageFirst = Color(0x800A4028) // first-time chatter (green)
val ChatMessageReward = Color(0x800E4C68) // channel-point redeem (blue)
val ChatMessageNotice = Color(0x803E0E68) // sub/system notice (purple)
val ChatMessageMention = Color(0x80680E0E) // you were mentioned (red)

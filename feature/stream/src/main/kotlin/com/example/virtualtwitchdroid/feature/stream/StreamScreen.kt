package com.example.virtualtwitchdroid.feature.stream

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import androidx.activity.compose.LocalActivity
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.SpeakerNotes
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SpeakerNotesOff
import androidx.compose.material.icons.filled.TagFaces
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.ExoPlayer
import com.example.virtualtwitchdroid.core.designsystem.component.ChatMessageRow
import com.example.virtualtwitchdroid.core.designsystem.component.EmoteGrid
import com.example.virtualtwitchdroid.core.designsystem.theme.IconSize
import com.example.virtualtwitchdroid.core.designsystem.theme.PlayerScrim
import com.example.virtualtwitchdroid.core.designsystem.theme.Sizing
import com.example.virtualtwitchdroid.core.designsystem.theme.TwitchTheme
import com.example.virtualtwitchdroid.core.designsystem.theme.XtraLiveRed
import com.example.virtualtwitchdroid.core.model.ChatMessage
import kotlinx.coroutines.delay

@Composable
internal fun StreamScreen(
    controller: PlayerController,
    onMinimize: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StreamViewModel = hiltViewModel(),
) {
    val streamState by viewModel.streamUiState.collectAsStateWithLifecycle()
    val chatState by viewModel.chatUiState.collectAsStateWithLifecycle()
    val session by controller.session.collectAsStateWithLifecycle()
    val quality by controller.quality.collectAsStateWithLifecycle()
    val playbackError by controller.playbackError.collectAsStateWithLifecycle()

    // Hand the resolved playlist to the shared, app-scoped player. Binding the same channel is a
    // no-op on the media, so returning to this screen (expand from the mini-player) never reloads.
    LaunchedEffect(streamState) {
        (streamState as? StreamUiState.Success)?.let {
            controller.bind(viewModel.channelLogin, viewModel.viewerCount, it.hlsPlaylistUrl)
        }
    }

    // If the shared player is already on this channel (i.e. we expanded from the mini-player),
    // show the live surface immediately rather than flashing the buffering spinner while the
    // ViewModel re-resolves a fresh signed URL for the still-playing stream.
    val alreadyPlaying = session?.channelLogin == viewModel.channelLogin

    StreamScreen(
        channelLogin = viewModel.channelLogin,
        viewers = viewModel.viewerCount,
        streamState = streamState,
        chatState = chatState,
        onRetry = viewModel::retry,
        onRetryChat = viewModel::retryChat,
        playbackError = playbackError,
        onRetryPlayback = {
            // Order matters: retryPlayback() clears the error + forgets the loaded channel FIRST,
            // then retry() resolves a fresh signed URL whose bind() performs the single reload.
            controller.retryPlayback()
            viewModel.retry()
        },
        onSendMessage = viewModel::sendDemoMessage,
        onMinimize = onMinimize,
        onMutedChange = controller::setMuted,
        onPausedChange = controller::setPaused,
        player = controller.exoPlayer,
        showVideoWhileLoading = alreadyPlaying,
        quality = quality,
        onQualitySelected = controller::setQuality,
        modifier = modifier,
    )
}

@Composable
internal fun StreamScreen(
    channelLogin: String,
    streamState: StreamUiState,
    chatState: ChatUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onRetryChat: () -> Unit = {},
    playbackError: Boolean = false,
    onRetryPlayback: () -> Unit = {},
    viewers: Int = 0,
    onSendMessage: (String) -> Unit = {},
    onMinimize: () -> Unit = {},
    onMutedChange: (Boolean) -> Unit = {},
    onPausedChange: (Boolean) -> Unit = {},
    player: ExoPlayer? = null,
    showVideoWhileLoading: Boolean = false,
    quality: PlayerController.VideoQuality = PlayerController.VideoQuality.AUTO,
    onQualitySelected: (PlayerController.VideoQuality) -> Unit = {},
) {
    val activity = LocalActivity.current
    val configuration = LocalConfiguration.current
    val landscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val inPip = remember(configuration) {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity?.isInPictureInPictureMode == true
    }

    var muted by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    // Drive the (externally-owned) shared player from the local UI toggles.
    LaunchedEffect(muted) { onMutedChange(muted) }
    LaunchedEffect(paused) { onPausedChange(paused) }
    var following by rememberSaveable { mutableStateOf(false) }
    var chatOpen by rememberSaveable { mutableStateOf(true) }
    var showEmotes by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var elapsed by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            elapsed++
        }
    }

    // Auto-hide the controller ~3s after it appears, but only while actually playing
    // (Xtra's controllerHideAction) — during buffering/error it stays put.
    LaunchedEffect(controlsVisible, paused, streamState) {
        if (controlsVisible && !paused && streamState is StreamUiState.Success) {
            delay(3000)
            controlsVisible = false
        }
    }

    // The video's on-screen bounds, used as the PiP source-rect hint for a smooth transition.
    var playerBounds by remember { mutableStateOf<android.graphics.Rect?>(null) }

    // Android 12+: smoothly auto-enter PiP when the user leaves the app while watching.
    LaunchedEffect(playerBounds, streamState) {
        if (activity != null && streamState is StreamUiState.Success) {
            activity.updatePipParams(playerBounds, autoEnter = true)
        }
    }

    // Landscape = immersive fullscreen (Xtra hides the system bars in landscape). Uses the modern
    // WindowInsetsControllerCompat instead of the deprecated systemUiVisibility flags.
    // DisposableEffect so leaving the stream screen (e.g. via PiP, then rotating/exiting) ALWAYS
    // restores the system bars — otherwise the "hidden" state leaks and the next screen's
    // status-bar inset collapses (the Live Channels header ends up under the status bar).
    DisposableEffect(landscape, inPip, activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        fun restoreBars() {
            controller ?: return
            // Reset to the DEFAULT behaviour before showing: transient bars don't provide layout
            // insets, so leaving them transient would keep the next screen's statusBarsPadding at
            // 0 and its header would sit under the status bar.
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        if (controller != null) {
            if (landscape && !inPip) {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                restoreBars()
            }
        }
        // Always restore normal, inset-providing bars when the stream screen leaves — covers the
        // in-app mini-player (minimize) path where the screen is popped while still immersive.
        onDispose { restoreBars() }
    }

    // The shared player's surface is movable content: switching layout branches (portrait ⇄
    // landscape ⇄ PiP) *moves* the same PlayerView rather than recreating it, so playback is
    // uninterrupted across the transition. The player itself is owned by PlayerController above.
    val videoSurface: @Composable () -> Unit = remember(player) {
        movableContentOf { if (player != null) PlayerSurface(player, Modifier.fillMaxSize()) }
    }

    val playerPane = @Composable { mod: Modifier ->
        PlayerArea(
            channelLogin = channelLogin,
            streamState = streamState,
            viewers = viewers,
            uptime = formatDuration(elapsed),
            muted = muted,
            paused = paused,
            following = following,
            landscape = landscape,
            chatOpen = chatOpen,
            controlsVisible = controlsVisible && !inPip,
            showChrome = !inPip,
            onToggleControls = { controlsVisible = !controlsVisible },
            onToggleMute = { muted = !muted },
            onTogglePause = { paused = !paused },
            onToggleFollow = { following = !following },
            onToggleChat = { chatOpen = !chatOpen },
            onSettings = { showSettings = true },
            onRetry = onRetry,
            playbackError = playbackError,
            onRetryPlayback = onRetryPlayback,
            onMinimize = onMinimize,
            onFullscreen = { activity?.toggleFullscreen(landscape) },
            onPip = { activity?.enterPip(playerBounds) },
            onBoundsChanged = { playerBounds = it },
            videoContent = videoSurface,
            showVideoWhileLoading = showVideoWhileLoading,
            modifier = mod,
        )
    }

    val chat = @Composable { mod: Modifier ->
        ChatPanel(
            messages = chatState.messages,
            errored = chatState.errored,
            input = input,
            showEmotes = showEmotes,
            onRetry = onRetryChat,
            onValueChange = { input = it },
            onToggleEmotes = { showEmotes = !showEmotes },
            onEmoteSelected = { input = TextFieldValue(input.text + it) },
            onSend = {
                onSendMessage(input.text)
                input = TextFieldValue("")
            },
            modifier = mod,
        )
    }

    when {
        inPip -> Box(modifier.fillMaxSize().background(Color.Black)) { playerPane(Modifier.fillMaxSize()) }

        landscape -> Row(modifier.fillMaxSize().background(Color.Black)) {
            playerPane(Modifier.weight(1f).fillMaxSize())
            if (chatOpen) {
                // imePadding lifts the chat input above the soft keyboard in immersive landscape.
                chat(
                    Modifier.weight(0.32f).fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .imePadding(),
                )
            }
        }

        else -> Column(
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                // safeDrawing = system bars + IME (unioned, no double padding), so the chat input
                // stays above the keyboard while the player keeps its status-bar inset.
                .safeDrawingPadding(),
        ) {
            playerPane(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            chat(Modifier.fillMaxWidth().weight(1f))
        }
    }

    if (showSettings) {
        PlayerSettingsSheet(
            quality = quality,
            onQualitySelected = onQualitySelected,
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun PlayerArea(
    channelLogin: String,
    streamState: StreamUiState,
    viewers: Int,
    uptime: String,
    muted: Boolean,
    paused: Boolean,
    following: Boolean,
    landscape: Boolean,
    chatOpen: Boolean,
    controlsVisible: Boolean,
    showChrome: Boolean,
    onToggleControls: () -> Unit,
    onToggleMute: () -> Unit,
    onTogglePause: () -> Unit,
    onToggleFollow: () -> Unit,
    onToggleChat: () -> Unit,
    onSettings: () -> Unit,
    onRetry: () -> Unit,
    playbackError: Boolean,
    onRetryPlayback: () -> Unit,
    onMinimize: () -> Unit,
    onFullscreen: () -> Unit,
    onPip: () -> Unit,
    onBoundsChanged: (android.graphics.Rect) -> Unit,
    videoContent: @Composable () -> Unit,
    showVideoWhileLoading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(Color.Black)
            .onGloballyPositioned { coords ->
                val r = coords.boundsInWindow()
                onBoundsChanged(
                    android.graphics.Rect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt()),
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggleControls,
            ),
    ) {
        when {
            // A hard ExoPlayer failure (e.g. network dropped mid-stream) — black window + retry,
            // over whatever frozen frame the player was showing. Takes precedence over the video.
            playbackError -> ErrorState(stringResource(R.string.playback_error), onRetryPlayback)
            streamState is StreamUiState.Error -> ErrorState(stringResource(streamState.messageRes), onRetry)
            // Already-running stream (expanded from mini-player) — show the live surface at once.
            showVideoWhileLoading -> videoContent()
            streamState is StreamUiState.Success -> videoContent()
            else -> WaitingState()
        }

        if (showChrome && controlsVisible && !playbackError && streamState !is StreamUiState.Error) {
            PlayerControls(
                channelLogin = channelLogin,
                viewers = viewers,
                uptime = uptime,
                muted = muted,
                paused = paused,
                following = following,
                landscape = landscape,
                chatOpen = chatOpen,
                onToggleMute = onToggleMute,
                onTogglePause = onTogglePause,
                onToggleFollow = onToggleFollow,
                onToggleChat = onToggleChat,
                onSettings = onSettings,
                onMinimize = onMinimize,
                onFullscreen = onFullscreen,
                onPip = onPip,
            )
        }
    }
}

/** Xtra's fading controller overlay: 40% scrim + corner clusters + centered live info. */
@Composable
private fun PlayerControls(
    channelLogin: String,
    viewers: Int,
    uptime: String,
    muted: Boolean,
    paused: Boolean,
    following: Boolean,
    landscape: Boolean,
    chatOpen: Boolean,
    onToggleMute: () -> Unit,
    onTogglePause: () -> Unit,
    onToggleFollow: () -> Unit,
    onToggleChat: () -> Unit,
    onSettings: () -> Unit,
    onMinimize: () -> Unit,
    onFullscreen: () -> Unit,
    onPip: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(PlayerScrim)) {
        // Top-left: minimize (→ in-app floating mini-player, Xtra style) + channel name
        Row(
            modifier = Modifier.align(Alignment.TopStart).padding(start = 6.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CtrlIcon(Icons.Filled.ExpandMore, stringResource(R.string.minimize), onMinimize)
            Text(
                text = channelLogin,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        // Top-right: follow, settings (quality), overflow
        Row(modifier = Modifier.align(Alignment.TopEnd).padding(end = 6.dp, top = 4.dp)) {
            CtrlIcon(
                if (following) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                stringResource(R.string.follow),
                onToggleFollow,
            )
            CtrlIcon(Icons.Filled.Settings, stringResource(R.string.settings), onSettings)
        }

        // Center: play/pause
        CtrlIcon(
            imageVector = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
            contentDescription = if (paused) stringResource(R.string.play) else stringResource(R.string.pause),
            onClick = onTogglePause,
            modifier = Modifier.align(Alignment.Center).size(Sizing.xxl),
            iconSize = IconSize.xxl,
        )

        // Center-bottom: red-dot uptime + viewers (live, no seekbar)
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Circle,
                contentDescription = null,
                tint = XtraLiveRed,
                modifier = Modifier.size(IconSize.xxs),
            )
            Text("  $uptime", color = Color.White, style = MaterialTheme.typography.titleSmall)
            Icon(
                Icons.Filled.Person,
                contentDescription = stringResource(R.string.viewers),
                tint = Color.White,
                modifier = Modifier.padding(start = 12.dp).size(IconSize.sm),
            )
            Text(" ${formatCount(viewers)}", color = Color.White, style = MaterialTheme.typography.titleSmall)
        }

        // Bottom-left: mute
        Row(modifier = Modifier.align(Alignment.BottomStart).padding(start = 6.dp, bottom = 4.dp)) {
            CtrlIcon(
                if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                stringResource(R.string.mute),
                onToggleMute,
            )
        }

        // Bottom-right: chat toggle (landscape only), PiP, fullscreen
        Row(modifier = Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 4.dp)) {
            if (landscape) {
                CtrlIcon(
                    if (chatOpen) Icons.AutoMirrored.Filled.SpeakerNotes else Icons.Filled.SpeakerNotesOff,
                    stringResource(R.string.toggle_chat),
                    onToggleChat,
                )
            }
            CtrlIcon(Icons.Filled.PictureInPictureAlt, stringResource(R.string.picture_in_picture), onPip)
            CtrlIcon(
                if (landscape) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                stringResource(R.string.fullscreen),
                onFullscreen,
            )
        }
    }
}

@Composable
private fun CtrlIcon(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(Sizing.lg),
    iconSize: Dp = IconSize.md,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun ChatPanel(
    messages: List<ChatMessage>,
    errored: Boolean,
    input: TextFieldValue,
    showEmotes: Boolean,
    onRetry: () -> Unit,
    onValueChange: (TextFieldValue) -> Unit,
    onToggleEmotes: () -> Unit,
    onEmoteSelected: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        ChatList(messages = messages, modifier = Modifier.fillMaxWidth().weight(1f))
        // A network drop leaves the chat socket dead; offer a manual reconnect on a black bar.
        if (errored) {
            ChatDisconnectedBar(onRetry = onRetry)
        }
        if (showEmotes) {
            EmoteGrid(onEmoteSelected = onEmoteSelected)
        }
        XtraChatInput(
            value = input,
            onValueChange = onValueChange,
            onToggleEmotes = onToggleEmotes,
            onSend = onSend,
        )
    }
}

@Composable
private fun ChatList(messages: List<ChatMessage>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    LazyColumn(state = listState, modifier = modifier) {
        items(items = messages, key = { it.id }) { message ->
            ChatMessageRow(
                message = message,
                isSystem = message.badges.contains(StreamViewModel.SYSTEM_BADGE),
            )
        }
    }
}

/** Xtra's chat input row: text field + emote-picker button (tag_faces) + send. */
@Composable
private fun XtraChatInput(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onToggleEmotes: () -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { inner ->
                Box {
                    inner()
                    if (value.text.isEmpty()) {
                        Text(
                            stringResource(R.string.send_a_message),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
        )
        IconButton(onClick = onToggleEmotes) {
            Icon(
                Icons.Filled.TagFaces,
                contentDescription = stringResource(R.string.emotes),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (value.text.isNotBlank()) {
            IconButton(onClick = onSend) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.send),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun WaitingState() {
    Box(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.waiting_for_host),
            color = Color.White,
            modifier = Modifier.align(Alignment.Center),
        )
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = message, color = Color.White)
        Spacer(Modifier.size(12.dp))
        RetryButton(onClick = onRetry)
    }
}

/** A black bar shown below the chat list when the socket dropped, with a reconnect button. */
@Composable
private fun ChatDisconnectedBar(onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.chat_disconnected),
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
        )
        RetryButton(onClick = onRetry)
    }
}

/**
 * Shared Retry action for network-error states — a black button with a white outline + refresh icon.
 * Used by both the playback [ErrorState] and the chat [ChatDisconnectedBar]; they live in separate
 * panes, so both can be present at once — any test matching "Retry" must scope to one pane.
 */
@Composable
private fun RetryButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Black,
            contentColor = Color.White,
        ),
        border = BorderStroke(1.dp, Color.White),
        modifier = modifier,
    ) {
        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(IconSize.sm))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.retry))
    }
}

/** The player settings gear opens straight to Xtra's video-quality (resolution) picker, only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSettingsSheet(
    quality: PlayerController.VideoQuality,
    onQualitySelected: (PlayerController.VideoQuality) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                text = stringResource(R.string.video_quality),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            PlayerController.VideoQuality.entries.forEach { option ->
                QualityRow(
                    label = stringResource(option.labelRes),
                    selected = option == quality,
                    onClick = {
                        onQualitySelected(option)
                        onDismiss()
                    },
                )
            }
        }
    }
}

@Composable
private fun QualityRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (selected) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp).size(IconSize.sm),
        )
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun formatCount(count: Int): String = when {
    count >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", count / 1_000_000f)
    count >= 1_000 -> String.format(java.util.Locale.US, "%.1fK", count / 1_000f)
    else -> count.toString()
}

private fun Activity.toggleFullscreen(currentlyLandscape: Boolean) {
    requestedOrientation = if (currentlyLandscape) {
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    } else {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }
}

// Requires API 26 (PiP); every caller returns early below O, which lint can't see across the call.
@RequiresApi(Build.VERSION_CODES.O)
private fun Activity.pipParams(sourceHint: android.graphics.Rect?, autoEnter: Boolean): PictureInPictureParams {
    val builder = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9))
    if (sourceHint != null && !sourceHint.isEmpty) builder.setSourceRectHint(sourceHint)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) builder.setAutoEnterEnabled(autoEnter)
    return builder.build()
}

private fun Activity.enterPip(sourceHint: android.graphics.Rect? = null) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
    runCatching { enterPictureInPictureMode(pipParams(sourceHint, autoEnter = false)) }
}

/** Keeps the PiP params current (source-rect hint + auto-enter on 12+) so leaving the app is smooth. */
private fun Activity.updatePipParams(sourceHint: android.graphics.Rect?, autoEnter: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
    runCatching { setPictureInPictureParams(pipParams(sourceHint, autoEnter)) }
}

@Preview
@Composable
private fun StreamScreenPreview() {
    TwitchTheme {
        StreamScreen(
            channelLogin = "monstercat",
            viewers = 10_700,
            streamState = StreamUiState.Loading,
            chatState = ChatUiState(
                connected = true,
                messages = listOf(
                    ChatMessage("1", "Alice", "#FF0000", "hello world"),
                    ChatMessage("2", "Bob", null, "waves", isAction = true),
                ),
            ),
            onRetry = {},
        )
    }
}

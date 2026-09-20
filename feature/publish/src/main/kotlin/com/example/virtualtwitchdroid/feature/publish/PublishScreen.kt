package com.example.virtualtwitchdroid.feature.publish

import android.Manifest
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PausePresentation
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.virtualtwitchdroid.core.common.media.VoiceState
import com.example.virtualtwitchdroid.core.designsystem.component.ChatMessageRow
import com.example.virtualtwitchdroid.core.designsystem.component.LocalMiniWindowAnchor
import com.example.virtualtwitchdroid.core.designsystem.theme.IconSize
import com.example.virtualtwitchdroid.core.designsystem.theme.LiveRed
import com.example.virtualtwitchdroid.core.designsystem.theme.Sizing
import com.example.virtualtwitchdroid.core.designsystem.theme.Spacing
import com.example.virtualtwitchdroid.core.model.ChatMessage

private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

/**
 * The Go Live (broadcast) screen. It is **locked to portrait** — no landscape layout — so the camera
 * preview only ever has one orientation to get right. The camera fills the top; the stream-key field
 * sits below. Minimize / PiP are overlaid on the preview. (The floating mini-player, by contrast,
 * DOES follow device rotation once minimized — see [PublishMiniPlayer].)
 */
@Composable
internal fun PublishScreen(
    controller: PublishController,
    onBackClick: () -> Unit,
    onMinimize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val configuration = LocalConfiguration.current

    // System-PiP mode: entering PiP is a configuration change, so this recomputes and the branch
    // below hides all chrome, leaving only the camera in the PiP window.
    val inPip = remember(configuration) {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity?.isInPictureInPictureMode == true
    }

    // The top header (with its back arrow) is gone, so keep system-back navigating away from Go Live.
    BackHandler(enabled = !inPip) { onBackClick() }

    val streamer by controller.streamer.collectAsStateWithLifecycle()
    val uiState by controller.uiState.collectAsStateWithLifecycle()
    val quality by controller.quality.collectAsStateWithLifecycle()
    val micMuted by controller.micMuted.collectAsStateWithLifecycle()
    val vtuberMode by controller.vtuberMode.collectAsStateWithLifecycle()
    val zundamonVoice by controller.zundamonVoice.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }

    var hasPermissions by remember {
        mutableStateOf(
            REQUIRED_PERMISSIONS.all {
                context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
            },
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        hasPermissions = result.values.all { it }
        if (hasPermissions) controller.initialize()
    }

    // While the publish screen is shown: lock the app to PORTRAIT (the fullscreen camera screen has no
    // landscape layout), keep the app-scoped camera, and PAN (not resize) for the keyboard so the
    // camera surface isn't disturbed on IME open/close. Everything is restored on leave — in
    // particular the lock is released so the floating mini can rotate with the device.
    val miniWindowAnchor = LocalMiniWindowAnchor.current
    DisposableEffect(Unit) {
        controller.setScreenActive(true)
        val previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val window = activity?.window
        val previousSoftInput = window?.attributes?.softInputMode
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        // We PAN for the IME here; tell the floating mini so it doesn't add its own inset lift on top.
        miniWindowAnchor.windowPansForIme = true
        onDispose {
            controller.setScreenActive(false)
            miniWindowAnchor.windowPansForIme = false
            if (activity != null && previousOrientation != null) {
                activity.requestedOrientation = previousOrientation
            }
            if (window != null && previousSoftInput != null) window.setSoftInputMode(previousSoftInput)
        }
    }
    LaunchedEffect(hasPermissions, streamer) {
        if (hasPermissions && streamer == null) controller.initialize()
    }

    // Empty by default so the field shows its "Twitch stream key" hint (like the Chat username field);
    // the user pastes their own key before going live.
    var streamKey by rememberSaveable { mutableStateOf("") }
    // The channel whose live chat to receive while broadcasting (entered left of the stream key).
    var chatUsername by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(chatUsername) { controller.setChatChannel(chatUsername) }
    val chatMessages by controller.chatMessages.collectAsStateWithLifecycle()
    val isLive = uiState is PublishUiState.Live
    val isConnecting = uiState is PublishUiState.Connecting
    val isReconnecting = uiState is PublishUiState.Reconnecting

    val currentStreamer = streamer

    // The chat-username + stream-key section (+ any error), shown below the camera while off-air.
    val keyPane: @Composable (Modifier) -> Unit = { paneMod ->
        Column(paneMod) {
            (uiState as? PublishUiState.Error)?.let { err ->
                Text(
                    stringResource(err.messageRes),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = Spacing.xs),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = chatUsername,
                    onValueChange = { chatUsername = it },
                    singleLine = true,
                    enabled = !isLive && !isConnecting,
                    label = { Text(stringResource(R.string.chat_username)) },
                    modifier = Modifier.weight(0.42f),
                )
                OutlinedTextField(
                    value = streamKey,
                    onValueChange = { streamKey = it },
                    singleLine = true,
                    enabled = !isLive && !isConnecting,
                    label = { Text(stringResource(R.string.twitch_stream_key)) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.weight(0.58f),
                )
            }
        }
    }

    // The camera pane (preview + minimize/PiP overlays + live overlay). The fullscreen and the mini
    // share the SAME RootEncoder [CameraPreview] (one always-running GL pipeline); RootEncoder gets the
    // camera scale/orientation right, so there is no hand-rolled transform. The screen is
    // portrait-locked so the fullscreen preview never rotates.
    val paneState = cameraPaneState(
        hasPermissions = hasPermissions,
        hasStreamer = currentStreamer != null,
        isError = uiState is PublishUiState.Error,
    )
    val cameraPane: @Composable (Modifier) -> Unit = { paneMod ->
        Box(paneMod.then(if (inPip) Modifier else Modifier.clip(MaterialTheme.shapes.small))) {
            if (paneState == CameraPaneState.Preview && currentStreamer != null) {
                CameraPreview(controller = controller, streamer = currentStreamer, modifier = Modifier.fillMaxSize())
                if (!inPip) {
                    OverlayCircleButton(
                        icon = Icons.Filled.ExpandMore,
                        contentDescription = stringResource(R.string.minimize),
                        onClick = onMinimize,
                        modifier = Modifier.align(Alignment.TopStart),
                    )
                    // Zundamon voice status (state, recognised text, lag) + the mandatory voice credit, under
                    // the camera-flip button while the voice is on.
                    if (zundamonVoice) {
                        ZundamonVoiceOverlay(
                            controller = controller,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = Sizing.xl + Spacing.lg, start = Sizing.xxl, end = Sizing.xxl)
                                .fillMaxWidth(),
                        )
                    }
                    // Top-center: flip front/back camera — a little larger than the other overlay
                    // buttons. Shown only when the device actually has both cameras (else the flip
                    // would kill the preview). In VTuber mode it flips the face-tracking lens instead.
                    if (controller.hasMultipleCameras) {
                        OverlayCircleButton(
                            icon = Icons.Filled.Cameraswitch,
                            contentDescription = stringResource(R.string.switch_camera),
                            onClick = controller::switchCamera,
                            modifier = Modifier.align(Alignment.TopCenter),
                            size = Sizing.xl,
                            iconSize = IconSize.lg,
                        )
                    }
                    // Received chat (from the entered username's channel) — a transparent overlay over
                    // the camera's LOWER HALF, sitting on top of the broadcast button (whose measured
                    // height it pads for). Only while live.
                    var broadcastHeightPx by remember { mutableIntStateOf(0) }
                    if (isLive) {
                        ChatReceiveOverlay(
                            messages = chatMessages,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(
                                    start = Spacing.sm,
                                    end = Spacing.sm,
                                    bottom = with(LocalDensity.current) { broadcastHeightPx.toDp() } + Spacing.sm,
                                ),
                        )
                    }
                    PublishOverlayControls(
                        isLive = isLive,
                        isConnecting = isConnecting,
                        isReconnecting = isReconnecting,
                        onToggleStream = { controller.toggleStream(streamKey) },
                        onBroadcastSize = { broadcastHeightPx = it },
                        // Quality (resolution) settings, like the watch player's gear. Hidden while
                        // live — the encode resolution can't change mid-broadcast (set it beforehand).
                        settingsVisible = !isLive && !isConnecting && !isReconnecting,
                        onSettings = { showSettings = true },
                        micMuted = micMuted,
                        onToggleMic = controller::toggleMic,
                        avatarSupported = controller.avatarSupported,
                        vtuberMode = vtuberMode,
                        onToggleVtuber = { controller.setVtuberMode(!vtuberMode) },
                        voiceSupported = controller.voiceSupported,
                        zundamonVoice = zundamonVoice,
                        onToggleVoice = { controller.setZundamonVoice(!zundamonVoice) },
                        onPip = { activity?.enterCameraPip(cameraPipRational()) },
                    )
                }
            } else if (!inPip) {
                when (paneState) {
                    CameraPaneState.NeedPermission -> PermissionPrompt(
                        onGrant = { permissionLauncher.launch(REQUIRED_PERMISSIONS) },
                        modifier = Modifier.align(Alignment.Center),
                    )
                    // Camera failed to open (permission granted) — show the reason + a Retry, not the
                    // misleading permission prompt.
                    CameraPaneState.Error -> CameraErrorState(
                        message = stringResource(
                            (uiState as? PublishUiState.Error)?.messageRes ?: R.string.camera_error,
                        ),
                        onRetry = { controller.initialize() },
                        modifier = Modifier.align(Alignment.Center),
                    )
                    // Opening the camera.
                    CameraPaneState.Loading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )
                    CameraPaneState.Preview -> Unit // handled above
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(if (inPip) Color.Black else MaterialTheme.colorScheme.background)
            // Top (status-bar) inset only — the tab bar below in the host already
            // consumes the bottom nav inset, so the below-input gap stays equal to
            // the above-input (camera) gap instead of gaining a redundant inset.
            .then(if (inPip) Modifier else Modifier.statusBarsPadding()),
    ) {
        if (inPip) {
            // System PiP: camera fills the whole window, no chrome.
            cameraPane(Modifier.weight(1f).fillMaxWidth())
        } else {
            // Portrait (the fullscreen screen is portrait-locked): camera above, stream-key below.
            // The camera's TOP margin and the key section's BOTTOM margin match (both Spacing.md) so the
            // content sits symmetrically between the status bar and the bottom nav.
            cameraPane(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = Spacing.sm, start = Spacing.sm, end = Spacing.sm),
            )
            // Hide the key/username section while broadcasting (set it before going live).
            if (!isLive) {
                keyPane(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = Spacing.md, end = Spacing.md, top = Spacing.sm, bottom = Spacing.sm),
                )
            }
        }
    }

    if (showSettings) {
        PublishQualitySheet(
            quality = quality,
            onQualitySelected = {
                controller.setQuality(it)
                showSettings = false
            },
            onDismiss = { showSettings = false },
        )
    }
}

/** Bottom sheet listing the publish encode resolutions (mirrors the watch player's quality menu). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PublishQualitySheet(
    quality: PublishController.VideoQuality,
    onQualitySelected: (PublishController.VideoQuality) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                text = stringResource(R.string.camera_quality),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            PublishController.VideoQuality.entries.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onQualitySelected(option) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (option == quality) {
                            Icons.Filled.RadioButtonChecked
                        } else {
                            Icons.Filled.RadioButtonUnchecked
                        },
                        contentDescription = null,
                        tint = if (option == quality) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(end = 12.dp).size(IconSize.sm),
                    )
                    Text(
                        stringResource(option.labelRes),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
internal fun PermissionPrompt(onGrant: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.permission_rationale),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Button(onClick = onGrant) { Text(stringResource(R.string.grant_access)) }
    }
}

/** Shown in the camera pane when the camera can't be opened — the reason + a Retry (re-initialize). */
@Composable
internal fun CameraErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Filled.VideocamOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(IconSize.xl),
        )
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Text(stringResource(R.string.retry))
        }
    }
}

/**
 * A transparent, bottom-anchored list of received chat messages, overlaid on the camera preview while
 * live. Fills half the camera height; newest pinned to the bottom. Follows new messages only while the
 * reader is already at the bottom, so scrolling up to read old chat is never interrupted. Reuses the
 * shared [ChatMessageRow]; no background — the camera shows through.
 */
@Composable
internal fun ChatReceiveOverlay(messages: List<ChatMessage>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    // Key on the newest id (not messages.size): once the rolling buffer saturates at its cap the size
    // stops changing, so a size key would stop following new messages while the count stays constant.
    LaunchedEffect(messages.lastOrNull()?.id) {
        if (messages.isEmpty()) return@LaunchedEffect
        // Follow the newest only when the reader is already at the bottom; if they scrolled up to read
        // old chat, leave them there instead of yanking the list down on every incoming message.
        val info = listState.layoutInfo
        val lastVisible = info.visibleItemsInfo.lastOrNull()
        val atBottom = lastVisible == null || lastVisible.index >= info.totalItemsCount - 2
        if (atBottom) listState.animateScrollToItem(messages.lastIndex)
    }
    LazyColumn(
        state = listState,
        // The section is half the camera height (the overlay owns the fraction); newest at the bottom.
        modifier = Modifier.fillMaxHeight(0.5f).then(modifier),
        verticalArrangement = Arrangement.Bottom,
    ) {
        items(items = messages, key = { it.id }) { message ->
            ChatMessageRow(message = message)
        }
    }
}

/**
 * The broadcast button's fill color, as a pure (unit-testable) function of the broadcast state:
 * [LiveRed] while on-air (live OR reconnecting), otherwise the brand [brandPrimary] (purple). This is
 * the "purple before live, red on live" status rule.
 */
internal fun broadcastContainerColor(isLive: Boolean, isReconnecting: Boolean, brandPrimary: Color): Color =
    if (isLive || isReconnecting) LiveRed else brandPrimary

/**
 * The broadcast toggle overlaid at the camera's bottom-center. Modern live-status button: purple
 * (the brand primary) before going live, red ([LiveRed]) while on-air / reconnecting.
 */
@Composable
internal fun BroadcastButton(
    isLive: Boolean,
    isConnecting: Boolean,
    isReconnecting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onAir = isLive || isReconnecting
    Button(
        // Disabled only during the initial connect; while reconnecting it stays enabled so the user
        // can cancel the broadcast.
        onClick = { if (!isConnecting) onClick() },
        enabled = !isConnecting,
        colors = ButtonDefaults.buttonColors(
            containerColor = broadcastContainerColor(isLive, isReconnecting, MaterialTheme.colorScheme.primary),
        ),
        modifier = modifier,
    ) {
        Icon(
            imageVector = if (onAir) Icons.Filled.PausePresentation else Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = Color.White,
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = when {
                isReconnecting -> stringResource(R.string.reconnecting)
                isConnecting -> stringResource(R.string.connecting)
                isLive -> stringResource(R.string.go_backstage)
                else -> stringResource(R.string.go_live)
            },
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Padding around every [OverlayCircleButton] (also its distance from the pane's edge). */
private val OVERLAY_BUTTON_PADDING = Spacing.md

/**
 * The camera pane's overlay controls, placed in the pane's [BoxScope]:
 * - **top-right**: the avatar (VTuber mode) toggle (when [avatarSupported]) with the avatar-voice (Zundamon)
 *   toggle directly under it (when [vtuberMode] and [voiceSupported]);
 * - **bottom-left**: mic mute stacked on top of the quality settings gear (when [settingsVisible]);
 * - **bottom-center**: the [BroadcastButton]; **bottom-right**: Picture-in-Picture.
 * [onBroadcastSize] reports the height (px) of the broadcast button's slot — its touch target plus its
 * bottom margin — so overlays can sit above it.
 */
@Composable
internal fun BoxScope.PublishOverlayControls(
    isLive: Boolean,
    isConnecting: Boolean,
    isReconnecting: Boolean,
    onToggleStream: () -> Unit,
    settingsVisible: Boolean,
    onSettings: () -> Unit,
    micMuted: Boolean,
    onToggleMic: () -> Unit,
    avatarSupported: Boolean,
    vtuberMode: Boolean,
    onToggleVtuber: () -> Unit,
    voiceSupported: Boolean,
    zundamonVoice: Boolean,
    onToggleVoice: () -> Unit,
    onPip: () -> Unit,
    onBroadcastSize: (heightPx: Int) -> Unit = {},
) {
    Column(Modifier.align(Alignment.TopEnd), horizontalAlignment = Alignment.End) {
        // VTuber mode: broadcast the face-tracked avatar instead of the camera. Works live too
        // (RootEncoder swaps the video source on the running pipeline). Only on devices that can
        // run the avatar.
        if (avatarSupported) {
            OverlayCircleButton(
                icon = Icons.Filled.Face,
                contentDescription = stringResource(
                    if (vtuberMode) R.string.vtuber_mode_off else R.string.vtuber_mode_on,
                ),
                onClick = onToggleVtuber,
                tint = if (vtuberMode) MaterialTheme.colorScheme.primary else Color.White,
            )
        }
        // Zundamon voice: the broadcast audio becomes VOICEVOX ずんだもん speaking the streamer's
        // recognised Japanese (the microphone never reaches the stream). VTuber mode only.
        if (vtuberMode && voiceSupported) {
            OverlayCircleButton(
                icon = Icons.Filled.RecordVoiceOver,
                contentDescription = stringResource(
                    if (zundamonVoice) R.string.zundamon_voice_off else R.string.zundamon_voice_on,
                ),
                onClick = onToggleVoice,
                tint = if (zundamonVoice) MaterialTheme.colorScheme.primary else Color.White,
            )
        }
    }
    Column(Modifier.align(Alignment.BottomStart), horizontalAlignment = Alignment.Start) {
        OverlayCircleButton(
            icon = if (micMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
            contentDescription = if (micMuted) {
                stringResource(R.string.unmute_microphone)
            } else {
                stringResource(R.string.mute_microphone)
            },
            onClick = onToggleMic,
        )
        if (settingsVisible) {
            OverlayCircleButton(
                icon = Icons.Filled.Settings,
                contentDescription = stringResource(R.string.video_quality),
                onClick = onSettings,
            )
        }
    }
    // The broadcast toggle — purple before live, red while live. Live status reads off it, so no
    // separate live-info bar competes with it in the bottom band. Kept clear of the two side columns
    // (each one circle-button wide) so a long label at a large font scale wraps instead of overlapping.
    BroadcastButton(
        isLive = isLive,
        isConnecting = isConnecting,
        isReconnecting = isReconnecting,
        onClick = onToggleStream,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .onSizeChanged { onBroadcastSize(it.height) }
            .padding(start = Sizing.xxl, end = Sizing.xxl, bottom = Spacing.md),
    )
    OverlayCircleButton(
        icon = Icons.Filled.PictureInPictureAlt,
        contentDescription = stringResource(R.string.picture_in_picture),
        onClick = onPip,
        modifier = Modifier.align(Alignment.BottomEnd),
    )
}

/** The Zundamon voice's live status line: pipeline state + lag, the last recognised text, and the credit. */
@Composable
private fun ZundamonVoiceOverlay(controller: PublishController, modifier: Modifier = Modifier) {
    val state by controller.voice.state.collectAsStateWithLifecycle()
    val lastText by controller.voice.lastText.collectAsStateWithLifecycle()
    val lag by controller.voice.lagSeconds.collectAsStateWithLifecycle()
    val error by controller.voice.error.collectAsStateWithLifecycle()
    val stateRes = when (state) {
        VoiceState.IDLE -> R.string.voice_state_idle
        VoiceState.PREPARING -> R.string.voice_state_preparing
        VoiceState.LISTENING -> R.string.voice_state_listening
        VoiceState.THINKING -> R.string.voice_state_thinking
        VoiceState.SPEAKING -> R.string.voice_state_speaking
        VoiceState.ERROR -> R.string.voice_state_error
    }
    Column(modifier.padding(horizontal = Spacing.lg), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(stateRes, lag),
            style = MaterialTheme.typography.labelMedium,
            color = if (state == VoiceState.ERROR) MaterialTheme.colorScheme.error else Color.White,
        )
        (error ?: lastText)?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = stringResource(R.string.voice_credit),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}

/**
 * A circular scrim icon button overlaid on the camera preview (minimize, the bottom control row). [tint] marks
 * an active toggle (e.g. VTuber mode on) in the brand colour.
 */
@Composable
private fun OverlayCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = Sizing.lg,
    iconSize: Dp = IconSize.md,
    tint: Color = Color.White,
) {
    Box(
        modifier = modifier
            .padding(OVERLAY_BUTTON_PADDING)
            .size(size)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClick),
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.align(Alignment.Center).size(iconSize),
        )
    }
}

/** Enters system Picture-in-Picture showing the camera, sized to its [aspect] (works live or not). */
private fun Activity.enterCameraPip(aspect: Rational) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
    runCatching {
        enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(aspect).build())
    }
}

package com.example.virtualtwitchdroid.feature.avatar

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.virtualtwitchdroid.core.common.media.SpeechStreamSource
import com.example.virtualtwitchdroid.core.common.media.VoiceState
import com.example.virtualtwitchdroid.core.designsystem.theme.Spacing
import com.example.virtualtwitchdroid.feature.avatar.render.AvatarSurface
import com.example.virtualtwitchdroid.feature.avatar.rig.VrmExpression
import com.example.virtualtwitchdroid.feature.avatar.tracking.CameraTrackingEffect
import com.example.virtualtwitchdroid.feature.avatar.tracking.TrackerStatus

/**
 * The avatar tab — Phase 0: a live face-tracking HUD (tracker health, fps / inference latency, detected
 * flag, head yaw/pitch/roll, strongest VRM expressions) driven by the front or back camera. It exists
 * to prove the tracking pipeline and its performance on-device before any rendering; the avatar itself
 * lands in Phase 1 (see `scripts/vtuber-avatar-plan.md`).
 */
@Composable
internal fun AvatarScreen(modifier: Modifier = Modifier, viewModel: AvatarViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var hasCamera by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCamera = it }

    if (!hasCamera) {
        CameraPermissionPrompt(onGrant = { launcher.launch(Manifest.permission.CAMERA) }, modifier = modifier)
        return
    }
    val frontCamera by viewModel.frontCamera.collectAsStateWithLifecycle()
    CameraTrackingEffect(viewModel.tracker, frontCamera = frontCamera)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        // The rendered avatar, driven by the smoothed rig (Phase 1) — and by the Zundamon voice's mouth track.
        AvatarSurface(
            rig = uiState.rig,
            modifier = Modifier.fillMaxWidth().weight(AVATAR_WEIGHT),
            mouthTrack = viewModel.mouthTrack,
        )
        Text(
            text = stringResource(R.string.avatar_model_credit), // required by the bundled model's license
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        )
        viewModel.voice?.takeIf { it.supported }?.let { ZundamonVoiceCard(it) }
        Box(Modifier.fillMaxWidth().weight(HUD_WEIGHT)) {
            AvatarHud(state = uiState, topExpressions = HUD_EXPRESSIONS, showTitle = false)
            Button(
                onClick = viewModel::toggleCamera,
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.lg),
            ) {
                Text(
                    stringResource(
                        if (frontCamera) R.string.avatar_use_back_camera else R.string.avatar_use_front_camera,
                    ),
                )
            }
        }
    }
}

/**
 * Rehearsal controls for the Zundamon voice: listen (Japanese speech → recognised text → Zundamon), a text
 * field to make Zundamon say something directly, the pipeline state/lag, and the mandatory voice credit.
 * The RECORD_AUDIO permission is requested on the first Listen tap. Internal so the layout can be exercised
 * with a fake voice.
 */
@Composable
internal fun ZundamonVoiceCard(voice: SpeechStreamSource) {
    val context = LocalContext.current
    val state by voice.state.collectAsStateWithLifecycle()
    val lastText by voice.lastText.collectAsStateWithLifecycle()
    val lag by voice.lagSeconds.collectAsStateWithLifecycle()
    val error by voice.error.collectAsStateWithLifecycle()
    val broadcasting by voice.broadcasting.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) voice.start()
    }
    val listening = state == VoiceState.LISTENING ||
        state == VoiceState.THINKING ||
        state == VoiceState.SPEAKING ||
        state == VoiceState.PREPARING
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Button(
                // While Go Live owns the voice (broadcast sink attached) the rehearsal controls yield to it.
                enabled = !broadcasting,
                onClick = {
                    if (listening) {
                        voice.stop()
                    } else if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        voice.start()
                    } else {
                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
            ) {
                Text(stringResource(if (listening) R.string.voice_stop else R.string.voice_listen))
            }
            Text(
                text = if (broadcasting) {
                    stringResource(
                        R.string.voice_in_use_by_go_live,
                    )
                } else {
                    stringResource(voiceStateLabel(state), lag)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (state ==
                    VoiceState.ERROR
                ) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.voice_say_hint)) },
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { voice.say(text) }, enabled = text.isNotBlank() && !broadcasting) {
                Text(stringResource(R.string.voice_say))
            }
        }
        (error ?: lastText)?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
        // The voice credit is a licence requirement — always visible, never displaced by the recognised text.
        Text(
            text = stringResource(R.string.voice_credit),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@StringRes
private fun voiceStateLabel(state: VoiceState): Int = when (state) {
    VoiceState.IDLE -> R.string.voice_state_idle
    VoiceState.PREPARING -> R.string.voice_state_preparing
    VoiceState.LISTENING -> R.string.voice_state_listening
    VoiceState.THINKING -> R.string.voice_state_thinking
    VoiceState.SPEAKING -> R.string.voice_state_speaking
    VoiceState.ERROR -> R.string.voice_state_error
}

/** Stateless HUD so the layout can be exercised with a fake [state] in an instrumented test. */
@Composable
internal fun AvatarHud(
    state: AvatarUiState,
    modifier: Modifier = Modifier,
    topExpressions: Int = 8,
    showTitle: Boolean = true,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (showTitle) {
            Text(
                text = stringResource(R.string.avatar_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        TrackerStatusLine(state.status)
        Text(
            text = if (state.rig.faceDetected) {
                stringResource(R.string.avatar_face_detected)
            } else {
                stringResource(R.string.avatar_face_none)
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (state.rig.faceDetected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Text(
            text = stringResource(R.string.avatar_stats, state.stats.fps, state.stats.inferenceMs),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.avatar_head, state.rig.head.yaw, state.rig.head.pitch, state.rig.head.roll),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.avatar_expressions),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.md),
        )
        state.rig.expressions.entries
            .sortedByDescending { it.value }
            .take(topExpressions)
            .forEach { (expression, weight) -> ExpressionBar(expression, weight) }
    }
}

@Composable
private fun TrackerStatusLine(status: TrackerStatus) {
    val (text, color) = when (status) {
        TrackerStatus.Starting -> stringResource(R.string.avatar_tracker_starting) to
            MaterialTheme.colorScheme.onSurfaceVariant
        is TrackerStatus.Running ->
            stringResource(R.string.avatar_tracker_running, status.delegate) to
                MaterialTheme.colorScheme.onSurfaceVariant
        is TrackerStatus.Failed -> when (status.reason) {
            TrackerStatus.FailureReason.INIT_FAILED -> stringResource(R.string.avatar_tracker_failed_init)
            TrackerStatus.FailureReason.RUNTIME_ERROR -> stringResource(R.string.avatar_tracker_failed_runtime)
        } to MaterialTheme.colorScheme.error
    }
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = color)
}

@Composable
private fun ExpressionBar(expression: VrmExpression, weight: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = expression.key,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(LABEL_WEIGHT),
        )
        LinearProgressIndicator(progress = { weight.coerceIn(0f, 1f) }, modifier = Modifier.weight(BAR_WEIGHT))
        Text(
            text = stringResource(R.string.avatar_weight, weight),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CameraPermissionPrompt(onGrant: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = stringResource(R.string.avatar_camera_rationale),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Button(onClick = onGrant) { Text(stringResource(R.string.avatar_grant_camera)) }
        }
    }
}

// Row weights for the expression rows: the longest preset name ("blinkRight") needs about a third.
private const val LABEL_WEIGHT = 0.35f
private const val BAR_WEIGHT = 0.65f

// Screen split: the avatar takes the upper part, the tracking HUD the rest.
private const val AVATAR_WEIGHT = 0.55f
private const val HUD_WEIGHT = 0.45f
private const val HUD_EXPRESSIONS = 4

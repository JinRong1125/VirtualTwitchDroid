package com.example.virtualtwitchdroid.feature.avatar

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.example.virtualtwitchdroid.core.designsystem.theme.TwitchTheme
import com.example.virtualtwitchdroid.feature.avatar.rig.FaceRig
import com.example.virtualtwitchdroid.feature.avatar.rig.HeadPose
import com.example.virtualtwitchdroid.feature.avatar.rig.VrmExpression
import com.example.virtualtwitchdroid.feature.avatar.tracking.TrackerStats
import com.example.virtualtwitchdroid.feature.avatar.tracking.TrackerStatus
import org.junit.Rule
import org.junit.Test

/**
 * The stateless HUD with fake tracking state — no camera or model needed. Expected text is resolved
 * through the same resources + device locale as `stringResource`, so number formatting can't diverge.
 */
class AvatarHudTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

    @Test
    fun detectedFace_showsStatusHeadPoseStatsAndStrongestExpressions() {
        val rig = FaceRig(
            expressions = mapOf(VrmExpression.AA to 0.8f, VrmExpression.BLINK_LEFT to 0.25f, VrmExpression.HAPPY to 0f),
            head = HeadPose(yaw = 12f, pitch = -5f, roll = 3f),
            faceDetected = true,
        )
        val state = AvatarUiState(rig, TrackerStats(fps = 29.7f, inferenceMs = 21), TrackerStatus.Running("GPU"))
        composeTestRule.setContent { TwitchTheme { AvatarHud(state) } }

        composeTestRule.onNodeWithText(string(R.string.avatar_face_detected)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.avatar_tracker_running, "GPU")).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.avatar_stats, 29.7f, 21L)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.avatar_head, 12f, -5f, 3f)).assertIsDisplayed()
        composeTestRule.onNodeWithText(VrmExpression.AA.key).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.avatar_weight, 0.8f)).assertIsDisplayed()
    }

    @Test
    fun noFace_andFailedTracker_showBothStates() {
        val state = AvatarUiState(status = TrackerStatus.Failed(TrackerStatus.FailureReason.INIT_FAILED))
        composeTestRule.setContent { TwitchTheme { AvatarHud(state) } }

        composeTestRule.onNodeWithText(string(R.string.avatar_face_none)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.avatar_tracker_failed_init)).assertIsDisplayed()
    }
}

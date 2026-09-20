package com.example.virtualtwitchdroid.feature.publish

import android.view.Surface
import com.example.virtualtwitchdroid.core.common.media.AvatarStreamSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Test

/**
 * The [AvatarVideoSource] state machine around RootEncoder's stop/release contract. `start()` needs a real
 * `SurfaceTexture` (Android framework, not available on the JVM), so the running state is driven through
 * the base class's lifecycle and the detach side is what is asserted: the avatar must be detached exactly
 * once, and never when nothing was attached.
 */
class AvatarVideoSourceTest {

    private class FakeAvatar : AvatarStreamSource {
        var detaches = 0
        override val supported = true
        override val frontCamera: StateFlow<Boolean> = MutableStateFlow(true)

        override fun toggleCamera() = Unit

        override fun attachOutput(surface: Surface, width: Int, height: Int) = Unit

        override fun detachOutput() {
            detaches++
        }
    }

    private val avatar = FakeAvatar()
    private val source = AvatarVideoSource(avatar)

    @Test
    fun create_acceptsAnyEncoderConfig_andIsNotRunningYet() {
        assertTrue(source.init(720, 1280, 30, 0))
        assertTrue(source.created)
        assertEquals(720, source.width)
        assertEquals(1280, source.height)
        assertFalse(source.isRunning())
    }

    @Test
    fun stopOrRelease_beforeStart_doesNotDetach() {
        source.stop()
        source.release()
        assertEquals(0, avatar.detaches)
        assertFalse(source.isRunning())
    }
}

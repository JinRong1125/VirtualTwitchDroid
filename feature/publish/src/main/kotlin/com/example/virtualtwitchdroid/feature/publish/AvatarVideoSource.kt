package com.example.virtualtwitchdroid.feature.publish

import android.graphics.SurfaceTexture
import android.view.Surface
import com.example.virtualtwitchdroid.core.common.media.AvatarStreamSource
import com.pedro.encoder.input.sources.video.VideoSource

/**
 * A RootEncoder [VideoSource] whose frames come from the app's [AvatarStreamSource] instead of a camera
 * (the "VTuber mode" of the Go Live screen). RootEncoder hands every source the `SurfaceTexture` its GL
 * pipeline samples from; we size its buffers to the encoder resolution, wrap it in a [Surface] and give
 * that to the avatar to render into — the rest of the pipeline (GL preview, encoder, RTMP) is untouched,
 * so switching camera↔avatar is a plain `changeVideoSource`, live or not.
 *
 * Orientation is left at the pipeline's defaults (the base [VideoSource] config): the avatar renders an
 * upright portrait frame at exactly the portrait encoder size, so no rotation/aspect fix-up is needed.
 */
internal class AvatarVideoSource(private val avatar: AvatarStreamSource) : VideoSource() {

    // Written on the publisher's worker (start/stop), read by RootEncoder on whichever thread starts the
    // sources (isRunning), so keep it visible across threads.
    @Volatile private var running = false

    override fun create(width: Int, height: Int, fps: Int, rotation: Int): Boolean = true

    override fun start(surfaceTexture: SurfaceTexture) {
        this.surfaceTexture = surfaceTexture
        surfaceTexture.setDefaultBufferSize(width, height)
        // The avatar owns this Surface from here on and releases it when the output is detached.
        avatar.attachOutput(Surface(surfaceTexture), width, height)
        running = true
    }

    override fun stop() {
        if (!running) return
        running = false
        avatar.detachOutput() // blocks until the avatar no longer draws into RootEncoder's buffer queue
    }

    override fun release() = stop()

    override fun isRunning(): Boolean = running
}

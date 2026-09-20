package com.example.virtualtwitchdroid.core.common.media

import android.view.Surface
import kotlinx.coroutines.flow.StateFlow

/**
 * A self-rendering avatar that can stand in for the camera as a broadcast's video: the publisher hands
 * it a [Surface] to draw into (at the encoder's size) and the avatar renders itself there — face-tracked
 * from the device camera — until the output is detached.
 *
 * This is the seam between the avatar and the publish features (features never depend on each other;
 * `:feature:avatar` implements it, `:feature:publish` consumes it, Hilt composes them at `:app`).
 *
 * Threading: both attach and detach may be called from any thread. [detachOutput] **blocks until the
 * avatar has stopped drawing** into the surface, because the caller reuses the underlying buffer queue
 * for the next video source right after (two producers on one queue is an error).
 */
interface AvatarStreamSource {
    /** False on devices below the avatar's minimum (see the implementation for the policy): hide the feature. */
    val supported: Boolean

    /** Which camera feeds the face tracker (true = front / selfie, mirrored). */
    val frontCamera: StateFlow<Boolean>

    fun toggleCamera()

    /**
     * Start rendering into [surface] at [width]×[height]. Ownership of [surface] passes to the avatar,
     * which releases it on [detachOutput]. A second attach replaces the first.
     */
    fun attachOutput(surface: Surface, width: Int, height: Int)

    /** Stop rendering and release the attached surface; returns once nothing draws into it any more. */
    fun detachOutput()
}

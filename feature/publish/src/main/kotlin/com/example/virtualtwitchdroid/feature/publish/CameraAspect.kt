package com.example.virtualtwitchdroid.feature.publish

import android.util.Rational

/**
 * The camera's base capture resolution (a landscape 1280×720 sensor size). [PublishController] passes
 * these to `prepareVideo` SWAPPED (720×1280) so the ENCODE/stream is PORTRAIT (9:16); here they stay
 * landscape as the reference for [cameraPipRational] (which swaps them to 9:16). The floating mini
 * uses the shared 16:9 window shape (same as the watch/stream mini) and cover-fills the camera into it.
 */
internal const val CAMERA_VIDEO_WIDTH = 1280
internal const val CAMERA_VIDEO_HEIGHT = 720

/**
 * The camera view's on-screen aspect ratio (width / height) = the PORTRAIT encode (720×1280 ⇒ 9:16,
 * i.e. < 1 = tall). The floating mini uses this so it shows the full upright camera undistorted, at
 * the same shape as the fullscreen preview (vs. the watch/stream mini's 16:9).
 */
internal fun cameraViewAspectRatio(): Float = CAMERA_VIDEO_HEIGHT.toFloat() / CAMERA_VIDEO_WIDTH

/**
 * The camera preview's aspect as an integer [Rational] for `PictureInPictureParams.setAspectRatio`,
 * so the system PiP window matches the camera shape (portrait 9:16).
 */
internal fun cameraPipRational(): Rational = Rational(CAMERA_VIDEO_HEIGHT, CAMERA_VIDEO_WIDTH)

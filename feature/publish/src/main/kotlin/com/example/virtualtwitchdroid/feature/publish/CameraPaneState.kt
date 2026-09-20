package com.example.virtualtwitchdroid.feature.publish

/** What the fullscreen camera pane should show, derived from permission + streamer + error state. */
internal enum class CameraPaneState { NeedPermission, Loading, Error, Preview }

/**
 * Decide the camera pane content. Order matters: no permission wins; otherwise a ready streamer shows
 * the preview; otherwise an error (the camera failed to open) offers Retry; otherwise we're still
 * initializing (Loading). Pure so it is unit-tested without Compose/Android.
 */
internal fun cameraPaneState(hasPermissions: Boolean, hasStreamer: Boolean, isError: Boolean): CameraPaneState = when {
    !hasPermissions -> CameraPaneState.NeedPermission
    hasStreamer -> CameraPaneState.Preview
    isError -> CameraPaneState.Error
    else -> CameraPaneState.Loading
}

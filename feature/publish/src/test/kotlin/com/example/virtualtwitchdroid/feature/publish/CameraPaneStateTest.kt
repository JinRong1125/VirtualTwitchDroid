package com.example.virtualtwitchdroid.feature.publish

import kotlin.test.assertEquals
import org.junit.Test

class CameraPaneStateTest {

    @Test
    fun noPermission_alwaysNeedsPermission() {
        assertEquals(
            CameraPaneState.NeedPermission,
            cameraPaneState(hasPermissions = false, hasStreamer = false, isError = false),
        )
        assertEquals(
            CameraPaneState.NeedPermission,
            cameraPaneState(hasPermissions = false, hasStreamer = true, isError = true),
        )
    }

    @Test
    fun readyStreamer_showsPreview_evenIfAnErrorLingers() {
        assertEquals(
            CameraPaneState.Preview,
            cameraPaneState(hasPermissions = true, hasStreamer = true, isError = false),
        )
        // A streaming/connection error must not hide a working camera preview.
        assertEquals(
            CameraPaneState.Preview,
            cameraPaneState(hasPermissions = true, hasStreamer = true, isError = true),
        )
    }

    @Test
    fun cameraOpenFailure_showsError() {
        assertEquals(CameraPaneState.Error, cameraPaneState(hasPermissions = true, hasStreamer = false, isError = true))
    }

    @Test
    fun initializing_showsLoading() {
        assertEquals(
            CameraPaneState.Loading,
            cameraPaneState(hasPermissions = true, hasStreamer = false, isError = false),
        )
    }
}

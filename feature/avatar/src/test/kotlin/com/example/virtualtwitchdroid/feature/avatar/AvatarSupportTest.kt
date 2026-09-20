package com.example.virtualtwitchdroid.feature.avatar

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/** The min-device policy: either Filament backend (GLES ≥ 3.0 or any Vulkan hardware level) is enough. */
class AvatarSupportTest {

    @Test
    fun gles30_isSupported_withoutVulkan() {
        assertTrue(isAvatarSupported(reqGlEsVersion = GLES_3_0, hasVulkanHardware = false))
    }

    @Test
    fun gles31AndAbove_isSupported() {
        assertTrue(isAvatarSupported(reqGlEsVersion = 0x30001, hasVulkanHardware = false))
        assertTrue(isAvatarSupported(reqGlEsVersion = 0x30002, hasVulkanHardware = false))
    }

    @Test
    fun gles2_isSupported_onlyWithVulkan() {
        assertFalse(isAvatarSupported(reqGlEsVersion = 0x20000, hasVulkanHardware = false))
        assertTrue(isAvatarSupported(reqGlEsVersion = 0x20000, hasVulkanHardware = true))
    }

    @Test
    fun unknownGles_isUnsupported_withoutVulkan() {
        assertFalse(isAvatarSupported(reqGlEsVersion = 0, hasVulkanHardware = false))
    }
}

package com.example.virtualtwitchdroid.feature.avatar

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The minimum-device gate for the avatar feature (the Avatar tab and the Go Live VTuber toggle are hidden
 * when [supported] is false). Policy, see [isAvatarSupported]: the renderer needs Filament's OpenGL
 * backend (GLES 3.0) or its Vulkan backend; the face tracker itself falls back to CPU on its own.
 */
@Singleton
class AvatarSupport @Inject constructor(@param:ApplicationContext private val context: Context) {
    val supported: Boolean by lazy {
        val glEs = (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
            ?.deviceConfigurationInfo?.reqGlEsVersion ?: 0
        val vulkan = context.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
        isAvatarSupported(glEs, vulkan).also {
            Log.i(TAG, "avatar supported=$it (GLES 0x${Integer.toHexString(glEs)}, vulkan=$vulkan)")
        }
    }

    private companion object {
        const val TAG = "AvatarSupport"
    }
}

/**
 * Pure device policy: [reqGlEsVersion] is `ConfigurationInfo.reqGlEsVersion` (major in the high 16 bits),
 * [hasVulkanHardware] the `FEATURE_VULKAN_HARDWARE_LEVEL` system feature. Either Filament backend suffices.
 */
fun isAvatarSupported(reqGlEsVersion: Int, hasVulkanHardware: Boolean): Boolean =
    reqGlEsVersion >= GLES_3_0 || hasVulkanHardware

/** `reqGlEsVersion` encoding of OpenGL ES 3.0. */
const val GLES_3_0 = 0x30000

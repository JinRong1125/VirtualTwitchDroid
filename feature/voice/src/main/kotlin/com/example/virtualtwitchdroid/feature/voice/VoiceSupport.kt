package com.example.virtualtwitchdroid.feature.voice

import android.os.Build
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether this device can run the Zundamon voice: VOICEVOX CORE's Android package requires API 26 and ships
 * 64-bit libraries only (the app filters to arm64-v8a / x86_64). Pure decision, unit-tested.
 */
@Singleton
class VoiceSupport internal constructor(val supported: Boolean) {

    @Inject
    constructor() : this(isVoiceSupported(Build.VERSION.SDK_INT, Build.SUPPORTED_64_BIT_ABIS?.isNotEmpty() == true))

    companion object {
        const val MIN_SDK = 26

        fun isVoiceSupported(sdkInt: Int, has64BitAbi: Boolean): Boolean = sdkInt >= MIN_SDK && has64BitAbi
    }
}

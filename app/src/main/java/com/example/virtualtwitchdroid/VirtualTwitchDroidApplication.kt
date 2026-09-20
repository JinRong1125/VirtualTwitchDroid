package com.example.virtualtwitchdroid

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class VirtualTwitchDroidApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Dev-time monitoring: on a debuggable build, surface stability smells to logcat — main-thread
        // disk/network I/O (jank) and leaked Activities / Closeables / registrations (memory leaks) —
        // so they're caught during local + ARTEMIS runs. `penaltyLog` only: never crashes the app, and
        // the whole block is skipped in release, so there is zero production impact.
        if (isDebuggable()) enableStrictModeMonitoring()
    }

    private fun isDebuggable(): Boolean = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private fun enableStrictModeMonitoring() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll() // main-thread disk reads/writes, network, slow calls
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            // Targeted leak detectors (not detectAll) to keep the signal high — detectAll's
            // untagged-socket / non-SDK-API checks are noisy with media/camera/image libraries.
            StrictMode.VmPolicy.Builder()
                .detectActivityLeaks()
                .detectLeakedClosableObjects()
                .detectLeakedRegistrationObjects()
                .detectLeakedSqlLiteObjects()
                .penaltyLog()
                .build(),
        )
        Log.i("TwitchStrictMode", "StrictMode dev monitoring enabled (debuggable build)")
    }
}

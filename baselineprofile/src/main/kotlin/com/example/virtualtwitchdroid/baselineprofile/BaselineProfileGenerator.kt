package com.example.virtualtwitchdroid.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates a Baseline Profile for the critical startup + browse journey. Run with
 * `./gradlew :baselineprofile:generateBaselineProfile` (needs a connected API 28+ device); the
 * generated `baseline-prof.txt` is consumed by `:app` via its `baselineProfile(...)` dependency and
 * packaged so ART pre-compiles these hot paths at install time.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = "com.example.virtualtwitchdroid",
        // Also emit a startup profile so R8 groups these classes into contiguous dex pages.
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()

        // Wait for the start destination — the "Live Channels" (Popular) feed.
        device.wait(Until.hasObject(By.textContains("Live Channels")), 10_000)

        // Exercise the browse list (thumbnails, cards) by scrolling.
        device.findObject(By.scrollable(true))?.let { list ->
            list.setGestureMargin(device.displayWidth / 5)
            repeat(2) { list.fling(Direction.DOWN) }
            list.fling(Direction.UP)
        }

        // Visit the other top-level tabs so their first-render code is profiled too.
        device.findObject(By.textContains("Games"))?.click()
        device.waitForIdle()
        device.findObject(By.textContains("Popular"))?.click()
        device.waitForIdle()
    }
}

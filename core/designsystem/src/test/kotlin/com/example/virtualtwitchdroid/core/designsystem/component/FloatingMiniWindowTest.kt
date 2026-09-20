package com.example.virtualtwitchdroid.core.designsystem.component

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * The stream/watch mini docks at HALF the screen's shorter side: half the viewport WIDTH in portrait,
 * half the viewport HEIGHT in landscape (via [defaultDockedMiniWidth]). Pixel values approximate a
 * Pixel 8a: caption 48dp ≈ 126px, edge padding 12dp ≈ 32px @ 420dpi; 16:9 video.
 */
class FloatingMiniWindowTest {
    private val caption = 126f
    private val pad = 32f
    private val aspect = 16f / 9f

    @Test
    fun portrait_dockWidthIsHalfTheViewportWidth() {
        val w = 1080f
        val h = 2400f
        val miniW = defaultDockedMiniWidth(w, h, caption, pad, aspect)
        // Half the width; the height clamp does not engage in portrait (plenty of vertical room).
        assertEquals(w / 2f, miniW, 1f)
    }

    @Test
    fun landscape_dockWidthIsHalfTheViewportHeight() {
        val w = 2400f
        val h = 1080f
        val miniW = defaultDockedMiniWidth(w, h, caption, pad, aspect)
        // Half the (shorter) height; verify the height clamp still leaves room so it's not reduced.
        assertEquals(h / 2f, miniW, 1f)
    }

    @Test
    fun publishPortrait_thirdWidthFraction_isOneThirdOfViewportWidth() {
        val w = 1080f
        val h = 2400f
        // Publish portrait: 9:16 (tall) camera aspect, ⅓ width fraction.
        val miniW = defaultDockedMiniWidth(w, h, caption, pad, 9f / 16f, widthFraction = 1f / 3f)
        assertEquals(w / 3f, miniW, 1f)
    }

    @Test
    fun degenerateSize_neverBelowOnePixel() {
        assertTrue(defaultDockedMiniWidth(0f, 0f, caption, pad, aspect) >= 1f)
    }

    // ---- miniRestTop: docking clear of the keyboard and a focused field --------------------------

    private val areaH = 2400f
    private val miniTotalH = 430f // ~half-width 16:9 video (~304px) + caption on a Pixel 8a

    @Test
    fun restTop_noImeNoField_docksAtTheBottom() {
        // Unchanged legacy behaviour: rest pad above the area bottom.
        val top = miniRestTop(areaH, imeBottomPx = 0f, focusedFieldTopPx = null, miniTotalH, pad)
        assertEquals(areaH - miniTotalH - pad, top, 0.5f)
    }

    @Test
    fun restTop_imeOpenNoOverlappingField_docksAboveTheKeyboard() {
        val ime = 1000f
        val top = miniRestTop(areaH, imeBottomPx = ime, focusedFieldTopPx = null, miniTotalH, pad)
        // Bottom edge (top + miniTotalH) sits pad above the keyboard top (areaH - ime), not behind it.
        assertEquals(areaH - ime - miniTotalH - pad, top, 0.5f)
        assertTrue(top + miniTotalH <= areaH - ime, "mini must not extend behind the keyboard")
    }

    @Test
    fun restTop_focusedFieldOverlaps_docksAboveTheField() {
        val ime = 1000f
        val fieldTop = areaH - ime - 150f // field flush above the keyboard (150px tall)
        val top = miniRestTop(areaH, imeBottomPx = ime, focusedFieldTopPx = fieldTop, miniTotalH, pad)
        // Docks above the FIELD, not merely above the keyboard.
        assertEquals(fieldTop - miniTotalH - pad, top, 0.5f)
        assertTrue(top + miniTotalH <= fieldTop, "mini bottom must clear the focused field's top")
    }

    @Test
    fun restTop_fieldHigherThanCard_isIgnored_docksAtBottom() {
        // A focused field far up the screen (clear of the bottom-docked card) must NOT drag it upward.
        val fieldTop = 200f
        val top = miniRestTop(areaH, imeBottomPx = 0f, focusedFieldTopPx = fieldTop, miniTotalH, pad)
        assertEquals(areaH - miniTotalH - pad, top, 0.5f) // unchanged bottom dock
    }

    @Test
    fun restTop_overlappingFieldWithNoIme_docksAboveTheField() {
        // Focus-only re-dock (e.g. hardware keyboard, bottom bar still shown): a bottom field that
        // overlaps the docked card lifts it, with no IME inset involved.
        val fieldTop = 2000f // below naturalTop (areaH - miniTotalH - pad ≈ 1938) → overlaps
        val top = miniRestTop(areaH, imeBottomPx = 0f, focusedFieldTopPx = fieldTop, miniTotalH, pad)
        assertEquals(fieldTop - miniTotalH - pad, top, 0.5f)
        assertTrue(top + miniTotalH <= fieldTop, "mini bottom must clear the field's top")
    }

    @Test
    fun restTop_reDockClampedToTopPadding_whenFieldLeavesNoRoom() {
        // A tall re-dock that would push the card off the top is clamped to pad.
        val top = miniRestTop(areaH = 500f, imeBottomPx = 0f, focusedFieldTopPx = 40f, miniTotalH, pad)
        assertEquals(pad, top, 0.5f)
    }

    @Test
    fun restTop_neverAboveTopPadding() {
        // Degenerate: keyboard taller than the area must not push the card off the top.
        val top = miniRestTop(areaH = 500f, imeBottomPx = 5000f, focusedFieldTopPx = null, miniTotalH, pad)
        assertEquals(pad, top, 0.5f)
    }
}

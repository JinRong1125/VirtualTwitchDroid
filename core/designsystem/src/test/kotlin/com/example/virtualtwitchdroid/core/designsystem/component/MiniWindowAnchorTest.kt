package com.example.virtualtwitchdroid.core.designsystem.component

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

/** The ownership guard that makes focus transfer between two opted-in fields safe. */
class MiniWindowAnchorTest {

    @Test
    fun claim_setsTheReportedTop() {
        val anchor = MiniWindowAnchor()
        anchor.claim(owner = Any(), topInWindow = 120f)
        assertEquals(120f, anchor.focusedInputTopInWindow)
    }

    @Test
    fun release_byTheOwner_clearsTheSlot() {
        val anchor = MiniWindowAnchor()
        val field = Any()
        anchor.claim(field, 120f)
        anchor.release(field)
        assertNull(anchor.focusedInputTopInWindow)
    }

    @Test
    fun release_byAFormerOwner_afterTransfer_isIgnored() {
        val anchor = MiniWindowAnchor()
        val fieldA = Any()
        val fieldB = Any()
        anchor.claim(fieldA, 100f)
        anchor.claim(fieldB, 200f) // focus moved A -> B
        anchor.release(fieldA) // A's late blur must NOT null B's slot
        assertEquals(200f, anchor.focusedInputTopInWindow)
    }
}

package kr.me.nulld.iduohome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DockSlotsTest {
    @Test fun `dock grows with empty slots and keeps pinned apps`() {
        val resized = resizeDock(listOf("one", null, "three", "four"), 6)

        assertEquals(6, resized.size)
        assertEquals(listOf("one", null, "three", "four"), resized.take(4))
        assertNull(resized[4])
        assertNull(resized[5])
    }

    @Test fun `dock shrinks in display order and count remains between four and six`() {
        assertEquals(listOf("one", "two", "three", "four"),
            resizeDock(listOf("one", "two", "three", "four", "five", "six"), 4))
        assertEquals(4, resizeDock(emptyList(), 2).size)
        assertEquals(6, resizeDock(emptyList(), 9).size)
    }
}

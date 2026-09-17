package kr.me.nulld.iduohome

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.*
import org.junit.Test

class HomeItemInteractionTest {
    private val grid = WidgetGridSizing(4, 7, 80f, 60f, 100f,
        topRowHeightDp = 100f, appRowHeightDp = 60f)

    @Test fun `resize snaps across mixed row heights and supports all edges`() {
        val widget = WidgetPlacement(0, CLOCK_WIDGET, 0, 1, 1, 2, 2)
        assertEquals(widget.copy(spanY = 3), snappedWidgetResize(widget, 0, 1, 0f, 40f, grid, null))
        assertEquals(widget, snappedWidgetResize(widget, 0, 1, 0f, 20f, grid, null))
        assertEquals(widget.copy(column = 0, spanX = 3), snappedWidgetResize(widget, -1, 0, -80f, 0f, grid, null))
        assertEquals(widget.copy(row = 0, spanY = 3), snappedWidgetResize(widget, 0, -1, 0f, -100f, grid, null))
        assertEquals(7, snappedWidgetResize(widget, 0, 1, 0f, 5000f, grid, null).let { it.row + it.spanY })
    }

    @Test fun `provider fixed axes and maximum spans are respected`() {
        val widget = WidgetPlacement(0, 42, 0, 0, 0, 2, 2)
        val limits = WidgetSpanConstraints(WidgetSpan(2, 2), WidgetSpan(1, 2), WidgetSpan(3, 2), true, false)
        assertEquals(widget.copy(spanX = 3), snappedWidgetResize(widget, 1, 1, 900f, 900f, grid, limits))
    }

    @Test fun `popup prefers below and flips above at bottom within safe bounds`() {
        val safe = Rect(8f, 24f, 392f, 776f)
        assertEquals(IntOffset(50, 212), homePopupOffset(Rect(100f, 100f, 200f, 200f), IntSize(200, 100), safe, 12f))
        assertEquals(IntOffset(192, 588), homePopupOffset(Rect(340f, 700f, 390f, 760f), IntSize(200, 100), safe, 12f))
    }

    @Test fun `widget swaps with another widget preserving ids and restore metadata`() {
        val first = WidgetPlacement(0, 81, 0, 0, 0, 2, 2)
        val second = WidgetPlacement(1, 82, -1, 2, 3, 2, 2)
        val before = HomeLayout(emptyList(), listOf(null), listOf(first, second))
        val after = moveWidget(before, 0, homeCellIndex(-1, 14))
        assertEquals(first.copy(page = -1, column = 2, row = 3), after.placement(0))
        assertEquals(second.copy(page = 0, column = 0, row = 0), after.placement(1))
        assertEquals(before, moveWidget(after, 0, 0))
    }

    @Test fun `widget displaces only collided apps into its vacated cells`() {
        val widget = WidgetPlacement(0, CLOCK_WIDGET, 0, 0, 0, 2, 2)
        val before = HomeLayout(List(18) { when (it) { 8 -> "one"; 9 -> "two"; 17 -> "untouched"; else -> null } },
            listOf("dock"), listOf(widget))
        val after = moveWidget(before, 0, 8)
        assertEquals("one", after.slotAt(0)); assertEquals("two", after.slotAt(1))
        assertNull(after.slotAt(8)); assertNull(after.slotAt(9))
        assertEquals("untouched", after.slotAt(17)); assertEquals(before.dock, after.dock)
        assertTrue(after.widgetPlacements.flatMap { it.coveredIndices() }.none { after.slotAt(it) != null })
        assertEquals(before.slots.filterNotNull().toSet(), after.slots.filterNotNull().toSet())
    }

    @Test fun `impossible swap and resizing collisions leave original layout unchanged`() {
        val small = WidgetPlacement(0, CLOCK_WIDGET, 0, 2, 0, 2, 2)
        val large = WidgetPlacement(1, DATE_WIDGET, 0, 0, 3, 3, 2)
        val before = HomeLayout(emptyList(), emptyList(), listOf(small, large))
        assertSame(before, moveWidget(before, 0, 12))
        assertSame(before, resizeWidget(before, 0, 2, 5))
    }
}

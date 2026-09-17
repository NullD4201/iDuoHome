package kr.me.nulld.iduohome

import kotlin.math.abs

/**
 * Maps the native full-width pager position to the scroll distance shown by the
 * expanded workspace. Home and All apps advance by one pane; Discover uses
 * the full pager width.
 */
internal data class WorkspacePageMotion(
    val firstHome: Int,
    val pageWidth: Float,
    val homeStride: Float,
) {
    init {
        require(pageWidth.isFinite() && pageWidth > 0f)
        require(homeStride.isFinite() && homeStride > 0f)
    }

    /** Visual scroll offset for a physical (and possibly fractional) pager position. */
    fun offset(position: Float): Float {
        val logical = position - firstHome
        return logical * if (logical < 0f) pageWidth else homeStride
    }

    /** Physical pager position for a visual scroll offset. */
    fun position(offset: Float): Float {
        val logical = offset / if (offset < 0f) pageWidth else homeStride
        return logical + firstHome
    }

    fun positionAfterVisualDelta(position: Float, delta: Float): Float =
        position(offset(position) + delta)

    fun stride(fromPosition: Int, towardPosition: Int): Float =
        abs(offset(towardPosition.toFloat()) - offset(fromPosition.toFloat()))
}

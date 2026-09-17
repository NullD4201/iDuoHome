package kr.me.nulld.iduohome

import org.junit.Assert.*
import org.junit.Test

class FoldEffectMotionTest {
    @Test fun handoffSurvivesRecreationButDoesNotTriggerForRotationOrOldSessions() {
        assertTrue(isInnerFoldViewport(616, 816))
        assertTrue(isInnerFoldViewport(816, 616))
        assertFalse(isInnerFoldViewport(400, 900))
        assertFalse(isInnerFoldViewport(900, 400))
        val session = FoldEffectSession()
        assertFalse(session.viewport(400, 900, 0))
        assertFalse(session.viewport(900, 400, 100))
        session.visibleAt(20_000)
        assertTrue(session.viewport(750, 900, 20_200))
        assertFalse(session.viewport(900, 750, 20_300))
        assertTrue(session.viewport(400, 900, 20_500))
        assertFalse(session.viewport(750, 900, 30_000))
        session.clear()
        assertFalse(session.viewport(400, 900, 30_100))
    }

    @Test fun postureValuesAndInvalidOrOutOfOrderSamplesDoNotProveContinuousAngles() {
        val samples = FoldAngleSamples()
        listOf(0f, 90f, 180f, 90f, 0f).forEachIndexed { index, angle ->
            assertTrue(samples.accept(angle, index.toLong()))
        }
        assertFalse(samples.continuous)
        assertFalse(samples.accept(Float.NaN, 5))
        assertFalse(samples.accept(Float.POSITIVE_INFINITY, 5))
        assertFalse(samples.accept(-1f, 5))
        assertFalse(samples.accept(181f, 5))
        assertFalse(samples.accept(50f, 3))
        assertTrue(samples.accept(45f, 5))
        assertTrue(samples.accept(45.2f, 6))
        assertFalse(samples.continuous)
        assertTrue(samples.accept(47f, 7))
        assertTrue(samples.continuous)
        assertTrue(samples.accept(180f, 8))
    }

    @Test fun opticsAreFiniteAndFadeToSharpAtBothEndpoints() {
        assertEquals(0f, foldOptics(0f, false, 1f).amount, 0f)
        assertEquals(0f, foldOptics(180f, true, 1f).amount, 0f)
        assertEquals(.39f, foldOptics(90f, false, 1f).expansion, .0001f)
        assertEquals(1f / 16, foldOptics(90f, true, 1f).depth, .0001f)
        for (inner in listOf(false, true)) {
            val amounts = (0..180).map { foldOptics(it.toFloat(), inner, 1f).amount }
            amounts.zipWithNext().forEach { (a, b) -> assertTrue(if (inner) a >= b else a <= b) }
            assertEquals(0f, foldOptics(Float.NaN, inner, Float.NaN).amount, 0f)
            assertEquals(.5f, foldOptics(90f, inner, .5f).amount, 0f)
        }
    }
}

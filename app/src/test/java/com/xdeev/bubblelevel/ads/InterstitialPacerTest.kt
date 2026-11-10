package com.xdeev.bubblelevel.ads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterstitialPacerTest {
    private val rules = InterstitialPacer.Rules(
        graceMs = 1_000, minGapMs = 10_000, minBreaksBetweenAds = 2,
        quietAfterLevelMs = 500, minAwayForAppOpenMs = 3_000,
    )

    private fun pacer(): InterstitialPacer = InterstitialPacer(rules).also { it.startSession(0) }

    @Test fun nothingDuringTheGracePeriod() {
        val p = pacer()
        assertFalse(p.onBreak(100, true))
        assertFalse(p.onBreak(900, true))
    }

    @Test fun needsEnoughActionsEvenAfterTheGracePeriod() {
        val p = pacer()
        assertFalse(p.onBreak(2_000, true)) // 1st break
        assertTrue(p.onBreak(3_000, true))  // 2nd break: allowed
    }

    @Test fun actionsDuringTheGracePeriodStillCount() {
        val p = pacer()
        p.onBreak(100, true)
        p.onBreak(200, true)
        assertTrue(p.onBreak(1_500, true))
    }

    @Test fun minimumGapIsTheOnlyHardLimit() {
        val p = pacer()
        p.onBreak(2_000, true)
        assertTrue(p.onBreak(3_000, true))
        // plenty of actions but too soon
        repeat(5) { assertFalse(p.onBreak(4_000L + it, true)) }
        // gap over: allowed again, and again, with no session or daily cap
        assertTrue(p.onBreak(14_000, true))
        p.onBreak(25_000, true); assertTrue(p.onBreak(25_500, true))
        p.onBreak(40_000, true); assertTrue(p.onBreak(40_500, true))
    }

    @Test fun quietRightAfterTheLevelIsReached() {
        val p = pacer()
        p.onBreak(2_000, true)
        p.noteLevelReached(2_900)
        assertFalse(p.onBreak(3_000, true))   // 100 ms after level
        assertTrue(p.onBreak(3_600, true))    // quiet period over
    }

    @Test fun anAdThatIsNotLoadedIsNeverShownAndNotCounted() {
        val p = pacer()
        p.onBreak(2_000, false)
        assertFalse(p.onBreak(3_000, false))
        assertTrue(p.onBreak(4_000, true))
    }

    @Test fun appOpenNeedsALongEnoughAbsence() {
        val p = pacer()
        assertFalse(p.onForeground(5_000, 1_000, true))
        assertTrue(p.onForeground(5_000, 4_000, true))
    }

    @Test fun appOpenNeverRightAfterOrBeforeAnotherFullScreenAd() {
        val p = pacer()
        p.onBreak(2_000, true); assertTrue(p.onBreak(3_000, true))        // interstitial shown at t=3 s
        assertFalse(p.onForeground(6_000, 5_000, true))                    // app open right after: blocked
        assertTrue(p.onForeground(14_000, 5_000, true))                    // gap over: allowed
        p.onBreak(15_000, true); assertFalse(p.onBreak(15_500, true))      // interstitial right after: blocked
    }

    @Test fun appOpenNotInTheGracePeriodOrWhenNotLoaded() {
        val p = pacer()
        assertFalse(p.onForeground(500, 5_000, true))
        assertFalse(p.onForeground(5_000, 5_000, false))
    }
}

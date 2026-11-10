package com.xdeev.bubblelevel.ads

/**
 * Decides when a full-screen ad (interstitial or app open) is allowed. Pure logic (no Android, no clock
 * of its own) so it can be unit-tested.
 *
 * The one hard limit is TIME: at least [Rules.minGapMs] between any two full-screen ads, whatever their
 * type. There is deliberately no per-session or per-day cap. On top of that:
 *  - an interstitial only appears at a natural break (the user just switched mode or closed settings),
 *    after a grace period, after a few real actions since the previous ad, and never right after the
 *    phone became level (the user is still reading the result);
 *  - an app open ad only appears when the user comes back to the app after being away for a while.
 */
class InterstitialPacer(private val rules: Rules) {

    data class Rules(
        val graceMs: Long,
        val minGapMs: Long,
        val minBreaksBetweenAds: Int,
        val quietAfterLevelMs: Long,
        val minAwayForAppOpenMs: Long,
    ) {
        companion object {
            /** What real users get. */
            val RELEASE = Rules(
                graceMs = 90_000, minGapMs = 300_000, minBreaksBetweenAds = 3,
                quietAfterLevelMs = 20_000, minAwayForAppOpenMs = 120_000,
            )

            /** Debug builds: same logic, short timers, so it can be tried by hand in a minute. */
            val DEBUG_FAST = Rules(
                graceMs = 5_000, minGapMs = 20_000, minBreaksBetweenAds = 2,
                quietAfterLevelMs = 3_000, minAwayForAppOpenMs = 4_000,
            )
        }
    }

    private var sessionStart = NEVER
    private var lastShown = NEVER
    private var lastLevel = NEVER
    private var breaksSinceLastAd = 0

    fun startSession(now: Long) {
        if (sessionStart == NEVER) sessionStart = now
    }

    fun noteLevelReached(now: Long) {
        lastLevel = now
    }

    /** Call at every natural break. True = an interstitial may be shown right now (and counts as shown). */
    fun onBreak(now: Long, adReady: Boolean): Boolean {
        breaksSinceLastAd++
        if (!adReady || !gapAndGraceOk(now)) return false
        if (breaksSinceLastAd < rules.minBreaksBetweenAds) return false
        if (lastLevel != NEVER && now - lastLevel < rules.quietAfterLevelMs) return false
        markShown(now)
        return true
    }

    /** Call when the user returns to the app after [awayMs] in the background. True = an app open ad may show. */
    fun onForeground(now: Long, awayMs: Long, adReady: Boolean): Boolean {
        if (!adReady || awayMs < rules.minAwayForAppOpenMs || !gapAndGraceOk(now)) return false
        markShown(now)
        return true
    }

    private fun gapAndGraceOk(now: Long): Boolean {
        if (sessionStart == NEVER || now - sessionStart < rules.graceMs) return false
        return lastShown == NEVER || now - lastShown >= rules.minGapMs
    }

    private fun markShown(now: Long) {
        lastShown = now
        breaksSinceLastAd = 0
    }

    private companion object {
        const val NEVER = Long.MIN_VALUE
    }
}

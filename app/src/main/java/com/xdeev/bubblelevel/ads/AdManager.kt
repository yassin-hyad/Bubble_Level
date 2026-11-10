package com.xdeev.bubblelevel.ads

import android.app.Activity
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.xdeev.bubblelevel.BuildConfig

/**
 * All ad rules in one place:
 *  - nothing loads before the user's consent (Google UMP) allows it;
 *  - one small banner under the controls, never over the measuring area, from the first launch;
 *  - interstitials only at natural breaks, app open ads only when the user comes back to the app;
 *    both go through [InterstitialPacer]: at least a few minutes between ANY two full-screen ads,
 *    never right after the phone became level, never while measuring;
 *  - app open ads never on a cold start, and only from the 3rd launch on (Google's own guidance).
 */
object AdManager {
    var bannerEnabled by mutableStateOf(false); private set
    var privacyOptionsRequired by mutableStateOf(false); private set

    private val pacer = InterstitialPacer(
        if (BuildConfig.DEBUG) InterstitialPacer.Rules.DEBUG_FAST else InterstitialPacer.Rules.RELEASE,
    )
    private var started = false
    private var appOpenAllowed = false
    private var showingFullScreen = false
    private var backgroundedAt = 0L

    private var interstitial: InterstitialAd? = null
    private var appOpen: AppOpenAd? = null
    private var appOpenLoadedAt = 0L

    /** [launchCount] is 1 on the very first launch. */
    fun start(activity: Activity, launchCount: Int) {
        appOpenAllowed = launchCount >= MIN_LAUNCHES_FOR_APP_OPEN
        pacer.startSession(SystemClock.elapsedRealtime())
        val consent = UserMessagingPlatform.getConsentInformation(activity)
        consent.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { initialize(activity, consent) }
            },
            { initialize(activity, consent) },
        )
        // Consent may already have been given in an earlier session.
        initialize(activity, consent)
    }

    /** Lets the user change their consent. Google requires this entry point where consent applies. */
    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) {}
    }

    /** The phone just became level: the user is reading the result, so keep quiet for a while. */
    fun noteLevelReached() = pacer.noteLevelReached(SystemClock.elapsedRealtime())

    /** Call when the user finishes something (switches mode, closes settings). May show an interstitial. */
    fun onNaturalBreak(activity: Activity) {
        if (!started || showingFullScreen) return
        val ad = interstitial
        if (!pacer.onBreak(SystemClock.elapsedRealtime(), ad != null)) return
        ad ?: return
        interstitial = null
        showingFullScreen = true
        ad.fullScreenContentCallback = fullScreenCallback { loadInterstitial(activity) }
        ad.show(activity)
    }

    fun onAppStopped() {
        // Opening or closing one of our own full-screen ads also stops the activity: not a real absence.
        if (!showingFullScreen) backgroundedAt = SystemClock.elapsedRealtime()
    }

    /** The user came back to the app. May show an app open ad. Never called for a cold start's first onStart. */
    fun onAppStarted(activity: Activity) {
        if (backgroundedAt == 0L) return
        val now = SystemClock.elapsedRealtime()
        val away = now - backgroundedAt
        backgroundedAt = 0L
        if (!started || !appOpenAllowed || showingFullScreen) return
        val ad = appOpen
        val fresh = ad != null && now - appOpenLoadedAt < APP_OPEN_MAX_AGE_MS // loaded ads expire after 4 h
        if (!pacer.onForeground(now, away, fresh)) return
        ad ?: return
        appOpen = null
        showingFullScreen = true
        ad.fullScreenContentCallback = fullScreenCallback { loadAppOpen(activity) }
        ad.show(activity)
    }

    private fun fullScreenCallback(reload: () -> Unit) = object : FullScreenContentCallback() {
        override fun onAdDismissedFullScreenContent() {
            showingFullScreen = false
            reload()
        }

        override fun onAdFailedToShowFullScreenContent(error: AdError) {
            showingFullScreen = false
            reload()
        }
    }

    private fun initialize(activity: Activity, consent: ConsentInformation) {
        privacyOptionsRequired =
            consent.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        if (started || !consent.canRequestAds()) return
        started = true
        MobileAds.initialize(activity.applicationContext) {
            bannerEnabled = true
            loadInterstitial(activity)
            if (appOpenAllowed) loadAppOpen(activity)
        }
    }

    private fun loadInterstitial(activity: Activity) {
        InterstitialAd.load(
            activity.applicationContext,
            BuildConfig.INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitial = null
                }
            },
        )
    }

    private fun loadAppOpen(activity: Activity) {
        AppOpenAd.load(
            activity.applicationContext,
            BuildConfig.APP_OPEN_ID,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpen = ad
                    appOpenLoadedAt = SystemClock.elapsedRealtime()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    appOpen = null
                }
            },
        )
    }

    private const val MIN_LAUNCHES_FOR_APP_OPEN = 3
    private const val APP_OPEN_MAX_AGE_MS = 4L * 60 * 60 * 1000
}

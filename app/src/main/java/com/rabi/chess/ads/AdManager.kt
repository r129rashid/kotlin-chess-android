package com.rabi.chess.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * Loads and shows an interstitial ad at the end of a game.
 *
 * Policy-friendly throttling: an ad is shown at most once every [minGamesBetweenAds]
 * completed games AND never less than [minIntervalMs] after the previous one. If no ad
 * is ready, gameplay continues without blocking.
 */
class AdManager(private val context: Context) {

    companion object {
        private const val TAG = "AdManager"
        private const val minGamesBetweenAds = 2
        private const val minIntervalMs = 45_000L
    }

    private var interstitial: InterstitialAd? = null
    private var loading = false
    private var gamesSinceAd = 0
    private var lastShownAt = 0L

    fun initialize() {
        MobileAds.initialize(context) { Log.i(TAG, "MobileAds initialized") }
        load()
    }

    private fun load() {
        if (loading || interstitial != null) return
        loading = true
        InterstitialAd.load(
            context,
            AdIds.INTERSTITIAL,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.i(TAG, "Interstitial loaded")
                    interstitial = ad
                    loading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Interstitial failed to load: ${error.message}")
                    interstitial = null
                    loading = false
                }
            }
        )
    }

    /**
     * Called when a game ends. Shows an interstitial if throttling allows and one is ready,
     * then invokes [onDone] (whether or not an ad was shown) so the caller can proceed.
     */
    fun onGameOver(activity: Activity, onDone: () -> Unit) {
        gamesSinceAd++
        val now = System.currentTimeMillis()
        val ad = interstitial
        val allowed = gamesSinceAd >= minGamesBetweenAds && (now - lastShownAt) >= minIntervalMs

        if (ad == null || !allowed) {
            if (ad == null) load()
            onDone()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitial = null
                lastShownAt = System.currentTimeMillis()
                gamesSinceAd = 0
                load()
                onDone()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitial = null
                load()
                onDone()
            }
        }
        ad.show(activity)
    }
}

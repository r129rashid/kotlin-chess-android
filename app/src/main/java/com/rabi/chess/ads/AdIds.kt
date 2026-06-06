package com.rabi.chess.ads

/**
 * Single source of truth for ad unit IDs.
 *
 * These are Google's official, always-filling TEST ad unit IDs. The app works out of
 * the box with no AdMob account. BEFORE PUBLISHING (see PUBLISHING.md):
 *   1. Create an AdMob app + an Interstitial ad unit.
 *   2. Replace [INTERSTITIAL] below with your real unit ID.
 *   3. Replace the APPLICATION_ID meta-data in AndroidManifest.xml with your real App ID.
 */
object AdIds {
    /** Google sample interstitial unit — test ads only. */
    const val INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
}

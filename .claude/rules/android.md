---
paths:
  - "app/src/main/java/com/rabi/chess/*.kt"
  - "app/src/main/java/com/rabi/chess/ui/**"
  - "app/src/main/java/com/rabi/chess/audio/**"
  - "app/src/main/java/com/rabi/chess/ads/**"
---

# Android layer rules

## Threading
- AI must run on Dispatchers.Default, never on the main thread.
- All UI updates (board render, status text, overlay visibility) must happen on the main thread.
- ValueAnimator callbacks run on the main thread — safe for drawing, not for blocking work.

## Lifecycle
- SoundManager.release() must be called in onDestroy — no other place.
- ValueAnimator must be cancelled in BoardView.onDetachedFromWindow.
- AdMob listeners must not hold Activity references after onDestroy.

## Input
- BoardView.inputEnabled must be false during AI thinking and during animation.
- Only re-enable input in the animateMove onEnd callback, not before.

## Ads
- All AdMob access goes through AdManager. Nothing else touches InterstitialAd directly.
- AdIds.kt is the single source for unit IDs. Never hardcode an ad unit ID elsewhere.
- Test IDs must stay in place until the user explicitly requests production IDs.

## Resources
- All user-visible strings go in res/values/strings.xml — no hardcoded strings in Kotlin.
- All colours go in res/values/colors.xml — no hardcoded hex values in Kotlin.

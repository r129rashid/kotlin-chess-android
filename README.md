# Kotlin Chess

A clean, native Android chess game in Kotlin — **local two-player** and **play vs the
computer** (a built-in minimax AI with Easy/Medium/Hard difficulty). Polished Material 3
UI, animated piece movement, sound effects, and AdMob interstitial ads between games
(shipping with Google **test** ad IDs so it runs immediately).

## Highlights

- **Full chess rules** — castling, en passant, promotion, check/checkmate/stalemate,
  fifty-move and insufficient-material draws. Engine is pure Kotlin and unit-tested.
- **Computer opponent** — alpha-beta minimax with material + piece-square evaluation,
  running off the UI thread so the app stays responsive.
- **No image/audio assets to license** — pieces are Unicode glyphs drawn on a `Canvas`;
  the four sound clips are tiny generated WAVs in `res/raw`.
- **Tiny, easy to publish** — single module, ready for a signed `.aab` (see `PUBLISHING.md`).

## Project layout

```
app/src/main/java/com/rabi/chess/
  engine/   pure-Kotlin rules engine (Board, MoveGenerator, Piece, Move, …)
  ai/       ChessAI — minimax opponent
  ui/       BoardView (draw/animate/tap), PromotionDialog
  audio/    SoundManager (SoundPool)
  ads/      AdManager + AdIds (AdMob)
  MainActivity / GameActivity
app/src/test/…   engine unit tests
```

## Build & run

The Android SDK path is configured in `local.properties` (gitignored). Then:

```bash
# Run the engine unit tests
./gradlew :app:testDebugUnitTest

# Build a debug APK
./gradlew assembleDebug        # -> app/build/outputs/apk/debug/app-debug.apk

# Install on a running emulator/device
./gradlew installDebug
```

Or just open the folder in **Android Studio** and press Run.

> Test ads require Google Play services, so use a Play Store emulator image (e.g. the
> `Medium_Phone_API_36` AVD) or a real device.

## Customizing

- **Difficulty** = search depth in `ai/ChessAI.kt` (`Difficulty` enum).
- **Colors / board palette** in `res/values/colors.xml`.
- **Sounds** in `res/raw/` (regenerate or drop in your own `move/capture/check/gameover`).
- **Real ads / publishing** — see `PUBLISHING.md`.

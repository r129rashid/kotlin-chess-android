# Kotlin Chess

A native Android chess game in Kotlin — local two-player pass-and-play and vs a built-in
minimax AI (Easy / Medium / Hard). Ships with AdMob interstitial ads (Google test IDs;
real IDs configured before publishing). Full legal chess rules, Material 3 UI, animated
piece movement, and sound effects.

## Stack

- **Language:** Kotlin 2.0.x
- **Platform:** Android (minSdk 24, targetSdk / compileSdk 35)
- **Build:** Gradle wrapper (8.11.1) + AGP 8.7.3
- **SDK path:** `~/Library/Android/sdk` (configured in gitignored `local.properties`)
- **Ads:** Google Mobile Ads SDK (AdMob) — test IDs in `app/src/main/java/com/rabi/chess/ads/AdIds.kt`
- **AI:** Self-contained alpha-beta minimax (no external engine)

## Commands

```bash
# Run the chess engine unit tests (no emulator needed)
./gradlew :app:testDebugUnitTest

# Build a debug APK
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# Install on a running emulator or device
./gradlew installDebug

# Build a signed release App Bundle (see PUBLISHING.md for signing setup first)
./gradlew bundleRelease
# → app/build/outputs/bundle/release/app-release.aab

# Clean
./gradlew clean
```

## Source layout

```
app/src/main/java/com/rabi/chess/
  engine/          Pure-Kotlin rules engine — Board, MoveGenerator, Piece, Move, Position,
                   GameResult. No Android imports. Fully unit-testable.
  ai/              ChessAI — alpha-beta minimax with piece-square tables. Difficulty enum.
  ui/              BoardView (Canvas draw + tap + animation), PromotionDialog.
  audio/           SoundManager (SoundPool wrapper, four .wav clips in res/raw/).
  ads/             AdManager (throttled interstitial) + AdIds (single source of unit IDs).
  MainActivity     Start menu: Two Players / vs Computer with difficulty + color selectors.
  GameActivity     Game orchestrator: wires engine, AI, board, sound, ads.

app/src/test/java/com/rabi/chess/engine/
  MoveGeneratorTest   9 JUnit tests: starting moves, fool's/scholar's mate, stalemate,
                      castling through check, en passant, promotion, undo.

app/src/main/res/
  values/    colors.xml (board palette, Material 3), themes.xml, strings.xml
  raw/       move.wav, capture.wav, check.wav, gameover.wav (generated, tiny)
  drawable/  ic_launcher_background.xml, ic_launcher_foreground.xml (vector chess king)
  mipmap-anydpi-v26/  Adaptive launcher icon (light/round variants)
```

## File conventions

- Kotlin files: `PascalCase` for classes, `camelCase` for functions/properties
- Package root: `com.rabi.chess`
- All engine code lives in `engine/` — zero Android imports allowed there
- All ad logic lives in `ads/` — nothing else touches AdMob directly
- Sound logic lives in `audio/` — nothing else instantiates `SoundPool` directly

## Key files to know

| File | Purpose |
|------|---------|
| `engine/Board.kt` | Mutable position with make/undoMove, castling rights, ep target |
| `engine/MoveGenerator.kt` | Legal move gen, isInCheck, result (checkmate/stalemate/draws) |
| `ai/ChessAI.kt` | bestMove() — negamax + alpha-beta; piece-square eval |
| `ads/AdIds.kt` | **Swap test IDs here before publishing** |
| `PUBLISHING.md` | Step-by-step: keystore → signed .aab → Play Console |

## Boundaries — Claude must NEVER:

- Force-push to any branch (`git push --force`)
- Delete or modify `*.jks`, `*.keystore`, or `keystore.properties` (signing keys)
- Commit `local.properties` (contains absolute SDK path, machine-specific)
- Run `rm -rf` without explicit confirmation
- Click or enable real ad unit IDs without being asked (test IDs are intentional)
- Modify `PUBLISHING.md` release steps without confirmation

## Notes for Claude

- Always run `./gradlew :app:testDebugUnitTest` after touching anything in `engine/`
- The engine is the source of truth for chess logic — the UI trusts it completely
- `AdIds.kt` has clear comments marking what to replace before publishing; do not change
  the test IDs unless the user explicitly asks to switch to production
- Before any emulator-facing change, confirm `adb devices` shows a connected device
- Read this file at the start of every session to restore project context

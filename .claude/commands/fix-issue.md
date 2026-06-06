---
name: fix-issue
description: Reads a GitHub issue, implements the minimal fix, writes a test, and commits.
argument-hint: "[issue-number]"
---

Fix GitHub issue #$ARGUMENTS for Kotlin Chess.

1. Read the full issue: run `gh issue view $ARGUMENTS`

2. Read CLAUDE.md to recall project architecture and boundaries.

3. Identify which layer the issue is in (engine, AI, UI, ads, audio) and read the relevant
   source files before touching anything.

4. If it is an engine bug:
   - Write a failing JUnit test in MoveGeneratorTest.kt that reproduces the bug first.
   - Then implement the fix in the engine.
   - Run `./gradlew :app:testDebugUnitTest` — the new test must pass AND all 9 prior tests must still pass.

5. If it is a UI/Activity bug:
   - Implement the minimal fix. Do not refactor surrounding code.
   - If engine/ was touched at all, run the test suite.

6. If it is an AdMob/sound bug:
   - Check AdManager and SoundManager in isolation first.
   - Do not change AdIds.kt test IDs unless the issue explicitly requires it.

7. Build the debug APK to confirm it compiles: `./gradlew assembleDebug`

8. Commit: `git add` the changed files, then commit with message:
   `fix: [short description of what was fixed] (closes #$ARGUMENTS)`

9. Report: what the bug was, what the root cause was, what was changed, test results.

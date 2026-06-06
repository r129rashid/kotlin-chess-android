---
name: run-tests
description: Runs the chess engine unit tests and reports results clearly.
---

Run the Kotlin Chess engine tests and report results.

1. Run the full engine test suite:
   `./gradlew :app:testDebugUnitTest --console=plain`

2. Parse the output and report:
   - How many tests passed
   - Any FAILED tests — show the test name, expected value, and actual value
   - Any compilation errors — show the file and line

3. If tests pass: confirm all 9 pass and note "engine is green".

4. If tests fail:
   - Identify which test failed and what assertion it hit
   - Look up the corresponding test in MoveGeneratorTest.kt
   - Explain what chess rule the test is checking
   - Suggest which engine file (Board.kt, MoveGenerator.kt) is likely the source
   - Do NOT fix automatically — report the finding and wait for instruction

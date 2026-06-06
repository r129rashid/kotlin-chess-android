---
name: run-review
description: Reviews recent changes against the chess project's architecture rules and correctness requirements.
---

Review the current state of this project:

1. Read CLAUDE.md to recall project context, boundaries, and architecture rules.

2. Run `git diff HEAD~1 --stat` to see what changed. If no prior commits, run `git status` instead.

3. Run `git diff HEAD~1` to read the full diff.

4. Invoke the code-reviewer agent on the changed files, with this context:
   - Engine files (engine/): check chess correctness, boundary violations, test coverage
   - AI files (ai/): check eval correctness, thread safety, null handling
   - UI files (ui/): check animation safety, thread safety, memory leaks
   - Ads files (ads/): check AdMob policy compliance, test IDs still in place
   - Audio files (audio/): check SoundPool lifecycle, release in onDestroy

5. If any engine/ files changed, run the test suite:
   `./gradlew :app:testDebugUnitTest`
   Report pass/fail count.

6. Summarize:
   - What changed and why (from git message / diff)
   - Any CRITICAL, WARNING, or SUGGESTION findings
   - Test results
   - Recommended next action

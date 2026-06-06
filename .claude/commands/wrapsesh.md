---
name: wrapsesh
description: Wraps up this Claude Code session and saves a structured note to Obsidian. Run at the end of every session.
---

You are closing out this Claude Code session. Your job is to write a clean, useful session note and save it directly to the Obsidian vault.

## Step 1 — Gather session context

Run these to understand what happened this session:

```
git diff HEAD~1 --stat 2>/dev/null || echo "no git changes"
git log --oneline -5 2>/dev/null || echo "no git log"
```

Also read CLAUDE.md to recall the project name and purpose.

## Step 2 — Build the note content

Write the note in this exact structure:

```
---
tags: [claude-session, Kotlin-Chess]
date: {{YYYY-MM-DD}}
project: Kotlin-Chess
status: wrapped
---

# Kotlin-Chess — {{YYYY-MM-DD}}

## What we did
[2-4 sentences. What was the goal of this session? What was actually built or changed?]

## Files changed
[List every file that was created, modified, or deleted. One per line with a short reason.]
- `path/to/file.kt` — [why it changed]

## Decisions made
[Any architectural, design, or technical decisions that future-you needs to know about.]
- [decision 1 and the reason behind it]

## What's working
[Things that are confirmed working after this session.]

## Open items
[Unfinished tasks, known bugs, next steps. Be specific.]
- [ ] [next thing to do]

## Notes for next session
[One paragraph. If you open this project in 2 weeks with zero memory, what do you need to know to pick up exactly where you left off?]
```

## Step 3 — Save to Obsidian

Save the note to the vault using this exact path:
`/Users/rabirashid/Rabi-AI-Projects/Obsidian-Rashid-s-Obsidian-obsidian-vault/Claude Sessions/Kotlin-Chess/{{YYYY-MM-DD}}-{{slug-of-what-we-did}}.md`

Where:
- `{{YYYY-MM-DD}}` is today's date
- `{{slug-of-what-we-did}}` is 2-4 words describing the session (e.g. `engine-tests-fixed`)

Create the folder if it doesn't exist:
```
mkdir -p "/Users/rabirashid/Rabi-AI-Projects/Obsidian-Rashid-s-Obsidian-obsidian-vault/Claude Sessions/Kotlin-Chess"
```

Then write the file with the full note content.

## Step 4 — Confirm

After saving, print:
```
wrapsesh complete.

Saved to: /Users/rabirashid/Rabi-AI-Projects/Obsidian-Rashid-s-Obsidian-obsidian-vault/Claude Sessions/Kotlin-Chess/{{YYYY-MM-DD}}-{{slug}}.md

Open items for next session:
[list the open items again, numbered]

See you next time.
```

## Rules
- Never truncate the note. Write every section fully.
- Never save to a temp file — always write directly to the vault path.
- If git is not initialized, still write the note — just skip the git sections.
- The "Notes for next session" paragraph is the most important part. Write it last, write it well.

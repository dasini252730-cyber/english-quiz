# Project instructions for Codex

## Project and source of truth
- This is a personal English-learning Android MVP. Read `요구사항/요구사항.md` for product constraints and use `backlog.json` as the task/status source of truth.
- Support Kotlin + Jetpack Compose on Galaxy S26 only. Do not add login, accounts, Supabase sync, social features, subscriptions, ads, push notifications, or excluded Fold/Flip/tablet/iOS support unless the requirements are explicitly revised.
- Keep learning records on-device: Room for structured records and DataStore for simple app state. Never put AI keys in the Android app.
- Preserve the core flow: first-use assessment → home → Conversation or Story → contextual meaning and automatic deduplicated saving → quiz → spaced repetition → results/streak → data survives restart and appears in later learning.

## Backlog workflow
- Do not read or edit the backlog JSON directly. Use `py .\backlog_cli.py list`, `show <id>`, `add`, and `update`.
- At the start of a backlog task, delegate to the `backlog-guide` custom agent. Give it the task ID; use its summary of the work in simple Korean and the relevant files it found. It updates the task's linked `backlog/<id>.md` with relevant files and a practical breakdown.
- Treat a task marked `needs_human` as a decision request. Use the criteria below to distinguish actual user decisions from technical implementation choices.
- Technical implementation decisions belong to the main agent by default, including libraries, internal architecture, test approach, storage details, API call structure, error handling, and practical defaults for models/providers. Record the chosen default and rationale, then continue.
- Use `needs_human` only for conflicting product requirements; cost, payment, or paid-service decisions; user data deletion or reset; security-sensitive credentials/account connections; changes to user experience or product direction; or choices that cannot be reasonably made without the user's preference. Uncertainty alone is not a reason to stop.
- Before asking the user to decide, inspect the code, requirements, backlog, `CLAUDE.md`/rules, and existing decision records. Do not ask again about a decision already recorded in conversation or project documentation.
- If a task needs a human decision, continue any independent ready task. Keep the blocked task waiting and reactivate it when the decision is recorded.
- Respect `depends_on` for completion and integration: do not mark a dependent task `done` while a prerequisite is not `done`. If a prerequisite is waiting on device or infrastructure evidence, continue implementable portions and other backlog work in order; record those portions as preparation and keep the dependent task waiting until its full acceptance criteria and prerequisite evidence are met.
- Before finishing each created or modified source file, ask the `code-critic` custom agent for an adversarial review of that file and the change. Its scope is limited to: missed requirements, regressions, incorrect dependencies, security, data loss, race/lifecycle/navigation defects, whether tests prove `done_when`, unnecessary complexity, and changes outside scope. It never edits files and only reports findings; the main agent decides whether and how to fix them, then may request a follow-up review.
- Update backlog status through the CLI as work moves. Use `review_needed` when implementation is ready for review and `done` only after its acceptance criteria are met.

## Implementation and quality
- Prefer small, focused files and established project patterns. Per-file limits live in `.codex/line-limits.json`: warn at 85% and refactor if a file exceeds its limit.
- Run the configured lint and build checks before finishing code work. Codex's Stop hook enforces the checks available for changed code. For Android changes, use the Gradle wrapper's `lint` and `assembleDebug`; for Python tooling, run the configured Python syntax/lint/build checks.
- Do not claim checks passed when a required tool or project build setup is missing. Report the blocker and leave the task out of `done`.
- For fixes, check the smallest relevant test first, then the required project lint/build gates.

## Git and session rules
- At session start, report the current branch. If it is `main`, tell the user to switch to `dev` before implementation; do not silently switch branches.
- A successful backlog CLI add/update is committed by the Codex PostToolUse hook. Marking a task `done` triggers a summary commit of outstanding work and a push of the current branch.
- Never push unrelated changes except as part of the explicitly requested `done` workflow. If this folder is not a Git repository or has no configured remote, report that the relevant hook action could not run.

## Communication
- Explain technical work in clear Korean unless the user asks otherwise. Report changed files, checks, and any blocked hook action.

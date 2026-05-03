# AGENTS.md

This repository expects disciplined delivery. Prefer clarity, testability, and maintainability over short-term speed.

## Project quick context

- The long-term host platform is iPhone; Android host code in this repository is prototype or reference code unless explicitly reaffirmed.
- Meebook-side Android work is first-class product work.
- Child-facing UI should stay minimal and avoid technical/debug detail.
- The target offline map architecture is device-local PMTiles packages, not ad hoc generated basemap payloads as a hidden default.
- See `docs/engineering-model.md` for the fuller operating model and repository-specific architecture rules.

## Core standard

- Plan non-trivial work before implementation sprawls.
- Keep architecture intentional; do not add hidden coupling or ad hoc structure.
- Prefer small, explicit modules with narrow responsibilities.
- Refactor when a change would otherwise add duplication, mixed concerns, or logic trapped in hard-to-test UI/framework code.
- Treat repository hygiene, issue hygiene, PR hygiene, and CI hygiene as part of the implementation.
- If a change would weaken these standards, tighten the implementation, split the scope, or create an explicit follow-up issue before merge.

## Planning and status

- Every non-trivial change should map to a GitHub issue.
- Default scope is one reviewable issue per pull request unless a small combined slice is clearly better.
- If work grows beyond the original scope, split the remaining work into new issues instead of silently expanding the change.
- Treat GitHub issue state, PR state, and Project board state as one combined source of truth when reporting status.
- When checking status, verify both the item state and whether it is in `Todo`, `In Progress`, or `Done` on the active project board.
- If GitHub or project-board state is not available from the current environment, say that explicitly instead of guessing.
- Do not leave implicit code TODOs as the only record of deferred work.

## Test policy

- Every feature should add at least one meaningful automated test case.
- Every issue should either add a test, extend relevant coverage, or explicitly document why automated coverage is not yet practical.
- Bug fixes should include a regression test whenever technically possible.
- Extract pure logic into testable units instead of leaving it trapped in activities, views, or event handlers.
- UI-heavy changes should still pull decision logic into testable code where practical.

## CI and merge rules

- Keep GitHub Actions green.
- The required checks for `main` are `frontend-tests` and `android-tests`.
- Do not merge changes that weaken CI coverage without replacing that coverage elsewhere.
- `main` is protected and should be updated through pull requests, not direct pushes, unless the user explicitly requests a small repository-process or documentation change on `main`.
- Do not bypass review, required checks, branch protection, or unresolved conversations.

## Branch and PR expectations

- On non-`main` branches, once the user asks for a commit, PR, or CI-visible update, pushing commits is the default expectation unless the user asks to keep work local.
- Reference the driving issue in the PR.
- Keep PRs scoped so reviewers can evaluate behavior, architecture, and tests without reconstructing intent from the diff.
- Summarize behavior changes, refactors, and risks separately when they are materially distinct.
- Call out missing tests explicitly.
- If you touch messy code, leave it cleaner; do adjacent safe cleanup in the same PR, and track broader cleanup in a follow-up issue.

## Anti-patterns

- No silent fallbacks that hide failure modes.
- No large unstructured files mixing domain logic, UI orchestration, and integration glue.
- No testless feature work unless the limitation is explicit.
- No silent architectural drift toward Android-host-specific expansion when shared mission-domain code is the better direction.

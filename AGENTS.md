# AGENTS.md

This repository is run with strict delivery rules. Agents working here are expected to preserve clarity, testability, and long-term maintainability over short-term speed.

## Central standard

Professional engineering behavior is a core repository requirement, not a preference.

That means:

- work is planned before it sprawls
- architecture is kept intentional
- refactoring is expected when code starts to drift
- every feature gets meaningful automated coverage
- every issue should add test coverage or explicitly explain why that is not yet practical
- no one treats temporary shortcuts as invisible debt
- repository hygiene, issue hygiene, PR hygiene, and CI hygiene are part of the implementation itself

If a proposed change would weaken those standards, the correct action is to tighten the implementation, split the work, or create explicit follow-up issues before merge.

See `docs/engineering-model.md` for the repository operating model that those standards map to.

## Core principles

- Do not let the codebase drift into hidden coupling or ad-hoc structure.
- Prefer small, explicit modules with narrow responsibilities.
- Refactor when a feature would otherwise add duplication, fragile branching, or mixed concerns.
- Keep domain logic out of framework-heavy entrypoints when possible.
- Make architectural tradeoffs explicit in pull requests and issue comments.

## Planning and issue hygiene

- Every non-trivial change should map to a GitHub issue.
- If implementation reveals missing work, create a follow-up issue before merging.
- Do not leave implicit TODOs in code as the only record of deferred work.
- If a task grows beyond its original scope, split the remaining work into new issues instead of silently expanding the change.

## Test policy

- Every feature should come with at least one meaningful automated test case.
- As a default rule, every issue should either:
  - add a new automated test case,
  - extend an existing test suite with coverage for the changed behavior, or
  - explicitly document why automated coverage is not yet practical.
- Bug fixes should include a regression test whenever technically possible.
- Pure logic belongs in testable units rather than being trapped in activities, views, or event handlers.
- UI-heavy changes should still extract testable decision logic where possible.

## CI expectations

- Keep the GitHub Actions checks green.
- The required checks for `main` are:
  - `frontend-tests`
  - `android-tests`
- Do not merge changes that weaken CI coverage without replacing that coverage elsewhere.

## Branching and merge rules

- `main` is protected and should only be updated through pull requests.
- Do not bypass review, required checks, or conversation resolution.
- Assume branch protection is intentional and design work to fit inside it.
- On non-`main` branches, pushed commits are the default expectation once the user asks for a commit, PR, or CI-visible update.
- Do not leave requested work stranded only in the local checkout unless the user explicitly asks to keep it local.

## Pull request expectations

- Reference the driving issue.
- Summarize behavior changes, refactors, and risks separately when they are distinct.
- Call out missing tests explicitly instead of implying they are covered.
- Keep pull requests scoped so that they can be reviewed for architecture, behavior, and test quality.

## Refactoring rule

- If touching messy code, leave it cleaner than you found it.
- If a safe cleanup is directly adjacent to the change, do it in the same pull request.
- If a broader cleanup is needed but would blur the scope, create a follow-up issue and reference it.

## Anti-patterns

- No silent fallbacks that hide failure modes.
- No large unstructured files that mix domain logic, UI orchestration, and integration glue.
- No testless feature work unless the limitation is explicit and documented.
- No direct pushes to `main`.

# Contributing

This repository is intentionally run with a strict engineering workflow.

## Central standard

Professional software engineering discipline is a central repository requirement.

This is not optional polish after features are done. It is part of done.

In practice that means:

- plan work before adding code
- keep architecture readable and scalable
- refactor when a change would otherwise increase entropy
- add automated tests for every feature
- as a default rule, ensure every issue adds or updates a meaningful test case
- document and track deferred work explicitly instead of letting it disappear into the codebase
- keep CI, review quality, and issue tracking aligned with the implementation

See `docs/engineering-model.md` for the repository operating model.

## Delivery rules

- `main` is protected and should only be updated through pull requests.
- Every non-trivial change starts from an issue or explicitly references one in the pull request.
- New behavior must come with automated tests or a written explanation of why a test is not yet possible.
- Refactors are expected when a change would otherwise increase coupling, duplication, or hidden complexity.
- "Fix later" is not a valid default. Capture deferred work as a GitHub issue before merging.

## Clean code expectations

- Keep modules small and responsibility-focused.
- Prefer extracting pure logic into testable units instead of hiding behavior in activities, views, or event handlers.
- Avoid silent fallbacks unless the user experience explicitly demands them.
- Name code by domain intent, not implementation trivia.
- Leave the codebase easier to reason about than you found it.
- If a change would make the codebase less controllable, stop and restructure the change.

## Planning and implementation

- Start with the smallest change that preserves the long-term architecture.
- Document architecture-impacting decisions in the pull request description.
- When a feature introduces future work, create follow-up issues and link them.
- If a change crosses UI, integration, and domain logic, keep those slices separated in the code review.

## Testing

- Frontend logic in `app/src/main/assets/web/` should be kept testable through small exported modules.
- Kotlin logic that does not require device APIs should live in unit-testable classes.
- Pull requests should keep the GitHub Actions checks green:
  - `frontend-tests`
  - `android-tests`

## Pull requests

- Keep pull requests scoped.
- Include the user-facing impact, technical approach, and rollback risk.
- Call out refactors separately from feature behavior when both happen together.
- Do not merge if CI is red, review comments are unresolved, or follow-up work is only implicit.

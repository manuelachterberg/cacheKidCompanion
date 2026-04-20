# Engineering Model

This document defines how work is planned, implemented, tested, reviewed, and merged in this repository.

It is intentionally strict. The goal is to keep the codebase scalable, reviewable, and under control as features accumulate.

## 1. Work starts from an issue

Every non-trivial change should begin from a GitHub issue.

An issue is only ready when it makes these things clear:

- what problem is being solved
- what is explicitly out of scope
- what "done" means
- what test case or test strategy is expected

If an issue is too vague to review before implementation, it is too vague to implement safely.

## 2. Default scope rule

The default workflow is:

- `1 issue -> 1 pull request`

It is acceptable to group two or three issues into one pull request only when they form one tight, reviewable slice.

Do not build broad "while I was here" pull requests that mix unrelated progress.

## 3. Pull request size rule

A pull request is too large when:

- it spans multiple architectural concerns without clear boundaries
- it mixes feature work, integration work, and cleanup without structure
- the reviewer would need to reconstruct the intended architecture from the diff

Small, focused pull requests are preferred over large "complete system" changes.

## 4. Refactoring rule

Refactoring is part of delivery, not optional cleanup.

Refactor in the same pull request when:

- the change would otherwise introduce duplication
- responsibilities are becoming mixed
- a file or module is becoming harder to reason about
- extracting logic is the right way to make the behavior testable

Create a follow-up issue instead of refactoring immediately when:

- the cleanup would blur the scope of the pull request
- it would materially increase review size or risk
- it is broader than the local area being changed

Rule of thumb:

- local cleanup now
- broad cleanup as an explicit follow-up issue

## 5. ADR rule

Create an architecture decision record when a decision:

- affects multiple future issues
- is expensive to reverse
- has meaningful alternatives with tradeoffs

Examples in this repository:

- native Android versus hybrid architecture
- smartphone host versus live internet on the Meebook
- transfer protocol choice
- offline map stack choice
- BLE usage boundaries

Do not create ADRs for every implementation detail. Use them for directional choices.

## 6. Test rule

Every feature should add at least one meaningful automated test case.

As a default rule, every issue should either:

- add a new automated test case
- extend an existing test suite with relevant coverage
- explicitly document why automated coverage is not yet practical

Bug fixes should include a regression test whenever technically possible.

Manual testing may complement automation, but it does not replace it as the default standard.

## 7. Test pyramid for this repository

Prefer:

- many unit tests for pure logic
- some integration tests for boundaries and adapters
- few expensive UI or end-to-end tests for critical flows

For this repository, that usually means:

- unit tests for parsing, validation, mission models, and navigation logic
- integration tests for packaging, transfer boundaries, and device-facing adapters where practical
- limited UI tests for critical user flows only

Do not push logic into UI layers where it becomes hard to test.

## 8. Merge rule

`main` should remain releasable.

Changes merge only through pull requests with:

- green required CI checks
- required review approval
- resolved review conversations
- branch protection respected

Do not bypass branch protection to "move faster".

## 8a. Branch cleanup rule

Merged working branches should normally be deleted.

That applies to:

- remote feature or chore branches after the pull request is merged
- local branches once the merged work is no longer needed for active development

Why:

- it keeps the branch list readable
- it reduces the chance of continuing work on stale history
- it makes the current delivery path obvious

Exceptions should be rare and explicit. Long-lived branches should exist only when there is a clear ongoing purpose.

## 9. Issue slicing rule

A good issue is:

- focused
- reviewable
- testable
- small enough to complete cleanly in one pull request

Bad examples:

- "build the whole map system"
- "finish the host app"

Good examples:

- ingest cache data from Android share intent
- validate mission draft input
- receive one mission package on the Meebook
- render route overlays on the map

## 10. Too-big signal

Stop and rescope when:

- the pull request no longer has one obvious review focus
- tests are being deferred until "after the main work"
- the change introduces visible architectural debt to save time
- the work now requires multiple independent explanations

When that happens:

- split the issue
- split the pull request
- or create explicit follow-up issues before merge

## 11. Definition of done

Work is done when:

- the scoped behavior is implemented
- the architecture remains understandable
- necessary refactoring has been done or captured explicitly
- automated tests cover the new or changed behavior
- CI remains green
- review can happen without guessing intent

## 12. Pre-implementation checklist

Before starting implementation, capture these five things:

- scope
- non-scope
- expected test case
- refactor need
- main risk

This should be lightweight, but it should exist.

## 13. Kid UI separation rule

Kid-facing screens must stay intentionally minimal.

The product should clearly separate:

- child-facing navigation views
- parent-facing setup views
- debug or technical views

Child-facing navigation should avoid exposing technical detail unless it is essential for the child to complete the task.

As a default rule, the child view should favor:

- the treasure map
- the route or next waypoint
- the large target marker
- at most a very small number of clear actions

It should avoid exposing development-oriented values such as:

- raw heading values
- sensor source labels
- technical permission state
- setup-oriented controls

If a screen needs technical controls, it should default to a parent or debug mode instead of becoming part of the standard child flow.

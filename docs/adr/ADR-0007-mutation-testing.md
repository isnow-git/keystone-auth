# ADR-0007: Mutation testing on the rules layers

## Status

Accepted

## Context

`:domain` reaches 97.7% line / 95.7% branch coverage. `:application` clears
its 80% line gate. Both numbers measure **what is exercised** — they do not
measure **whether the assertions actually constrain behaviour**.

A line is covered as soon as it runs. A test that calls `register(...)` and
asserts `result != null` covers every line but kills almost nothing if the
behaviour silently changes (e.g. `Email.of("bad")` stops throwing). Coverage
percentages alone create the illusion of safety on a codebase whose whole job
is enforcing security invariants — exactly where the illusion is most
dangerous.

## Decision

Apply **PIT** (`info.solidsoft.pitest` Gradle plugin) to `:domain` and
`:application`. PIT mutates the bytecode (flips conditionals, swaps return
values, removes operations, etc.) and reports which mutations the test suite
kills.

- `targetClasses` and `targetTests` scoped per module.
- `STRONGER` mutator set — covers the standard mutations plus a few that catch
  subtle invariant breaks.
- Reports under `<module>/build/reports/pitest/index.html`; XML for tooling.
- **Not gated yet.** The first run sets a baseline score; we raise the bar
  intentionally rather than blocking unrelated PRs on flaky improvements.

Baseline at adoption (on the rules layers):

| Module          | Mutations | Killed | Score |
|-----------------|-----------|--------|-------|
| `:domain`       | 95        | 81     | **85%** (test strength 89%) |
| `:application`  | (rerun)   | (rerun)| see report |

## Consequences

**Positive**

- Tests are now graded on whether they actually constrain code, not whether
  they happen to execute it. A surviving mutation is a concrete bug-shaped
  hole in the suite.
- Worth more than coverage numbers when reviewers ask "how do I know these
  tests are good?".
- Adds ~30 seconds to a full `:domain:pitest` run; trivial on CI.

**Negative**

- PIT runs the suite many times in parallel; flaky tests amplify. We have
  none yet, but any future timing-dependent test will be caught hard.
- The plugin requires running tests in JUnit Platform mode, which we already
  do.
- Some mutations are equivalent (no observable behaviour change). Score
  approaches but rarely reaches 100% — that's expected.

**Trade-off accepted.** Coverage is a smoke check; mutation testing is the
actual signal. Surviving mutations become the worklist for the next round of
test tightening.

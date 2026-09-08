<!--
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# AGENTS.md

Unit test conventions for this package. They apply to the Angular shell in `src/`, package-level infrastructure specs under `test/`, and the libraries under `projects/` that have no file of their own: `zeppelin-sdk`, which is framework-neutral, and `zeppelin-visualization`, which is mostly so apart from one `@Component` base class.

Two subtrees override this file: [`e2e/AGENTS.md`](e2e/AGENTS.md) for the Playwright suite, and [`projects/zeppelin-react/AGENTS.md`](projects/zeppelin-react/AGENTS.md) for the React remote, which has a different CI status and one exception of its own.

The repository root `AGENTS.md` asks every change to include unit tests. This file is how.

## Layout

- A product-code spec lives next to its source: `foo.ts` / `foo.spec.ts`. Specs for package-level test and reporting infrastructure live under `test/`.
- The runner is Vitest on jsdom. There is no Karma. `test/test-setup.ts` loads `zone.js` and reflection metadata, initializes Angular `TestBed`, and resets the test environment after each spec.
- `npm run test:shell` covers `src/`, package-level specs under `test/`, `projects/zeppelin-sdk` and `projects/zeppelin-visualization`. The two libraries have no runner of their own; they ride on the shell config because their code needs nothing extra. `projects/zeppelin-react` is separate. It has its own Vitest config and its own file here.

## Running

| Command | Purpose |
| --- | --- |
| `npm run test:shell` | Run the unit tests for `src/`, `test/` and the two libraries |
| `npm run test:shell -- --coverage` | Same, with a coverage report |
| `npm run test:shell -- foo.spec.ts` | Run one file |

`test:shell` is bound to the Maven `test` phase (`pom.xml`), so a spec added here starts running in CI the day it merges. It does not run where you would expect. `frontend.yml` builds this module with `-DskipTests`, which frontend-maven-plugin honours by skipping `test`-phase executions, so the run that counts is `mvnw verify -Pweb-e2e` inside the `run-playwright-e2e-tests` job. A failing spec surfaces there, under an e2e job name. Giving the unit tests a step of their own is [ZEPPELIN-6566](https://issues.apache.org/jira/browse/ZEPPELIN-6566).

## Where a test belongs

The frontend has two test layers, not three. There is no integration tier.

| Layer | Runner | Answers |
| --- | --- | --- |
| Unit | Vitest + jsdom | Is the judgement we wrote correct? |
| E2E | Playwright | Does the page actually work in a browser? |

Prefer a unit test when the question can be answered without a browser. Reach for e2e when the answer depends on wiring: routing, mounting a federated remote, authentication, or anything a user would have to click.

The two layers do not substitute for each other, and neither replaces the cross-framework parity checks the React migration needs.

A third kind is planned but does not exist yet: contract specs that replay captured WebSocket traffic against the notebook runtime, arriving with [ZEPPELIN-6627](https://issues.apache.org/jira/browse/ZEPPELIN-6627). Those will run on Vitest as well and live under `test/contract/`, with the captured traffic beside them. Conventions for them are added once they exist.

## What to test

The judgement we wrote: branches, boundaries, error paths.

If a function has an `if`, it is worth a spec. If it only forwards to a framework API, it usually is not.

Boundaries are where the bugs are. `HumanizeBytesPipe` switches units at 1000 but divides by 1024, so `transform(1000)` renders `0.98 KB`. A spec pins that down, a reviewer's intuition does not.

## What not to test

- **Framework internals.** Change detection, lifecycle ordering, dependency injection itself. Test the code we wrote, not Angular.
- **Template render snapshots.** Markup changes when a surface is restyled or migrated; a snapshot only records that it changed.
- **Flows already covered by e2e.** Wiring between components, navigation, and anything that needs a real browser belongs in `e2e/`.
- **Functions with no logic to check.** `element.ts` feature-detects a DOM API and forwards to it. A spec would assert that a mock was called.

## Specs that cannot fail

A spec with no assertion, or one whose assertion sits inside an `if`, passes by skipping the check it exists to make. These are caught by lint, not review:

| Rule | Catches |
| --- | --- |
| `vitest/expect-expect` | a test with no assertion |
| `vitest/no-conditional-expect` | an assertion only some runs reach |
| `vitest/no-identical-title` | a duplicate name silently shadowing another |
| `vitest/valid-expect` | `expect(x)` with no matcher |
| `vitest/no-focused-tests` | `it.only` left behind, hiding the rest |
| `vitest/no-disabled-tests` | `it.skip` left behind (warning) |

The e2e suite gets the same protection from `eslint-plugin-playwright`.

## monaco-editor and path aliases in specs

`vitest.shell.config.mts` mirrors the `paths` block in `tsconfig.base.json`, which Vite does not read; add an alias there when you add a path. Eleven files under `src/` import `monaco-editor`, two behind the `@zeppelin/services` barrel, so a spec that reaches the editor or notebook area loads it: a few seconds on first import and a `marked.umd.js.map` sourcemap warning, both monaco's, not ours. Mock it with `vi.mock('monaco-editor', ...)` when the spec only needs to assert the editor was called. The runner resolves monaco to `editor.api`, which skips the language registrations `editor.main` performs, so do not assert that a built-in language is registered.

## Determinism

No clock, no randomness, no network. A spec that reads `Date.now()` or fetches will eventually fail for reasons unrelated to the code under test.

## Naming

The failure message must say what broke. `should work` does not.

```ts
it('renders a dash for null and undefined', ...)     // good
it('handles input', ...)                             // not a test name
```

## Angular classes with and without TestBed

Construct directly testable directives, pipes and services as plain classes when Angular framework wiring is not the subject of the spec. Pass mocks to the constructor instead of starting `TestBed` unnecessarily.

```ts
const loader = { loadModule } as Pick<ReactRemoteLoaderService, 'loadModule'>;
const directive = new ReactMountDirective(host, ngZone, loader as ReactRemoteLoaderService);
```

See `src/app/share/react-mount/react-mount.directive.spec.ts` for a worked example, and `src/app/share/pipes/humanize-bytes.pipe.spec.ts` for a pipe.

Use `TestBed` when the behavior depends on template bindings, dependency injection, change detection, or Angular lifecycle wiring. `src/app/share/react-mount/react-mount.directive.testbed.spec.ts` shows that path with a decorated host component. Keep the direct-construction spec beside it for behavior that does not need Angular wiring.

## Migration (Angular to React)

**Write the spec while Angular is still the source of truth.**

A spec written after a surface moves to React pins the new implementation's behaviour, not the behaviour we were trying to preserve. That turns a regression into the expected result. The migration ([ZEPPELIN-6627](https://issues.apache.org/jira/browse/ZEPPELIN-6627)) gates on "does this still behave the same?", which cannot be judged when the previous behaviour was never written down.

`src/app/pages/workspace/notebook/paragraph/paragraph-patch.spec.ts` is the pattern: a regression was fixed and the behaviour was pinned in the same change.

## Coverage

`--coverage` produces a v8 report under `coverage/`. It is measured, not gated. There are no thresholds.

**Read the percentage carefully: it is not whole-tree coverage.** The denominator is only the files the specs actually load, because `include` is left unset (see `vitest.shell.config.mts`). Coverage parsing is separate from the test transform, so the whole tree must be deliberately remeasured before that setting is widened. Most of the tree is absent from the report rather than counted as zero, so the figure reads far better than the real state, and it can *fall* as specs are added, since each new spec pulls more files into the denominator. Expect a large drop when `include` is eventually turned on.

That is deliberate. `src/` currently holds a few specs against more than 200 source files, so any threshold set today is either meaningless or permanently red. The intended progression is: measure only, then ratchet so the number cannot fall, then require coverage on changed files. A whole-tree percentage is the wrong target during a migration, because much of the tree is going to be rewritten anyway.

This is a different measurement from `e2e/reporter.coverage.ts`, which counts annotated component pages rather than lines. The two numbers are not comparable and are not merged.

## Adding a Test (Agents Start Here)

1. Pick a target with logic to check: a branch, a boundary, an error path.
2. Create `foo.spec.ts` next to `foo.ts`.
3. Import from `vitest` (`describe`, `expect`, `it`), not from Jasmine or Jest.
   Check the target has callers before you invest in it. `get-keyword-positions.spec.ts` is a worked example of a function that turned out to have none.
4. Construct the class directly unless the behavior depends on Angular wiring; use `TestBed` when it does.
5. Run `npm run test:shell` and confirm it passes before opening a PR.

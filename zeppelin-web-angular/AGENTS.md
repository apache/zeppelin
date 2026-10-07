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

Unit test conventions for this package. They apply to the Angular shell in `src/`, package-level infrastructure specs under `test/`, and the libraries under `projects/` that have no file of their own: `zeppelin-notebook-core` and `zeppelin-sdk`, which are framework-neutral, and `zeppelin-visualization`, which is mostly so apart from one `@Component` base class.

Two subtrees override this file: [`e2e/AGENTS.md`](e2e/AGENTS.md) for the Playwright suite, and [`projects/zeppelin-react/AGENTS.md`](projects/zeppelin-react/AGENTS.md) for the React remote, which has a different CI status and one exception of its own.

The repository root `AGENTS.md` asks every change to include unit tests. This file is how.

## Layout

- A product-code spec lives next to its source: `foo.ts` / `foo.spec.ts`. Specs for package-level test and reporting infrastructure live under `test/`.
- The shell runner is Vitest on jsdom. There is no Karma. `test/test-setup.ts` loads `zone.js` and reflection metadata, initializes Angular `TestBed`, and resets the test environment after each spec.
- `npm run test:shell` covers `src/`, package-level specs under `test/`, `projects/zeppelin-sdk` and `projects/zeppelin-visualization`. The two libraries have no runner of their own; they ride on the shell config because their code needs nothing extra. `projects/zeppelin-react` is separate. It has its own Vitest config and its own file here.
- `projects/zeppelin-notebook-core` has a dedicated Vitest config (`vitest.notebook-core.config.mts`) using Node without Angular test setup. It covers the package's contract specs and the dependency-boundary checks under `test/notebook-core`. These specs are not part of the shell or React suites. The core compiler excludes DOM libraries and application path aliases.

## Running

| Command | Purpose |
| --- | --- |
| `npm run test:shell` | Run the unit tests for `src/`, `test/` and the two libraries |
| `npm run test:shell -- --coverage` | Same, with a coverage report |
| `npm run test:shell -- foo.spec.ts` | Run one file |
| `npm run test:react` | Run the React remote unit tests |
| `npm run typecheck:react` | Typecheck the React remote without emitting files |
| `npm run test:notebook-core` | Run the dedicated notebook-core Node suite |
| `npm run typecheck:notebook-core` | Check core source and specs, rebuild the package, and check the React type-only contract against built declarations and the same core source |
| `npm run typecheck:sdk-contracts` | Check the `zeppelin-sdk` specs, including their [type assertions](#type-assertions) |

`test:shell`, `test:react`, `typecheck:react`, `test:notebook-core`, `typecheck:notebook-core`, and `typecheck:sdk-contracts` are bound to the Maven `test` phase (`pom.xml`), so a spec added here starts running in CI the day it merges. `frontend.yml` builds this module with `-DskipTests`, which frontend-maven-plugin honours by skipping `test`-phase executions, so all of them run inside `mvnw verify -Pweb-e2e` in the `run-playwright-e2e-tests` job, on both legs. `test:shell`, `test:react`, and `typecheck:react` also run as named steps on the anonymous leg, so their failures are reported under their own step names. A failure in the other three still surfaces under an e2e job name.

## Where a test belongs

The frontend has two test layers, not three. There is no integration tier.

| Layer | Runner | Answers |
| --- | --- | --- |
| Unit | Vitest + jsdom (shell/React) or Node (notebook-core) | Is the judgement we wrote correct? |
| E2E | Playwright | Does the page actually work in a browser? |

Prefer a unit test when the question can be answered without a browser. Reach for e2e when the answer depends on wiring: routing, mounting a federated remote, authentication, or anything a user would have to click.

The two layers do not substitute for each other, and neither replaces the cross-framework parity checks the React migration needs.

The notebook-core host/remote contract is currently a type-only scaffold; it does not implement a notebook runtime.

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

## Type assertions

`expectTypeOf`, `assertType` and `@ts-expect-error` are erased before a spec runs, so Vitest passes them whatever they say. Only a `tsc` pass over the spec checks them, and only three spec programs have one in Maven:

| Specs | Checked by |
| --- | --- |
| `projects/zeppelin-sdk` | `typecheck:sdk-contracts` |
| `projects/zeppelin-notebook-core`, `test/notebook-core` | `typecheck:notebook-core` |
| `projects/zeppelin-react` | `typecheck:react` |
| `src/`, the rest of `test/`, `projects/zeppelin-visualization` | nothing yet |

Lint encodes the table: `vitest/expect-expect` accepts a type assertion as a test's only assertion in the first three rows and rejects it elsewhere. A type assertion in an unchecked spec, even beside an `expect`, cannot fail. When a program gains a `tsc` pass in Maven, add its glob to the `settings: { vitest: { typecheck: true } }` block in `eslint.config.js`, or set the same in `projects/zeppelin-react/eslint.config.js`.

- Put type assertions in an ordinary `.spec.ts`, never a separate type-test file or directory. One SDK interface file declares many unrelated types, so a type contract spec sits beside that file and is named for the contract it pins: `notebook-wire-fields.spec.ts`, `completion-item.spec.ts`.
- To show a value is accepted, pass it to `assertType<T>(...)`. A typed variable then needs a use, and a runtime `expect` on a literal only restates the literal.
- Prefer an exact matcher to `@ts-expect-error`. `expectTypeOf<CompletionItem>().toHaveProperty('name').toEqualTypeOf<string>()` fails when `name` becomes optional; `@ts-expect-error` is satisfied by any error on the next line, a typo included. Keep the directive for what no matcher can say, such as assigning to a `readonly` member (`host-remote-contract.spec.ts`), with one statement under it and the expected failure after it.
- A type assertion pins what the SDK declares, not what the server sends. Payload shapes are evidenced by the server code that builds them, and later by the captured-traffic contract specs described above.

`vitest --typecheck` with `*.test-d.ts` files is not used: it is still experimental, and it reports a file its tsconfig does not include as passed ([vitest#7988](https://github.com/vitest-dev/vitest/issues/7988)). [TSTyche](https://tstyche.org) checks the message after `@ts-expect-error` and has no such gap, but it is a second runner for a handful of files. Revisit it if must-not-compile contracts multiply or a swallowed error is found.

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
5. Run `npm run test:shell` for shell/SDK/visualization changes, plus `npm run typecheck:sdk-contracts` for SDK changes. For notebook-core changes, run `npm run test:notebook-core` and `npm run typecheck:notebook-core`. Confirm the relevant checks pass before opening a PR.

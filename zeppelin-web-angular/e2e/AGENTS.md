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

> E2E (Playwright) conventions for `zeppelin-web-angular/e2e/`. Loaded only when working under `e2e/`; the package baseline is `zeppelin-web-angular/AGENTS.md`, which covers unit tests. See [AGENTS.md specification](https://github.com/agentsmd/agents.md).

Config: `zeppelin-web-angular/playwright.config.js` (Angular UI) and `playwright.classic.config.js` (legacy classic UI), sharing `playwright.shared.js`. This document is the source of truth for E2E conventions, for contributors and for coding agents alike.

## Layout

- Specs: `e2e/tests/<area>/[<group>/]<feature>.spec.ts` (areas: `authentication`, `home`, `login`, `notebook`, `share`, `theme`, `workspace`). Larger areas group specs one level deeper, as in `notebook/keyboard/` and `workspace/notebook-repos/`. `tests/app.spec.ts` covers the app shell and sits outside any area.
- Page Objects (POM), split by role:
  - `e2e/models/<name>.ts`: locators + primitive actions (click, fill, navigate, simple state checks).
  - `e2e/models/<name>.util.ts`: workflows, composite verification, scenario helpers.
  - Most existing POMs are a single file. Split a new one by role, and split an existing one when its workflow code outgrows its locators.
- Shared helpers: `e2e/utils.ts`.

## Style

- English only. No unnecessary comments.
- BDD via `test.step('Given/When/Then …', …)`. Steps show up in traces and reports; `// Given:` comments do not. Some specs still use comments; migrate a test's comments to steps when you touch it.
- One `test.describe` per feature; construct the feature's own POM in `beforeEach`. A secondary POM that only one test needs, such as the second viewer in a collaboration test or a page reached mid-test, can be built in the test body.
- `test.describe.serial` is a last resort: one failure skips every later test in the group, which hides the rest instead of reporting them. Playwright recommends against it (https://playwright.dev/docs/test-parallel#serial-mode). Prefer making each test set up its own state.

## Detailed Guidance

Before changing locators, assertions, deliberate rule exceptions, migration
feature-flag coverage, or Classic UI tests, read the
[detailed E2E guidance](../../.agents/e2e.md).

The rules that apply to every test are:

- Prefer role, label, or text locators with exact accessible names, then
  `data-testid`; keep CSS in Page Objects and never use XPath.
- Use web-first assertions and observable readiness signals. Fixed waits and
  one-shot visibility checks require a documented exception.
- Mark a convention exception with `// JUSTIFIED: <reason>`; use a reasoned
  `eslint-disable-next-line` only for lint-only exceptions.
- Keep migration specs framework-neutral. The detailed guide records the
  feature flags and the explicit exceptions for the frozen Classic UI.

## Readiness & Auth

- After navigation, wait with `waitForZeppelinReady(page)` from `e2e/utils.ts` (not fixed sleeps).
- Auth is programmatic: the `setup` project logs in once and writes `playwright/.auth/user.json`; browser projects consume it via `storageState`. Do not add per-test login races. For logged-out scenarios use a fresh context.
- A skip says why it skipped. `playwright/no-skipped-test` errors on the declaration forms (`test.skip('title', fn)`, `test.describe.skip`) and on a bare `test.skip()` outside an `if`; those need the `eslint-disable` hatch and a tracking key. Every other skip passes lint whatever its message says, so the message is a convention, not a gate: name the missing capability (auth mode, interpreter, environment feature) or the tracking key.

## Coverage Annotation (Required)

Every `describe` must declare the page/component it exercises so coverage is attributed:

```ts
import { addPageAnnotationBeforeEach, PAGES } from '../../utils';

test.describe('Home Page - Core Elements', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.HOME);
  // …
});
```

Use an existing key from the `PAGES` object in `e2e/utils.ts`; add a new one there if the page is missing. The reporter discovers `src/app/**/*.component.ts` automatically and removes only the entries in `COVERAGE_EXCLUDED_COMPONENTS`, so component additions, deletions and moves update the denominator automatically. `PAGES` separately supplies the annotation names and must match those discovered targets. `test/reporter.coverage.spec.ts` enforces that match, rejects duplicate entries and verifies that every explicit exclusion still exists. Purely structural / non-page components (lifecycle hooks, shared UI primitives like the spinner or resize handle) are exercised transitively and are not counted.

## Adding a Test (Agents Start Here)

1. Pick/confirm the target route and the `PAGES` key.
2. Copy the shape of an existing spec in the same `<area>`; reuse or extend the matching POM (`models/<name>.ts` + `.util.ts`). Do not inline selectors the POM already owns.
3. Annotate the page (`addPageAnnotationBeforeEach`), navigate, then `waitForZeppelinReady`.
4. If the test covers a scenario in `e2e/scenarios/notebook-parity.json`, add its stable ID as a Playwright tag such as `{ tag: '@NB-PARITY-001' }`. Keep the title human-readable; the registry links coverage by tag and path. Browser execution controls such as project lists and skip conditions stay in the spec rather than being copied into the registry. Keep browser assumptions in the registry only when they define the scenario's behavior or expected outcome.
5. Run `npm run e2e:fast` and iterate until green.

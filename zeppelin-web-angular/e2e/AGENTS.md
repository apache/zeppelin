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

> E2E (Playwright) conventions for `zeppelin-web-angular/e2e/`. A scoped companion to the repository-root AGENTS.md, loaded only when working under `e2e/`. See [AGENTS.md specification](https://github.com/agentsmd/agents.md).

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

## Escape hatches

Two comment forms mark a deliberate rule violation, and both require a reason:

- `// JUSTIFIED: <why>` for the conventions in this document, either trailing the offending line or in the comment block directly above it. It is a contract with the reviewer: the marker says the deviation is deliberate and the reason says why. A `test.describe.serial` group needs one too.
- `// eslint-disable-next-line <rule> -- <why>` for a lint rule. Give a reason after the `--`; if the violation is tracked elsewhere, the ticket key is that reason.

When a rule is both a convention here and a lint rule, `// JUSTIFIED:` is the one to use: an `eslint-disable` silences the linter but leaves the convention unmet.

Neither hatch is a way to opt out of thinking. One without a concrete reason will be challenged in review.

## Locators

Prefer user-facing, in this order:

1. `getByRole('button' | 'link' | 'textbox', { name })`, `getByLabel`, `getByText`. Pass `exact: true` alongside `name`. The default matches the accessible name as a case-insensitive substring, which collides with note titles and other page content and fails strict mode.
2. `data-testid` (attribute selector) when a role or label is unavailable. Adding one to the Angular or React template is allowed, and is better than reaching into component internals with a CSS selector.
3. A CSS selector only when the element offers neither, which is common for ng-zorro internals and icon-only controls. It belongs in the Page Object, named for what it does (`cancelButton`, not `.cancel-para`), never inline in a spec. An accessible name that is really an icon glyph (`pause-circle`) is not an improvement; it rots on the next icon swap.

XPath is forbidden outright.

Much of the suite predates this section: it inlines CSS and mostly omits `exact: true`. The ratchet is that new or modified code complies. When you touch a test that inlines a selector, move it into the Page Object as part of that change.

## Assertions

- Web-first, auto-waiting assertions only: `toBeVisible`, `toHaveURL`, `toHaveText`, `toHaveCount`. The suite still asserts on values it extracted first in a few places, which `playwright/prefer-web-first-assertions` reports; the ratchet applies here too.
- No `waitForTimeout` without a `// JUSTIFIED:` rationale. When waiting on a count, use `toHaveCount`.
- No one-shot boolean checks (`expect(await el.isVisible())`) and no always-true assertions on a locator (`toBeDefined`, `not.toBeNull`). A Locator is always a defined, non-null object, so those pass whether or not the element exists. Asserting a non-locator value is not this smell: `expect.poll(...).not.toBeNull()` and a null-guard on a regex match are both legitimate.
- A conditional may gate a setup action on dual-mode UI (auth vs anonymous, the optional welcome modal), which is why `playwright/no-conditional-in-test` is off. Do not put an `expect` inside one: an assertion that runs on only one branch passes by skipping the check it exists to make. `playwright/no-conditional-expect` reports those and the suite still carries some, so the ratchet applies here too.
- A network wait is synchronization, not proof. `waitForLoadState('networkidle')` is discouraged by Playwright and the suite still has several, one of them inside `waitForZeppelinReady`; in new code wait on a user-visible signal instead. When you do wait on the network, assert the rendered result afterwards.
- The lint config covers part of this section, not all of it. `eslint-plugin-playwright` has no rule for always-true assertions, so those are a review responsibility.

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

Use an existing key from the `PAGES` object in `e2e/utils.ts`; add a new one there if the page is missing. `PAGES` is also the coverage-instrumentation set (`getCoverageTransformPaths`), so it defines the coverage denominator. Purely structural / non-page components (lifecycle hooks, shared UI primitives like the spinner or resize handle) are intentionally omitted from `PAGES`. They are exercised transitively and are not counted.

## Running

- Node: `nvm use` (version pinned in `.nvmrc`).
- Dev server: `npm run start` at `http://localhost:4200` (Playwright reuses a running one via `webServer.reuseExistingServer`).

| Command | Purpose |
| --- | --- |
| `npm run e2e` | Full suite |
| `npm run e2e:fast` | Chromium only (fast) |
| `npm run e2e:fast -- tests/<area>/<feature>.spec.ts` | One spec (path is relative to `e2e/`) |
| `npm run e2e:fast -- -g '<test title>'` | One test, matched by title |
| `npx eslint e2e/tests/<area>/<feature>.spec.ts` | Lint one file; `npm run lint` covers the whole app |
| `npm run e2e:classic` | Classic `/classic` UI suite against `:8080` (needs `-Pweb-classic`) |
| `npm run e2e:ui` | Playwright Test UI |
| `npm run e2e:headed` | Headed run |
| `npm run e2e:debug` | Step-by-step debugger |
| `npm run e2e:report` | Open last HTML report |
| `npm run e2e:report:classic` | Open last classic HTML report |
| `npm run e2e:ci` | CI mode (`CI=true`, baseURL `:8080`), main then classic suite |
| `npm run e2e:codegen` | Record against `:4200` |
| `npm run e2e:cleanup` | Delete leftover test notebooks (`e2e/cleanup-util.ts`) |

## Adding a Test (Agents Start Here)

1. Pick/confirm the target route and the `PAGES` key.
2. Copy the shape of an existing spec in the same `<area>`; reuse or extend the matching POM (`models/<name>.ts` + `.util.ts`). Do not inline selectors the POM already owns.
3. Annotate the page (`addPageAnnotationBeforeEach`), navigate, then `waitForZeppelinReady`.
4. Run `npm run e2e:fast` and iterate until green.

## Migration (Angular to React Microfrontend)

Pages are moving from Angular to React fragments incrementally. Today this is narrow: the published paragraph route reads a `?react=true` flag (`published/paragraph/paragraph.component`), the notebook footer swaps via a `?reactFooter=true` flag (read into the notebook component's `useReactFooter` input), and the configuration table swaps via a `?reactConfiguration=true` flag (`configuration/configuration.component`). All three are query params inside the hash. There is no app-wide "flip this route to React" flag, and no cross-framework parity project in this config. Write specs so they survive a route being reimplemented, but do not build parity infrastructure ahead of need.

### Write Framework-Neutral Specs

- Assert observable behavior only: what the user sees, the URL, network effects. Avoid asserting framework internals (`[ng-version]`, Angular component classes, `zeppelin-*` custom-element tags) except in a deliberate feature-flag test.
- Keep the locator order from the Locators section (role/label/text first). At a seam that will flip frameworks, prefer a shared `data-testid` that both implementations render.
- Never use fixed waits at a fragment seam. Wait on a user-visible post-mount signal or the specific remote response (`page.waitForResponse` on the fragment chunk), then assert the rendered result. `react-footer.spec.ts` shows the fallback pattern (`page.route('**/remoteEntry.js', route => route.abort())`).

### When a Route Gains a React Flag

- The flag is a route query param read via `ActivatedRoute.queryParams`, so with the hash router it goes INSIDE the hash: `/#/notebook/<id>/paragraph/<id>?react=true`, not before the `#`. Popups opened by app code (`window.open`) will not carry a flag added only to `page.goto`.
- To exercise both frameworks, follow the existing precedent and toggle the flag in-spec: navigate the same spec with and without the flag across tests, as `published-paragraph.spec.ts` does. A separate flag-appending Playwright project is an alternative, but scope it (its own `testMatch`) to routes that read the flag rather than running the whole suite twice.

### Coverage

- Coverage is tracked by `PAGES` key, not source file. The key is the stable identity; the path behind it is an implementation detail. When a page moves to React, update its path in `PAGES` rather than deleting the key (deleting drops it from the coverage denominator). Specs keep the same `addPageAnnotationBeforeEach(PAGES.KEY)` call across the migration.

### Suite Shape

- Keep the composed suite focused on real cross-seam user flows. Behavior that lives entirely inside one fragment belongs in that fragment's own tests; do not grow the composed suite into a per-fragment unit suite.

## Classic UI Tests (`e2e/tests/classic/`)

`e2e/tests/classic/` runs Playwright against the legacy AngularJS app served at `/classic`, ported from the retired `zeppelin-web` Protractor suite. Treat it as a frozen legacy surface: keep it at parity coverage and test new features only in the Angular/React suites.

- **Locators (classic exception):** the classic templates predate roles and `data-testid`, so the role/label/text-first rule cannot apply. Sanctioned here: element ids (`#findInput`), `ng-click="..."` / `ng-controller="..."` attribute selectors, class selectors the legacy templates already expose (`.username`, `.interpreterHead`), and Ace/Select2 internals. Do not add `data-testid` to the frozen `zeppelin-web` sources.
- **Readiness:** `waitForZeppelinReady` is Angular-specific (`[ng-version]`) and does not resolve on `/classic`; gate on a classic-visible signal instead (e.g. the first `ParagraphCtrl` paragraph, or `.ace_text-input` attached).
- **Coverage:** `PAGES` is the Angular coverage denominator; classic pages are intentionally outside it, so `addPageAnnotationBeforeEach` is not used here.
- **Running:** the classic suite has its own config, `playwright.classic.config.js` (Desktop Chrome only, targets `http://localhost:8080`), and needs a Zeppelin server built with `-Pweb-classic`. The `:4200` dev server does not serve `/classic`, so a plain `npm run e2e` never includes it. Run it with `npm run e2e:classic` (single spec: `npm run e2e:classic -- tests/classic/<spec>`). In CI the workflow enables it on the anonymous matrix leg only (`-Dweb.e2e.classic.disabled=false`), matching the anonymous-only legacy Protractor suite.
- **POM:** inlining locators/helpers is acceptable while the suite is this small; if it grows, move them behind `models/classic-*.ts` / `*.util.ts`.
- The React-migration / framework-neutral-spec guidance does not apply to `tests/classic/`.

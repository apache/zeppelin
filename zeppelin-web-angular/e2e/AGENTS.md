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

> E2E (Playwright) conventions for `zeppelin-web-angular/e2e/`. A scoped companion
> to the repository-root AGENTS.md, loaded only when working under `e2e/`.
> See [AGENTS.md specification](https://github.com/agentsmd/agents.md).

Config: `zeppelin-web-angular/playwright.config.js`. This file is the shared source
of truth for E2E conventions; Codex and agents.md-native tools read it directly.
Claude Code / Gemini users can symlink `CLAUDE.md` / `GEMINI.md` to it locally
(both gitignored, personal, not committed).

## Tooling: Use e2e-skills

Generate, review, and debug with [e2e-skills](https://github.com/voidmatcha/e2e-skills)
instead of ad-hoc prompts. It encodes the rules below and adds a deterministic
silent-pass scanner.

```bash
npx skills add voidmatcha/e2e-skills -g --all   # or -a <agent>
```

| Task | Skill |
| --- | --- |
| Generate new Playwright coverage | `playwright-test-generator` |
| Review specs for silent-pass smells | `e2e-reviewer` |
| Debug a failed Playwright report | `playwright-debugger` |
| Deterministic local scan | `bash skills/e2e-reviewer/scripts/scan.sh e2e/` |

Always run `e2e-reviewer` on generated specs. It catches always-passing
assertions (`toBeDefined()`, `not.toBeNull()`) that pass while the feature is broken.

## Layout

- Specs: `e2e/tests/<area>/<feature>.spec.ts` (areas: `authentication`, `home`,
  `login`, `notebook`, `share`, `theme`, `workspace`).
- Page Objects (POM), split by role:
  - `e2e/models/<name>.ts`: locators + primitive actions (click, fill, navigate, simple state checks).
  - `e2e/models/<name>.util.ts`: workflows, composite verification, scenario helpers.
- Shared helpers: `e2e/utils.ts`.

## Style

- English only. No unnecessary comments.
- BDD via `test.step('Given/When/Then …', …)`, as in existing specs.
- One `test.describe` per feature; construct the POM in `beforeEach`.

## Locators

Prefer user-facing, in this order:

1. `getByRole('button' | 'link' | 'textbox', { name })`, `getByLabel`, `getByText`.
2. Last resort: `data-testid` (attribute selector) when a role/label is unavailable
   and a CSS chain would be brittle.
3. Forbidden: raw CSS chains and XPath.

## Assertions

- Web-first, auto-waiting assertions only: `toBeVisible`, `toHaveURL`,
  `toHaveText`, `toHaveCount`.
- No `waitForTimeout`. When waiting on a count, use `toHaveCount`.
- No one-shot boolean checks (`expect(await el.isVisible())`) and no
  always-true assertions (`toBeDefined`, `not.toBeNull`).

## Readiness & Auth

- After navigation, wait with `waitForZeppelinReady(page)` from `e2e/utils.ts`
  (not fixed sleeps).
- Auth is programmatic: the `setup` project logs in once and writes
  `playwright/.auth/user.json`; browser projects consume it via `storageState`.
  Do not add per-test login races. For logged-out scenarios use a fresh context.

## Coverage Annotation (Required)

Every `describe` must declare the page/component it exercises so coverage is
attributed:

```ts
import { addPageAnnotationBeforeEach, PAGES } from '../../utils';

test.describe('Home Page - Core Elements', () => {
  addPageAnnotationBeforeEach(PAGES.WORKSPACE.HOME);
  // …
});
```

Use an existing key from the `PAGES` object in `e2e/utils.ts`; add a new one
there if the page is missing. `PAGES` is also the coverage-instrumentation set
(`getCoverageTransformPaths`), so it defines the coverage denominator. Purely
structural / non-page components (lifecycle hooks, shared UI primitives like the
spinner or resize handle) are intentionally omitted from `PAGES`. They are
exercised transitively and are not counted.

## Running

- Node: `nvm use` (pinned in `.nvmrc`, currently 22.21.1).
- Dev server: `npm run start` at `http://localhost:4200` (Playwright reuses a
  running one via `webServer.reuseExistingServer`).

| Command | Purpose |
| --- | --- |
| `npm run e2e` | Full suite |
| `npm run e2e:fast` | Chromium only (fast) |
| `npm run e2e:ui` | Playwright Test UI |
| `npm run e2e:headed` | Headed run |
| `npm run e2e:debug` | Step-by-step debugger |
| `npm run e2e:report` | Open last HTML report |
| `npm run e2e:ci` | CI mode (`CI=true`, baseURL `:8080`) |
| `npm run e2e:codegen` | Record against `:4200` |
| `npm run e2e:cleanup` | Delete leftover test notebooks (`e2e/cleanup-util.ts`) |

## Adding a Test (Agents Start Here)

1. Pick/confirm the target route and the `PAGES` key.
2. Copy the shape of an existing spec in the same `<area>`; reuse or extend the
   matching POM (`models/<name>.ts` + `.util.ts`). Do not inline selectors the
   POM already owns.
3. Annotate the page (`addPageAnnotationBeforeEach`), navigate, then
   `waitForZeppelinReady`.
4. Run `npm run e2e:fast` and iterate until green; then run `e2e-reviewer`.

## Migration (Angular to React Microfrontend)

Pages are moving from Angular to React fragments incrementally. Today this is
narrow: the published paragraph route reads a `?react=true` flag
(`published/paragraph/paragraph.component`), and the notebook footer swaps via a
`?reactFooter=true` flag (read into the notebook component's `useReactFooter`
input). Both are query params inside the hash. There is no app-wide "flip this
route to React" flag, and
no cross-framework parity project in this config. Write specs so they survive a
route being reimplemented, but do not build parity infrastructure ahead of need.

### Write Framework-Neutral Specs

- Assert observable behavior only: what the user sees, the URL, network effects.
  Avoid asserting framework internals (`[ng-version]`, Angular component classes,
  `zeppelin-*` custom-element tags) except in a deliberate feature-flag test.
- Keep the locator order from the Locators section (role/label/text first). At a
  seam that will flip frameworks, prefer a shared `data-testid` that both
  implementations render.
- Never use fixed waits at a fragment seam. Wait on a user-visible post-mount
  signal or the specific remote response (`page.waitForResponse` on the fragment
  chunk), then assert the rendered result. `react-footer.spec.ts` shows the
  fallback pattern (`page.route('**/remoteEntry.js', route => route.abort())`).

### When a Route Gains a React Flag

- The flag is a route query param read via `ActivatedRoute.queryParams`, so with
  the hash router it goes INSIDE the hash: `/#/notebook/<id>/paragraph/<id>?react=true`,
  not before the `#`. Popups opened by app code (`window.open`) will not carry a
  flag added only to `page.goto`.
- To exercise both frameworks, follow the existing precedent and toggle the flag
  in-spec: navigate the same spec with and without the flag across tests, as
  `published-paragraph.spec.ts` does. A separate flag-appending Playwright project
  is an alternative, but scope it (its own `testMatch`) to routes that read the
  flag rather than running the whole suite twice.

### Coverage

- Coverage is tracked by `PAGES` key, not source file. The key is the stable
  identity; the path behind it is an implementation detail. When a page moves to
  React, update its path in `PAGES` rather than deleting the key (deleting drops
  it from the coverage denominator). Specs keep the same
  `addPageAnnotationBeforeEach(PAGES.KEY)` call across the migration.

### Suite Shape

- Keep the composed suite focused on real cross-seam user flows. Behavior that
  lives entirely inside one fragment belongs in that fragment's own tests; do not
  grow the composed suite into a per-fragment unit suite.

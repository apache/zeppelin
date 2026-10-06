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

Unit test conventions for the React remote, covering only what differs from the package.

[`zeppelin-web-angular/AGENTS.md`](../../AGENTS.md) is the baseline and applies here unchanged: spec beside source, what to test and what not to, determinism, naming, coverage measured but not gated, and the lint rules that catch specs which cannot fail. Read it first. This file records the three places where this package is not the same.

## Running

| Command | Purpose |
| --- | --- |
| `npm test` | Run once |
| `npm run test:watch` | Re-run on change |
| `npm test -- --coverage` | With a coverage report |
| `npm run typecheck` | Typecheck without emitting files |

`npm test` and `npm run typecheck` run in pull-request CI as named steps on the anonymous frontend leg, through the frontend root scripts `test:react` and `typecheck:react`. Both are also bound to the Maven `test` phase, so `mvnw verify -Pweb-e2e` runs them again inside the `run-playwright-e2e-tests` job. The build runs through `build:react` at the Maven `generate-resources` phase. The lint runs through `lint:react`, but only inside the `run-playwright-e2e-tests` job, because `npm lint` is bound to the `test` phase and `frontend.yml` builds this module with `-DskipTests`. `npm audit` has its own job, and the npm-audit remediation workflow also runs the test suite when it validates a fix. The lint rules catch specs that cannot fail.

Specs are `Foo.spec.tsx` beside `Foo.tsx`, picked up by this package's own `vitest.config.mts`. `@testing-library/react` is available.

Coverage works the same way as the baseline describes, including the caveat: the denominator is only the files the specs load, so the percentage is not whole-tree coverage.

## What is worth testing here

The code that decides what to render. `SingleResultRenderer` picks a renderer from `DatasetType`; picking wrong leaves the user with a blank result. `HTMLRenderer` assigns `innerHTML` on a ref and then replaces `<script>` nodes by hand so that they execute, which React's own `dangerouslySetInnerHTML` would not do. That is worth pinning for both behaviour and safety.

Assert on what a user can see (a role, a label, a value) rather than on the DOM tree. Snapshot dumps of rendered markup record that markup changed, which it will, since these surfaces are being migrated.

## Do not pin behaviour that is already wrong

The baseline says to write the spec while Angular is still the source of truth. On this side there is an exception worth naming.

`TableVisualization` keeps its display mode in `useState` and has no save callback, so a setting the user changes is never persisted. Angular stores the same choice in `GraphConfig`. Writing a spec that asserts the current behaviour would turn that defect into the expected result.

**That surface is something to fix, not something to pin.** Test it once the state is lifted to the host. When in doubt: a spec records what the code *should* do. If the current behaviour is known to be wrong, fix it first or leave it alone.

## Adding a Test

`src/utils/` is the easiest start; `textUtils.spec.ts` is a worked example. Everything else follows the baseline.

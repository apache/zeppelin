<!--
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements. See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License. You may obtain a copy of the License at
http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Notebook route benchmark contract

ZEPPELIN-6668 defines the fixture and measurement format. It does not establish
a pinned Angular baseline. Generated JSON is ignored by Git.

Build with `npm run build`, then serve `dist/zeppelin` with an isolated local
Zeppelin backend. The benchmark imports one note and deletes that note in
cleanup. Do not point it at a shared notebook store. It checks the served index's
entry script list and the served entry asset hashes against the local build before
importing the fixture; server-injected inline markup does not affect this check. The normal E2E
suite does not run this benchmark; no development server is started by its config.
Each measured navigation's response is checked again after recording readiness,
before accepting the sample, so validation does not delay the measured ready time.

```sh
# From zeppelin-web-angular, with the built application served at this origin:
PLAYWRIGHT_BASE_URL=http://127.0.0.1:8080 \
  PERF_NOTEBOOK_DISPOSABLE=1 \
  npm run perf:notebook-baseline

# Contract validation only: one cold and one warm sample, plus an unrecorded prime.
PLAYWRIGHT_BASE_URL=http://127.0.0.1:8080 \
  PERF_NOTEBOOK_DISPOSABLE=1 \
  PERF_NOTEBOOK_DRY_RUN=1 \
  npm run perf:notebook-baseline
```

The standard Chromium project and its automatic authentication setup are reused.
Traces, screenshots, video, retries, parallel workers and service workers are
disabled for measurement. Each cold run uses a new browser context (empty HTTP
cache). Warm runs reuse one context/page after one unrecorded priming navigation;
each run performs a full document navigation. OS/server caches are not flushed.
The default is 10 cold samples followed by 10 warm samples, with no outlier removal.

The fixture is `../fixtures/performance/notebook-route-100-paragraphs.json`:
80 idle TEXT-result paragraphs, 10 idle TABLE-result paragraphs,
and 10 collapsed code paragraphs, all in READY status as preserved by note import.
No interpreter executes. The pinned `fixtureSha256` constant in
`notebook-baseline.ts` is checked before import, and the composition is validated.
A deliberate fixture revision must update that constant and be reviewed as a
benchmark contract change.

`notebookReadyMs` is the browser's `performance.now()` when the action bar is
visible, exactly 100 paragraph hosts are attached, and the first Monaco editor
is attached, checked together on an animation frame. It is relative to that
document's navigation start, not the preceding import. `fcpMs` is the browser
`first-contentful-paint` Performance entry. Missing readiness or FCP fails the
run; there is no timeout fallback metric.

Outputs are `baselines/<run-id>.raw.json` and `<run-id>.summary.json`. Both contain
schema version, run ID/time, dry-run flag, Zeppelin commit and dirty-tree flag,
OS/release, CPU model, physical memory bytes, browser/Node versions, build command,
fixture path/SHA-256, base URL, viewport, run counts and bundle measurements.
Raw samples contain `cache` (`cold` or `warm`), one-based `run`, `notebookReadyMs`
and `fcpMs`. Summary groups each metric by cache mode with count, median and
nearest-rank p95 (`ceil(0.95 * n)`); an even sample count uses the two middle
values' mean for the median. A dry run is schema evidence, not a baseline.

`bundles.angularEntryChunks` lists JavaScript entry scripts from the built
Angular `index.html`. `bundles.reactRemoteEntry` measures the separately served
`assets/react/remoteEntry.js`. Each file records relative path, SHA-256, raw bytes
and gzip bytes. These are entry asset measurements, not the whole `dist` tree
or a claim about the complete notebook route's lazy-loaded bundle size.

Focused static checks: `npm run test:shell -- test/notebook-baseline.spec.ts`
and `npx eslint e2e/performance/*.ts e2e/models/notebook-performance-page.ts`.

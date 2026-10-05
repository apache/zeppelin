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

# Notebook transport contract fixtures

These tests record and replay notebook REST requests and WebSocket messages.
They provide repeatable transport evidence for the notebook Core migration, including
[ZEPPELIN-6672](https://issues.apache.org/jira/browse/ZEPPELIN-6672).
A fixture is a JSON file containing captured traffic, its order, and its capture environment.

Use the stored fixtures to test replay without a Zeppelin server.
Use live capture when you need to update the recorded behavior of Apache Zeppelin.
The tests distinguish transport replay from convergence of actual Core state.

## Run the tests

Run these commands from `zeppelin-web-angular/` in the candidate checkout.
Use the Node version in `.nvmrc` and install the frontend dependencies first:

```bash
nvm use
npm ci
npm run check:core-contract-fixtures
```

The fixture command checks the formats, redaction, ordering, committed capture inventory,
and TypeScript types. It does not require a browser or a Zeppelin server.

For browser replay, install Chromium and run:

```bash
npx playwright install chromium
npm run e2e:core-contract
```

This command uses [the dedicated Playwright configuration](../../playwright.core-contract.config.js).
It runs without a Zeppelin server, authentication setup, or shared notebook cleanup.
It checks recorded payloads and delivery order. Two Core convergence tests skip until you
provide the reference checkout described below.

Additional checks cover the capture infrastructure:

| Command                              | Requirements                            | Coverage                                                                          |
| ------------------------------------ | --------------------------------------- | --------------------------------------------------------------------------------- |
| `npm run check:core-contract-server` | Bash, curl, lsof, and a free local port | Server start/stop and process ownership against a stub                            |
| `npm run check:core-contract-auth`   | Installed Chromium                      | Anonymous authentication setup and preservation of the ordinary E2E auth snapshot |
| `npm run e2e:core-contract:live`     | An isolated, built Zeppelin server      | New captures from live notebook operations                                        |

Maven runs the fixture checks in the test phase and server-helper checks in integration-test.
Live capture requires an explicit command.

### Test convergence with the reference Core

Convergence means that both viewers reach the captured server state after recovery.
These tests use the implemented Core and Angular adapter from a separate, pinned checkout.
The [migration proposal](https://cwiki.apache.org/confluence/spaces/ZEPPELIN/pages/393677314/Micro+Frontend+Migration+Angular+to+React+Proposal)
links to this implementation.

From `zeppelin-web-angular/`, prepare the reference and run:

```bash
git clone https://github.com/voidmatcha/zeppelin.git /tmp/zeppelin-core-reference
git -C /tmp/zeppelin-core-reference checkout --detach 36b5f356c63413e43c26ebcd448af91b0f353ba2
export ZEPPELIN_NOTEBOOK_CORE_REFERENCE=/tmp/zeppelin-core-reference
npm run e2e:core-contract -- -g 'converges actual Core'
```

The builder requires this exact commit and unchanged Core and adapter sources.
It produces a test-only browser bundle and attaches source hashes to the test results.
The reference code is not part of Apache master.

Each test runs two independent Core instances against a collaboration capture.
It applies a local draft and delays, duplicates, drops, or reorders selected received messages.
After all fault deliveries settle, it replays the captured GET_NOTE recovery.
The test compares both clean Core snapshots with their captured REST notes and with each other.
The comparison covers note and paragraph identity, order, text, execution fields, results, forms,
and scheduler/display configuration. Collaboration users come from the last delivered mode message.
ACL and revision-history state have no REST-note comparison in these scenarios.

Without the reference, successful wire replay does not establish Core convergence.

## Captured lifecycle scenarios

The seven version 2 captures and their environment records are in
[notebook-lifecycle](../fixtures/notebook-lifecycle/).
The [manifest](../fixtures/notebook-lifecycle/manifest.json) records capture outcomes.
All seven committed captures use Apache Zeppelin commit
`74b51b28d6f3c1c170f44822beadba299df9a959`.

| Fixture                        | Behavior covered                                                                                                                                                                                                                    |
| ------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `structural.json`              | WebSocket insert, copy, move, remove, and correlated commit. REST insert, move, and remove return full NOTE payloads. The fixture declares the two update sources separately.                                                       |
| `revision-reconnect.json`      | Checkpoint, history, compare, restore, and live/revision reconnect requests. Live updates arrive on a revision route while its displayed text and paragraph count remain unchanged.                                                 |
| `collaboration-anonymous.json` | Two independent browser contexts, patches, note updates, collaboration status, and final refetches.                                                                                                                                 |
| `collaboration-auth.json`      | The collaboration flow with two distinct authenticated users. Credentials and principals are redacted.                                                                                                                              |
| `association.json`             | A-to-B navigation, Job Manager, open, reload, home, new, and clone. A proxy holds one unchanged NOTE_B while B updates arrive before and after its release. The fixture records native browser delivery order and implicit replies. |
| `commit-request-loss.json`     | A commit request drops before server delivery. The local draft remains unconfirmed until explicit reconciliation.                                                                                                                   |
| `commit-reply-loss.json`       | The server accepts a commit, but replay drops its captured correlated reply. The same reconciliation reveals the accepted server text.                                                                                              |

The commit scenarios use a 250 ms observation deadline and explicit REST/GET_NOTE reconciliation.
Angular does not provide an automatic save-ACK timeout/refetch outcome in these tests.
A missing reply alone cannot distinguish request loss from reply loss.

## Recreate the lifecycle captures

Use two checkouts: the candidate contains the capture tests, and the baseline supplies the server and UI.
To reproduce the committed baseline, use the Apache commit listed above.
For a new baseline, record its full Apache master commit and build that exact checkout.
Live capture requires `/api/version` to report the selected commit.

Use a Bash environment with JDK 11, the pinned Node version, curl, and lsof.
Both the checkout and capture-root paths must be free of whitespace, including resolved symlink paths.
The Zeppelin launcher splits JVM arguments on whitespace, so the helper rejects these paths.

### Prepare the baseline

Start in the candidate repository root. Replace the baseline path with a location for your separate checkout:

```bash
export CANDIDATE_CHECKOUT="$(pwd)"
export BASELINE_CHECKOUT=/tmp/zeppelin-capture-baseline
git clone https://github.com/apache/zeppelin.git "$BASELINE_CHECKOUT"
git -C "$BASELINE_CHECKOUT" checkout --detach 74b51b28d6f3c1c170f44822beadba299df9a959

(
  cd "$BASELINE_CHECKOUT"
  ./mvnw clean install -DskipTests -pl zeppelin-web-angular -am
  ./mvnw install -DskipTests -pl markdown -am
)
```

GitNotebookRepo is required for revision capture. A registered interpreter, such as the built
markdown interpreter, is required for clone. The scenarios do not execute interpreter code.

Install the candidate's server helper at the same relative path in the baseline checkout:

```bash
mkdir -p "$BASELINE_CHECKOUT/zeppelin-web-angular/e2e/core-contract/capture"
cp "$CANDIDATE_CHECKOUT/zeppelin-web-angular/e2e/core-contract/capture/server.sh" \
  "$BASELINE_CHECKOUT/zeppelin-web-angular/e2e/core-contract/capture/server.sh"
export CAPTURE_HELPER="$BASELINE_CHECKOUT/zeppelin-web-angular/e2e/core-contract/capture/server.sh"
```

The helper selects the repository from its own file location, not the current working directory.
Invoking the helper from the candidate would launch the candidate server.

### Capture in anonymous mode

Use a fresh capture root and a separate output directory for review:

```bash
export CAPTURE_ROOT="$(mktemp -d)"
export ZEPPELIN_LIFECYCLE_FIXTURE_DIR="$(mktemp -d)"
bash "$CAPTURE_HELPER" start --root "$CAPTURE_ROOT" --port 18080 --storage git --job-manager

cd "$CANDIDATE_CHECKOUT/zeppelin-web-angular"
export ZEPPELIN_CAPTURE_MASTER_COMMIT="$(git -C "$BASELINE_CHECKOUT" rev-parse HEAD)"
export ZEPPELIN_CAPTURE_ENVIRONMENT="$CAPTURE_ROOT/capture-environment.json"
export ZEPPELIN_E2E_SHIRO_INI="$CAPTURE_ROOT/conf/shiro.ini"
export PLAYWRIGHT_BASE_URL=http://127.0.0.1:18080
npm run e2e:core-contract:live -- lifecycle-fixtures
npm run e2e:core-contract:live -- commit-fixtures

bash "$CAPTURE_HELPER" stop --root "$CAPTURE_ROOT"
```

Stop the server even if a capture command fails. The helper reports startup failures with server logs.
It checks listener ownership before stopping processes.

The server uses isolated configuration, notebook, search, recovery, log, and PID directories.
It clears inherited Zeppelin and JVM configuration and binds to loopback.
`JAVA_HOME` and `PATH` still select your toolchain.

Set `ZEPPELIN_E2E_SHIRO_INI` even in anonymous mode. The absent isolated `shiro.ini`
prevents the login helper from selecting credentials from another checkout.
The browser runner creates a unique temporary directory for auth state and results.
Set `ZEPPELIN_CORE_CONTRACT_RUN_DIR` to retain them at a chosen location, using a separate directory for each run.

Without `--home-note <id>`, the home-note scenario captures the null-note response.
To capture a configured home note, prepare it in the isolated notebook directory before starting the server.

### Capture authenticated collaboration

Keep the same fixture output directory so the manifest includes both modes.
Use a new capture root and the helper's isolated Shiro template:

```bash
export CAPTURE_ROOT="$(mktemp -d)"
bash "$CAPTURE_HELPER" start --root "$CAPTURE_ROOT" --port 18080 --storage git --job-manager --mode auth
export ZEPPELIN_CAPTURE_ENVIRONMENT="$CAPTURE_ROOT/capture-environment.json"
export ZEPPELIN_E2E_SHIRO_INI="$CAPTURE_ROOT/conf/shiro.ini"
npm run e2e:core-contract:live -- lifecycle-fixtures -g 'captures collaboration'
bash "$CAPTURE_HELPER" stop --root "$CAPTURE_ROOT"
```

Authenticated collaboration requires two distinct users configured in that template.
Missing capabilities produce a named skip, not a supported capture.
A test failure remains a failure in the manifest.

### Review and replace the captures

Review all seven files and the manifest in `ZEPPELIN_LIFECYCLE_FIXTURE_DIR` before replacing committed fixtures.
Check the scenario, covered operations, exclusions, baseline commit, and capture environment.
The environment includes the origin, browser version, storage paths, authentication mode,
interpreter groups, and progress configuration. Authenticated ordinary-user capture uses the helper
manifest for configuration provenance because the configuration APIs require an administrator.

Review redaction as described below. New note and paragraph IDs make recaptures differ even when the protocol stays unchanged.
After replacing the committed files in `e2e/fixtures/notebook-lifecycle/`, run:

```bash
npm run check:core-contract-fixtures
npm run e2e:core-contract
```

The inventory check requires all seven captures to be valid, supported, and attributed to Apache master.
Run the reference Core tests as well when reviewing collaboration recovery.

## Fixture format and replay rules

Both formats record notebook REST traffic under `/api/notebook` and WebSocket traffic at `/ws`.
Every fixture declares `scenario`, `owner`, `coveredOperations`, and `knownExclusions`.
Use exclusions to name behavior that the scenario does not cover.
Validate fixtures before replay. The version 1 replay adapter checks record shape and order,
but does not check ownership metadata.

This minimal version 1 fixture demonstrates the required metadata and a sent frame:

```json
{
  "version": 1,
  "metadata": {
    "scenario": "Open a notebook",
    "owner": "zeppelin-web-angular",
    "coveredOperations": ["GET_NOTE"],
    "knownExclusions": ["Live interpreter execution is covered by a separate E2E scenario"]
  },
  "records": [
    {
      "kind": "websocket",
      "sequence": 1,
      "websocket": { "direction": "send", "payloadText": "{\"op\":\"GET_NOTE\"}" }
    }
  ]
}
```

Version 1 supports one WebSocket connection. Version 2 adds multiple sessions, reconnects,
route context, and delivery faults. The lifecycle runner rejects version 1 inputs.
See [the version 2 declarations](lifecycle/fixture.d.mts) and the committed captures for complete examples.

A version 2 fixture declares its sessions. Every record has a session ID and a contiguous global sequence starting at 1:

| Record kind  | Meaning                                                                                                                                                                     |
| ------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `connection` | Opens or closes a socket generation identified by `connectionId`. Frames must belong to an open generation.                                                                 |
| `context`    | Records the test observer's active/inactive route, note ID, and revision ID. It is outside the wire payload and is not a server acknowledgement.                            |
| `websocket`  | Records direction, socket generation, and the captured text payload. `delivery: "dropped-before-server"` identifies a proxy-observed command that never reached the server. |
| `rest`       | Records a request or complete response. The pair shares a `requestId` within its session.                                                                                   |

REST response order uses the browser's `requestfinished` observation, even if reading the body completes later.
Consecutive REST requests can arrive in any order within their recorded batch. Responses retain their global order.
A request batch ends at a response or WebSocket record. Version 2 also ends batches at connection and context records.
Concurrent identical requests are indistinguishable by request shape.

A fault plan selects received frames by sequence. `copies: 0` drops a frame and `copies: 2` duplicates it.
`delayMs` delays delivery. `afterSequence` releases it after the selected record is consumed.
Plans leave the original capture unchanged. Supported delays range from 0 to 2147483647 milliseconds.
A delayed frame that targets a closed socket fails replay.

Replay fails on unexpected requests, mismatched frames, unconsumed records, or failed deliveries.
Completion requires all REST responses and scheduled frames to settle.
An expected input that never arrives reaches the Playwright test timeout.
Recorded disconnects replay with code 1012 because the capture event does not expose the original close code.

## Redaction and capture limits

Capture redacts credentials and participant identities by field name and replaces volatile timestamps with placeholders.
Note and paragraph IDs remain unchanged across URLs, bodies, and messages.
Envelope `msgId` values receive distinct placeholders that preserve repeated references.
Replay binds these placeholders to runtime IDs and preserves equality across viewers.
Legacy fixtures with the erased `<msgId>` value require recapture.

Only `accept` and `content-type` headers survive filtering. Redirect and authentication headers are outside this contract.
JSON strings, including notebook text, are not generally scanned for secrets.
Text scanning applies to URLs, raw bodies, and non-JSON frames, and cannot detect every credential form.
Use only the isolated server and synthetic notebook content. Review captures before committing them.

Capture rejects binary frames because there is no binary redaction policy.
Version 1 replay supports deliberately authored binary fixtures, but lifecycle replay does not.
Each WebSocket record requires exactly one of `payloadText` and `payloadBase64`.

Wait for the scenario to finish before calling recorder `stop()` or `write()`.
Both wait for already observed response-body reads. Failed or unfinished requests fail capture.
A snapshot taken before successful stop can contain gaps and is not a complete replay fixture.

These fixtures replay complete REST bodies, not HTTP chunks, header availability, or application callback timing.
Version 1 also lacks timing faults and reconnect boundaries.
Binary rejection under native Playwright dispatch has no live-server test.

The reference tests establish Core convergence for the named implementation and these captures.
Production Core integration belongs to ZEPPELIN-6687. Reconnect storm/backoff and terminal teardown belong to ZEPPELIN-6696.
Legacy/EventBus parity belongs to ZEPPELIN-6698. Authorization scenarios belong to ZEPPELIN-6673.
Live interpreter execution, performance, and accessibility require separate E2E coverage.

## Where to make changes

| Directory                                                            | Responsibility                                                            |
| -------------------------------------------------------------------- | ------------------------------------------------------------------------- |
| [capture/](capture/)                                                 | Isolated server start/stop helper and its tests                           |
| [transport/](transport/)                                             | Version 1 format, redaction, transport recording/replay, and test doubles |
| [lifecycle/](lifecycle/)                                             | Version 2 validation, recording, replay, and browser drivers              |
| [lifecycle/core/](lifecycle/core/)                                   | Reference Core build and convergence checks                               |
| [port-proof/](port-proof/)                                           | Angular/React port identity and runtime import checks                     |
| [../tests/notebook/core-contract/](../tests/notebook/core-contract/) | Browser scenarios                                                         |

Helper tests sit beside their implementations. [runner.test.mjs](runner.test.mjs) covers the shared Playwright configuration.

## Notebook route boundary proof

`npm run build:notebook-core-port-proof` builds the React consumer and Angular
route host separately. `npm run test:notebook-route-boundary` then checks the
current `/notebook/:noteId` and `/notebook/:noteId/revision/:revisionId` route
shapes in Chromium. The proof bootstraps the production `WorkspaceModule` lazy
route, follows its `NotebookModule` lazy route, and asserts that the activated
component is the production `NotebookComponent`. Its browser-only message-service
double records the production component's `getNote`, `noteRevision`, and revision
history requests. The Angular harness reads the resulting activated-route snapshot
into one host-owned test Core; the remote receives only its stable
`NotebookCorePort`, reads the selected note and revision snapshot, and observes
route-driven subscription updates. The browser assertion records the two production
paths explicitly, so a route-shape change requires an intentional proof update. It
also fails on uncaught page errors and on errors that Angular's `ErrorHandler`
logs, so a production component that throws against an incomplete double does not
pass silently.

Maven runs the proof build, this proof and the port identity proof in the
`integration-test` phase of `-Pweb-e2e`. `web.e2e.core.port.proof.disabled` gates
all three, and `frontend.yml` enables them only on the anonymous leg of
`run-playwright-e2e-tests`.

These responsibilities stay host-side: route parameters, the physical WebSocket
connect, close and reconnect lifecycle, the SDK, and Angular services. The port
exposes only `getSnapshot` and `subscribe`, and the proof asserts that route
activation and port consumption make no `bootstrap`, `connect` or `close` call.
Future Core work owns note re-subscription and state recovery. ZEPPELIN-6683
already rejects stale revision and interpreter-binding replies inside the Angular
`NotebookComponent`; this proof does not move that rule into the Core, and detailed
reconnect recovery remains outside it until the lifecycle rules have an enforceable
stale-reply mechanism. The harness does not implement those lifecycle rules, switch
the production renderer, or move production notebook state out of Angular.

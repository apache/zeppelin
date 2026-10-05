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

Use these tests to replay recorded notebook REST requests and WebSocket messages without a Zeppelin server.
A fixture stores the messages, their order, and the environment that produced them.
The browser tests also compare two viewers' notebook states after disrupted collaboration messages and a full-note refetch.

To run the stored fixtures, follow [Run the tests](#run-the-tests).
To record new server behavior, follow [Recreate the lifecycle captures](#recreate-the-lifecycle-captures).

## Run the tests

Run these commands from `zeppelin-web-angular/` in your working checkout.
Use the Node version in `.nvmrc`:

```bash
nvm use
npm ci
npm run check:core-contract-fixtures
```

This command runs the fixture and Core consumer tests, then checks TypeScript types.
It covers fixture formats, redaction, message order, and the required set of stored captures.
The tests do not start a browser or a Zeppelin server.

For browser replay, install Chromium and run:

```bash
npx playwright install chromium
npm run e2e:core-contract
```

This command uses [the dedicated Playwright configuration](../../playwright.core-contract.config.js).
It checks recorded messages, delivery order, and whether both viewers reach the recorded server state after recovery.
The anonymous and authenticated collaboration captures both run by default.
Replaying them requires no running Zeppelin server or login.

Additional checks cover the capture infrastructure:

| Command                              | Requirements                            | Coverage                                                                          |
| ------------------------------------ | --------------------------------------- | --------------------------------------------------------------------------------- |
| `npm run check:core-contract-server` | Bash, curl, lsof, and a free local port | Server start/stop and process ownership against a stub                            |
| `npm run check:core-contract-auth`   | Installed Chromium                      | Anonymous authentication setup and preservation of the ordinary E2E auth snapshot |
| `npm run e2e:core-contract:live`     | An isolated, built Zeppelin server      | New captures from live notebook operations                                        |

Maven runs the fixture checks in the test phase and server-helper checks in integration-test.
Live capture requires an explicit command.

### How the collaboration tests compare state

Each viewer runs in a separate browser context and has its own Core instance in Node.
[consumer.ts](lifecycle/core/consumer.ts) converts messages received by that browser into events for the [test runtime](lifecycle/core/runtime/).
Replay counts a message as delivered only after the consumer handles it.
A consumer error fails the test.

The tests delay, duplicate, drop, and reorder selected collaboration messages.
Before recovery, they check unsaved local text, received patches, differing viewer titles, and the actual delivery order.
The recorded `GET_NOTE` requests then fetch complete notes.
The tests compare each Core snapshot with its separately recorded REST note and with the other viewer's snapshot.

The comparison includes note IDs, paragraph IDs and order, text, execution status, progress, output, forms, and scheduler and display configuration.
It also requires all paragraphs to be clean, with no unsaved local text.
The latest collaboration-mode message supplies the expected participant list.

These tests exercise message replay and the test runtime's state changes.
They do not mount the production Angular notebook or test when it decides to recover.
Permissions and revision history are outside the state comparison in these two scenarios.

## Captured lifecycle scenarios

The seven version 2 captures and their environment records are in
[notebook-lifecycle](../fixtures/notebook-lifecycle/).
The [manifest](../fixtures/notebook-lifecycle/manifest.json) records capture outcomes.
All seven committed captures use Apache Zeppelin commit
`74b51b28d6f3c1c170f44822beadba299df9a959`.

| Fixture                        | Behavior covered                                                                                                                |
| ------------------------------ | ------------------------------------------------------------------------------------------------------------------------------- |
| `structural.json`              | Paragraph insert, copy, move, remove, and commit. Captures separate WebSocket and REST operations and their replies.            |
| `revision-reconnect.json`      | Checkpoint, history, comparison, restore, and reconnect. Captures live updates while the viewer remains on a revision.          |
| `collaboration-anonymous.json` | Two independent browser contexts, patches, note updates, collaboration status, and final refetches.                             |
| `collaboration-auth.json`      | The collaboration flow with two distinct authenticated users. Credentials and principals are redacted.                          |
| `association.json`             | Navigation between notes, Job Manager, open, reload, home, new, and clone. Captures delivery order when a full note is delayed. |
| `commit-request-loss.json`     | A commit request never reaches the server. A later refetch shows that the server still has the original text.                   |
| `commit-reply-loss.json`       | The server accepts a commit, but its reply is lost. A later refetch shows that the server has the saved text.                   |

The commit scenarios wait up to 250 ms for the save reply, then request the server state through REST and `GET_NOTE`.
The test initiates this refetch, not an automatic Angular save timeout.
A missing reply alone cannot distinguish request loss from reply loss.

## Recreate the lifecycle captures

Live capture uses two checkouts: your working checkout supplies the tests, and a baseline checkout supplies the server and UI.
To reproduce the committed baseline, use the Apache commit listed above.
For a new baseline, select a commit from Apache master and build that exact checkout.
Live capture requires `/api/version` to report the selected commit.

Use a Bash environment with JDK 11, the pinned Node version, curl, and lsof.
Both the checkout and capture-root paths must be free of whitespace, including resolved symlink paths.
The Zeppelin launcher splits JVM arguments on whitespace, so the helper rejects these paths.

### Prepare the baseline

Start in your working repository root. Replace the baseline path with a location for the server checkout:

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

Copy the server helper from your working checkout to the same relative path in the baseline checkout:

```bash
mkdir -p "$BASELINE_CHECKOUT/zeppelin-web-angular/e2e/core-contract/capture"
cp "$CANDIDATE_CHECKOUT/zeppelin-web-angular/e2e/core-contract/capture/server.sh" \
  "$BASELINE_CHECKOUT/zeppelin-web-angular/e2e/core-contract/capture/server.sh"
export CAPTURE_HELPER="$BASELINE_CHECKOUT/zeppelin-web-angular/e2e/core-contract/capture/server.sh"
```

The helper selects the repository from its own file location, not the current working directory.
Running the original helper in your working checkout launches that checkout's server instead of the baseline.

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

Set `ZEPPELIN_E2E_SHIRO_INI` even in anonymous mode, where that file does not exist.
This path prevents the login helper from selecting credentials from another checkout.
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
The environment includes the server address, browser version, storage paths, authentication mode, interpreter groups, and progress configuration.
In authenticated mode, the helper records configuration that the ordinary capture user cannot read through administrator-only APIs.

Review redaction as described below. New note and paragraph IDs make recaptures differ even when the protocol stays unchanged.
After replacing the committed files in `e2e/fixtures/notebook-lifecycle/`, run:

```bash
npm run check:core-contract-fixtures
npm run e2e:core-contract
```

The inventory check requires all seven captures to be valid, supported, and attributed to Apache master.
The browser command includes both collaboration convergence tests.

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
Replay waits up to 15 seconds for the next expected input or for completion.
Playwright also applies the test's overall timeout.
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

Use separate E2E tests for production notebook integration, reconnection policy, authorization, interpreter execution, performance, and accessibility.

## Where to make changes

| Directory                                                            | Responsibility                                                            |
| -------------------------------------------------------------------- | ------------------------------------------------------------------------- |
| [capture/](capture/)                                                 | Isolated server start/stop helper and its tests                           |
| [transport/](transport/)                                             | Version 1 format, redaction, transport recording/replay, and test doubles |
| [lifecycle/](lifecycle/)                                             | Version 2 validation, recording, replay, and browser drivers              |
| [lifecycle/core/](lifecycle/core/)                                   | Wire-to-Core mapping, test runtime, and convergence checks                |
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

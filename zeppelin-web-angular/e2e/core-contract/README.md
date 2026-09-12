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

This directory defines the versioned REST and WebSocket fixture format used by
Notebook adapter tests. It is an in-repository contract test, not a Pact
consumer/provider contract and not a replacement for live-server E2E tests.

## Fixture ownership

Every committed fixture includes these required fields.
`createNotebookTransportRecorder` rejects a capture without them and
`validateFixture` reports them as errors. The replay adapter does not: it calls
`validateReplayFixture`, which checks record shape and ordering only, so a fixture
replayed without being validated first is never checked for metadata.

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

- `scenario` describes the user-visible flow.
- `owner` identifies the component that maintains the fixture.
- `coveredOperations` lists the REST or WebSocket operations represented by the
  fixture.
- `knownExclusions` records intentionally uncovered behavior. An empty array
  is valid when there are no exclusions.

Add a fixture when a Notebook operation is moved into the shared adapter
contract. If that operation cannot yet be represented, add its explicit reason
to the scenario's `knownExclusions`; do not silently rely on another fixture.

## What version 1 does not model

A fixture records one observed interleaving of REST and WebSocket traffic and delivers
it in exactly that order. The migration plan this format serves does not assume the
observed order of an HTTP response and its related WebSocket events is guaranteed,
and the capture scenarios that follow this issue (ZEPPELIN-6671, ZEPPELIN-6672) have
to reproduce duplicate, reordered, late and dropped events. Version 1 cannot express
"either order is acceptable here": it can only pin the order that was captured.

A REST response occupies the position of Playwright's `requestfinished` event,
when its body has finished downloading. The earlier `response` event only supplies
headers. Reading the body with `response.text()` is asynchronous and must not move
that position past later WebSocket frames. `stop()` and `write()` wait for those
body reads; `stop()` also allows already captured responses to finish after it
stops accepting new traffic. This format replays complete response bodies, not
header-only availability, intermediate HTTP chunks or application callback timing.

For example, a client that awaits `fetch()` headers and sends another request or a
WebSocket acknowledgement before reading the body needs a separate header event.
Version 1 cannot reproduce that dependency and replay can stall. Such a scenario
must list this limitation in `knownExclusions`; capture validation checks record
structure, not whether application-level dependencies are replayable. Scenarios
using complete JSON responses should consume and assert those bodies during both
capture and replay.

What is pinned is the order the fixture *answers* in, not the order the page happens to
ask in, because a browser issues requests when it wants and a fixture cannot dictate
that. Two tolerances follow from it, and nothing beyond them:

- Consecutive REST request records form a request batch, ending at the next response
  or WebSocket record. Requests in that batch are matched by shape regardless of
  arrival order; responses retain their recorded order. For example, request A,
  request B, response A, response B replays when B arrives before A.
- A request that arrives while the fixture is expecting a WebSocket frame waits, as long
  as a later record answers it.

A request outside the current batch is rejected as a request mismatch. Response-only
fixtures contain no request batches, so they reject a different first request as out
of order. A request no remaining record can answer and a WebSocket frame that does
not match the next recorded frame are also rejected.

Two identical requests in flight at once cannot be told apart either. A record is
matched to a route by request shape - method, path, headers and body - so if a page
issues the same request twice concurrently and the capture recorded two different
responses, replay may hand each route the other's response and still report success.
Sequential requests are unaffected, because they are matched in arrival order.

`assertComplete()` requires every REST delivery to have settled successfully. A failed
fulfillment rejects its route and prevents successful completion, while independent
routes can still receive their recorded responses.

`principal` is redacted wherever it appears, and Zeppelin puts it on nearly every
WebSocket frame; `user`, `users` and `roles` go the same way. A fixture therefore names
nobody, which also means it cannot express a scenario that turns on who is acting. The
permission and authentication scenarios in ZEPPELIN-6673 will need that distinction.

Only `accept` and `content-type` survive header filtering, so a fixture cannot carry
the `www-authenticate` of a 401 or the `location` of a redirect, both of which
ZEPPELIN-6673 needs.

Two more limits follow from the same shape. A record carries no timing, so a
scenario that turns on delay - a server change applied before an HTTP response times
out - cannot be replayed. And a fixture models one WebSocket connection, so the
reconnection scenarios in ZEPPELIN-6672 need more than version 1 provides.
Capture rejects a second notebook WebSocket connection, including a reconnect,
instead of flattening connections into a fixture that cannot be replayed.

That is deliberate. Widening the format before those scenarios exist would mean
designing for guesses. When a scenario needs it, the `version` field is the place to
introduce it, and a fixture written for an older version is rejected rather than
silently reinterpreted.

## Test layers

Run the format, redaction, ordering, and replay checks with:

```bash
npm run check:core-contract-fixtures
```

That covers the format, the redaction rules and the replay adapter against records,
and takes a couple of seconds. The capture server has a suite of its own, which starts
`capture-server.sh` for real against a stub to cover start, stop and pid-file
behaviour. It spawns processes and binds a free local port, so it takes tens of
seconds and runs in Maven's integration-test phase rather than on every build:

```bash
npm run check:core-contract-server
```

One thing neither layer reaches: the recorder's rejection of a binary WebSocket
frame. A frame can only be recorded from a real WebSocket server - a socket answered
by `route.fulfill` never opens, and one mocked with `routeWebSocket` does not raise
`page.on('websocket')` - so that path is covered only by the node test's event
emitter, and its behaviour under Playwright's own dispatch is unverified.

The checks above need no browser. What a browser adds is the adapter's contact with
Playwright's own `Request`, `Response`, `Route` and `WebSocket` objects, which a hand
written double cannot stand in for - a `route.continue()` that should have been
`route.fallback()` looked correct against doubles for a long time. Browser tests cover
that contact, including both HTTP-body-first and WebSocket-first exchanges against
a local server. A capture/replay round trip also checks requests without Accept, because Playwright does not
describe a request identically to a `page.on('request')` listener and to a `page.route`
handler:

```bash
npm run e2e:core-contract
```

The focused command uses `playwright.core-contract.config.js`. Its non-live mode
has no authentication setup, stored browser session, global setup/teardown or dev
server. It does not contact `PLAYWRIGHT_BASE_URL` or clean notebooks from another run.
The focused command runs Chromium. The ordinary E2E suite still includes the synthetic
browser tests in its Chromium, Firefox and WebKit projects and excludes `@live`.

The capture server requires `lsof` to verify listener ownership and a built checkout
(`./mvnw clean install -DskipTests -pl zeppelin-web-angular -am`). A startup failure
reports the server log; a successful HTTP response alone does not establish ownership.
The capture root and repository paths must not contain whitespace, including in
physical paths reached through symlinks. The Zeppelin launcher splits JVM arguments
on whitespace; the capture script rejects these paths before creating files or
launching a process. Choose a root and checkout without spaces, tabs or newlines.
Start and stop hold an atomic `.capture-operation-lock` in the capture root until the
operation completes. A concurrent operation fails. If an operation is killed with
SIGKILL, inspect its processes before removing a leftover lock or `starting` claim;
the script does not guess that another operation's lock is stale.

Live capture uses the same dedicated config with an explicit `PLAYWRIGHT_BASE_URL`,
authentication setup and zero retries. The test deletes only the note it created,
in a `finally` block; neither mode invokes the shared API cleanup. `CI=true` disables
screenshots and video. Point the login helper at the capture root even in anonymous
mode: its absent `shiro.ini` prevents fallback to unrelated repository credentials.
Authentication state and browser results use separate directories under a unique
temporary run directory. They do not overwrite the ordinary suite's auth snapshot or
test results. Set `ZEPPELIN_CORE_CONTRACT_RUN_DIR` to keep them in a chosen capture
directory; use a different directory for each concurrent run. The auth snapshot is
under `.auth/user.json` and results are under `results/`; remove the run directory
when its artifacts are no longer needed.

The server discards inherited `ZEPPELIN_*` settings, JVM option variables
(`JAVA_OPTS`, `JAVA_TOOL_OPTIONS`, `_JAVA_OPTIONS`, `JDK_JAVA_OPTIONS`) and `CLASSPATH`.
It explicitly selects local `VFSNotebookRepo` storage and loopback binding, so a shell's
remote notebook configuration cannot redirect capture writes. `JAVA_HOME` and `PATH`
still select the installed toolchain.

```bash
CAPTURE_ROOT="$(mktemp -d)"
e2e/core-contract/capture-server.sh start --root "${CAPTURE_ROOT}" --port 18080
ZEPPELIN_E2E_SHIRO_INI="${CAPTURE_ROOT}/conf/shiro.ini" \
  ZEPPELIN_CORE_CONTRACT_RUN_DIR="${CAPTURE_ROOT}/browser" \
  CI=true PLAYWRIGHT_BASE_URL=http://127.0.0.1:18080 npm run e2e:core-contract:live
e2e/core-contract/capture-server.sh stop --root "${CAPTURE_ROOT}"
```

For authenticated capture, add `--mode auth` to start. That installs
`shiro.ini.template` in the capture root; the same `ZEPPELIN_E2E_SHIRO_INI` setting
selects it. The helper wiring and a successful authenticated capture are separate
checks. Multi-user permission scenarios remain the responsibility of ZEPPELIN-6673.
`npm run check:core-contract-auth` runs a browser-backed anonymous setup regression
in a disposable directory and verifies that the ordinary auth snapshot is preserved.
It requires installed Chromium but no Zeppelin server; the Node-only fixture check
skips this browser regression.

Replay is strict about what it answers: a request no remaining record can answer, a
WebSocket frame that does not match the next recorded one, and any record left
unconsumed all fail the test. The limits of that strictness are in "What version 1
does not model". Maven runs the format checks in its test phase and the capture-server checks
in integration-test. The browser tests are part of the ordinary e2e suite, so they
run wherever it does. The live capture is the one layer nothing runs for you: it is
tagged `@live` and excluded until `npm run e2e:core-contract:live` asks for it.

These checks prove fixture shape and adapter transport behavior. Separate E2E
scenarios must cover a running Zeppelin server, authorization, collaboration,
reconnection, interpreter execution, streaming output, performance, and
accessibility.

### How a replay reports failure

The Playwright adapter reports a broken fixture by rejecting the route handler, which
Playwright surfaces as an unhandled error and attributes to the running test. A route
whose key no remaining record can answer is rejected immediately. A route that is
merely waiting is not: the fixture cannot tell "the page has not sent that request
yet" from "the page will never send it", so that case is left to Playwright's own
test timeout.

## Capturing safely

`createNotebookTransportRecorder(metadata)` records only `/api/notebook` REST
traffic and `/ws` frames. It redacts configured sensitive and volatile fields
before it writes a fixture. JSON WebSocket frames are normalized and redacted;
binary frames are rejected during capture until a binary redaction policy is
implemented. Replay still supports deliberately authored binary fixtures for
protocol-level tests.
Each WebSocket record must contain exactly one of `payloadText` and `payloadBase64`;
validation rejects records with both representations.

A notebook request still awaiting a response when `stop()` or `write()` is called
fails the capture, as does a failed request. Await scenario completion before stopping.
`validateFixture` also rejects request records without a matching response, so an
incomplete hand-authored fixture fails validation before replay.

### Which identifiers are normalized, and which are kept

Timestamp fields `dateCreated`, `dateStarted`, `dateFinished`, `dateUpdated`,
`lastUpdated` and `time` are replaced by placeholders. Timing-dependent scenarios
remain outside v1.

WebSocket envelope `msgId` is a correlation key: Angular uses the echoed ID to focus
a locally inserted or cloned paragraph. Capture assigns distinct stable placeholders
such as `<msgId:1>` and preserves repeated references to each ID. Replay binds these
to IDs actually sent by the client and substitutes the live ID in matching responses.
It rejects inconsistent or reused bindings. Legacy fixtures containing the erased
`<msgId>` envelope value must be recaptured because their identity relationships
cannot be recovered. Non-envelope `msgId` fields retain the generic normalization rule.

Names that carry a person are masked the same way, by field name only: `user`, which is
what Zeppelin calls the acting principal on a paragraph, the `owners`, `readers`,
`writers` and `runners` that `/api/notebook/{id}/permissions` answers with, the `users` a
`COLLABORATIVE_MODE_STATUS` frame lists while more than one session has the note open,
and `roles`. Without them
an authenticated capture would write whoever took it, and whoever else can reach the
note, into the fixture. A permission set is masked entry by entry, so the array keeps its
shape and its count. The text rules leave `user=` and `owners=` alone, because both are
ordinary in a url and in note text.

`noteId` and `paragraphId` are kept as captured, preserving references across URLs,
bodies and frames. This is a v1 reproducibility tradeoff: recapturing the same flow
can produce a different file. A future bijective ID mapping could remove that churn
without losing references; it is not required for replaying one captured trace.

They are stable within a fixture but not between captures, and a Zeppelin paragraph id
carries the creation time in it (`paragraph_1757...`), so re-capturing a scenario
produces a textually different fixture even when the contract has not changed. A future
version could map each id to a numbered placeholder consistently and get diff-clean
re-captures without losing the reference; version 1 does not, and a re-capture is
reviewed as a new recording rather than as a diff.

### What redaction reaches, and what it does not

Redaction acts on field names first. A name counts as sensitive when one of its words
is a sensitive word, or when it ends in one, or - for a name written in one uppercase
run, as environment variables are - when it merely contains one. Names are split on
separators and on camel case, so `accessToken`, `secretKey`, `aws_secret_access_key`,
`spark.hadoop.fs.s3a.secret.key`, `x-api-key`, `PGPASSWORD`, `SECRETKEY`,
`private_key` and `passphrase` all count, while `tokenizer`, `secretary`, `tokens`,
`max_tokens`, `privately` and `keyboard` do not. A name outside the list is not masked
- `pwd`, `bearer` and `sessionId` are not - so add the word rather than relying on the
shape of the value. A name in mixed case with no separator, such as `SECRETkey`, falls
between the rules and is missed.

Two fields carry no inner structure for that to work on, and only those two are
scanned as text: `url`, whose credential sits in the query under no name of its own,
and `bodyRaw`, which arrives as one opaque string. There the rules look for
`name=value` and `name: value`, a url's `user:password@host`, and a header credential
that runs to the end of its line.

A WebSocket frame picks its own treatment. A frame that parses as JSON is redacted by
key, like any other record; a frame that does not is text-scanned as one string, the
same way `bodyRaw` is. Scanning the JSON as text on top of the key pass would rewrite
the note inside it and leave a capture the fixture could no longer replay.

**Everything else keeps its text.** A json field, and a string inside an array, are
covered by the name they sit under, so the value is left as written. This is deliberate: the text rules cannot tell a credential
from prose that mentions one, and a note is full of prose that does. Scanning note
text rewrote `ticketCount = df.count()`, `SELECT ... WHERE ticket_id = 42` and
`const cookieBanner = document.getElementById("x")`, which is a worse outcome for a
fixture than the narrow gap it closed. The capture helper starts an isolated, empty
server for the same reason: a fixture's note text is written by the test that captured
it, not by whoever owns the machine.

What the text rules still do not reach, where they do scan: an unquoted
value containing a space keeps its tail; a `name: value` pair is only matched where the
name opens an unindented line, a quoted string, an object or a JSON array entry, so an
indented yaml key and a `- ` list item are left as written; a url's userinfo is only
matched in its `user:password@host` form, so `token@host` is left alone; a name and its
value split across two fields, as in `{"name": "password", "value": "hunter2"}`, has no
name beside the value to match on; `password==abc` is read as a comparison rather than
an assignment; and a credential that carries no name at all - a bare token, a base64
blob - is invisible to every rule here.

In text a purely numeric value is left alone for names ending in `principal`, because
that word is also an accounting term. A field named `principal` is masked whatever it
holds.

Do not rely on any of this for a fixture captured from a server holding real
credentials. Capture from the isolated server this directory starts.

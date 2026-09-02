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

Every committed fixture includes these required fields. Both
`createNotebookTransportRecorder` and `validateFixture` enforce them:

```json
{
  "version": 1,
  "metadata": {
    "scenario": "Open a notebook",
    "owner": "zeppelin-web-angular",
    "coveredOperations": ["GET_NOTE"],
    "knownExclusions": ["Live interpreter execution is covered by a separate E2E scenario"]
  },
  "records": []
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

## Test layers

Run the fast format, redaction, ordering, and replay checks with:

```bash
npm run check:core-contract-fixtures
```

Run the Playwright adapter checks with:

```bash
npm run e2e:core-contract
```

The live capture scenario is intentionally separate because it requires a
running Zeppelin test server with a usable Notebook session:

```bash
npm run e2e:core-contract:live
```

The format check and adapter replay command use strict replay: unexpected
traffic, out-of-order traffic, or unconsumed records fail the test. The Maven
test phase runs the format check.

These checks prove fixture shape and adapter transport behavior. Separate E2E
scenarios must cover a running Zeppelin server, authorization, collaboration,
reconnection, interpreter execution, streaming output, performance, and
accessibility.

## Capturing safely

`createNotebookTransportRecorder(metadata)` records only `/api/notebook` REST
traffic and `/ws` frames. It redacts configured sensitive and volatile fields
before it writes a fixture. JSON WebSocket frames are normalized and redacted;
binary frames are rejected during capture until a binary redaction policy is
implemented. Replay still supports deliberately authored binary fixtures for
protocol-level tests.

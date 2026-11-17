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

# Getting Started — Zeppelin MCP Server (experimental)

This guide takes you from a fresh checkout to an AI coding agent driving your
Apache Zeppelin instance through the [Model Context Protocol](https://modelcontextprotocol.io).

> **Status: experimental.** There is no published package yet — you build and
> install it from source (below). Distribution through standard channels
> (PyPI, the Zeppelin binary distribution) is planned. The server itself is a
> thin client over Zeppelin's stable [REST API](https://zeppelin.apache.org/docs/latest/usage/rest_api/notebook.html),
> so it works against any reachable Zeppelin server with no server-side changes.

### Open items (pre-GA)

These are unresolved and tracked for follow-up — see the CR discussion:

- **Bundling in binary distributions.** Whether to ship the `zeppelin-mcp`
  console script (or a packaged artifact) inside the Zeppelin binary
  distribution, vs. publishing to PyPI and letting users `pip install`. Until
  decided, this module is build-from-source only.
- **Standard distribution channel** (PyPI release cadence, versioning).
- **Semantic search over notebooks as agent knowledge.** Optionally index
  notebook/paragraph text plus interpreter metadata so an agent can discover
  relevant notebooks by meaning (e.g. a `search_notebooks` tool) and build
  richer context, rather than only operating on an explicitly selected note.
  Not implemented today; the server currently exposes only direct REST-backed
  operations.

---

## 1. Prerequisites

| Requirement | Notes |
| --- | --- |
| **Python 3.10+** | `python3 --version`. The server and its console script run on this. |
| **A running Zeppelin** | Local (`bin/zeppelin-daemon.sh start` → `http://localhost:8080`) or any reachable instance. |
| **An MCP-capable agent** | Claude Code, Kiro, Codex, Cursor, or any MCP client. |
| **git** | To clone the repo (if you haven't already). |

Confirm Zeppelin is reachable before wiring up any agent:

```bash
curl -s http://localhost:8080/api/notebook | head -c 200
```

A JSON response (even an empty list) means you're good. A connection error
means start Zeppelin first.

---

## 2. Build & install from source

The server lives in the `zeppelin-mcp/` module. Install it into an isolated
virtualenv so the `zeppelin-mcp` console script lands on a predictable path.

```bash
cd zeppelin-mcp

# Create and activate an isolated environment
python3 -m venv .venv
source .venv/bin/activate          # Windows: .venv\Scripts\activate

# Install the package (editable, so source edits take effect immediately)
pip install -e .

# Verify the console script is installed and resolves
which zeppelin-mcp
```

`which zeppelin-mcp` prints an **absolute path** like
`/path/to/zeppelin/zeppelin-mcp/.venv/bin/zeppelin-mcp`. **Copy this path** —
some agents (notably Codex) need the absolute path because they don't inherit
your shell's activated venv.

> **Why a venv?** The console script is only on `PATH` while the venv is active.
> Agents launch the server as a subprocess and usually do **not** have your venv
> activated, so pointing them at the absolute path is the reliable option.

### Optional: run from the Maven reactor

The module is wired into the Zeppelin build. This creates a venv under
`target/venv` and runs the test suite (it does not install onto your `PATH`):

```bash
# from the repo root
./mvnw test -pl zeppelin-mcp
# skip the Python tests like every other module:
./mvnw clean package -pl zeppelin-mcp -DskipTests
```

---

## 3. Smoke-test the server

Before involving an agent, confirm the server starts and can reach Zeppelin.
It speaks MCP over **stdio**, so a bare launch will sit waiting for a client on
stdin — that's expected. Send it nothing and Ctrl-C; a clean start with no
import/connection error is the signal you want:

```bash
ZEPPELIN_URL=http://localhost:8080 zeppelin-mcp
# (no output, waiting on stdin) -> press Ctrl-C
```

If you see a `ModuleNotFoundError`, the venv isn't active or the install
failed — revisit step 2. If you want a fuller end-to-end check, wire up an
agent (step 5) and ask it to list interpreters.

---

## 4. Configuration (environment variables)

The server is configured entirely through environment variables — there are no
config files of its own.

| Variable | Default | Description |
| --- | --- | --- |
| `ZEPPELIN_URL` | *(required)* | Base server URL, e.g. `http://localhost:8080`. |
| `ZEPPELIN_AUTH` | `none` | `none`, `basic`, or `header`. |
| `ZEPPELIN_USER` | — | Username for `basic` auth. |
| `ZEPPELIN_PASSWORD` | — | Password for `basic` auth. |
| `ZEPPELIN_AUTH_HEADER` | `Authorization` | Header name for `header` auth. |
| `ZEPPELIN_AUTH_HEADER_VALUE` | — | Header value for `header` auth (e.g. `Bearer ...`). |
| `ZEPPELIN_TIMEOUT_SECONDS` | `600` | Per-request timeout (long enough for Spark/SQL jobs). |
| `ZEPPELIN_VERIFY_TLS` | `true` | Set `false` for self-signed dev servers. |
| `ZEPPELIN_NOTEBOOK` | — | Optional note id or URL to pre-select on startup. |

For local anonymous Zeppelin, `ZEPPELIN_URL` alone is enough. For a
Shiro-secured server use `ZEPPELIN_AUTH=basic` with user/password; for an
SSO/reverse-proxied server use `ZEPPELIN_AUTH=header`.

---

## 5. Connect an MCP client

The server is launched as a subprocess by the agent. In every example, replace
`zeppelin-mcp` with the **absolute path** from step 2 if the bare command isn't
on the agent's `PATH`.

### Claude Code

```bash
claude mcp add zeppelin \
  --env ZEPPELIN_URL=http://localhost:8080 \
  -- /path/to/zeppelin/zeppelin-mcp/.venv/bin/zeppelin-mcp
```

### Kiro

Kiro reads MCP config from `.kiro/settings/mcp.json` (workspace) or
`~/.kiro/settings/mcp.json` (user-level):

```json
{
  "mcpServers": {
    "zeppelin": {
      "command": "/path/to/zeppelin/zeppelin-mcp/.venv/bin/zeppelin-mcp",
      "args": [],
      "env": {
        "ZEPPELIN_URL": "http://localhost:8080"
      },
      "disabled": false,
      "autoApprove": []
    }
  }
}
```

### Codex (OpenAI Codex CLI)

Codex reads MCP servers from `~/.codex/config.toml`:

```toml
[mcp_servers.zeppelin]
command = "/path/to/zeppelin/zeppelin-mcp/.venv/bin/zeppelin-mcp"
args = []
env = { ZEPPELIN_URL = "http://localhost:8080" }
```

### Generic MCP client (`mcp.json` / `claude_desktop_config.json` / Cursor)

```json
{
  "mcpServers": {
    "zeppelin": {
      "command": "/path/to/zeppelin/zeppelin-mcp/.venv/bin/zeppelin-mcp",
      "env": {
        "ZEPPELIN_URL": "http://localhost:8080",
        "ZEPPELIN_AUTH": "basic",
        "ZEPPELIN_USER": "admin",
        "ZEPPELIN_PASSWORD": "password1"
      }
    }
  }
}
```

> **Compatibility note.** Any spec-compliant MCP client works — the server has
> no client-specific code; it just runs a stdio MCP server. Only the location
> and format of the config differ between agents. After editing a config file,
> restart the agent (or reload its MCP servers) so it picks up the change.

---

## 6. First run — verify end to end

Ask your agent something like:

> "List the Zeppelin interpreters, then create a notebook called `mcp-smoke`,
> run `%md # hello from MCP` in it, and read it back."

Under the hood the agent calls `list_interpreters` → `create_notebook` →
`run_code` → `read_notebook`. If the markdown paragraph renders, your wiring is
correct.

### Available tools

| Tool | What it does |
| --- | --- |
| `set_notebook(notebook)` | Select a notebook (bare id or pasted URL); returns its rendered contents. |
| `create_notebook(name, default_interpreter_group="")` | Create a notebook and select it. |
| `run_code(code, interpreter="")` | Add a paragraph, run it synchronously, return its output. |
| `read_notebook()` | Render the selected notebook (paragraph ids, code, truncated output). |
| `delete_paragraph(paragraph_id)` | Delete a paragraph by id. |
| `clear_output()` | Clear all paragraph output, keeping the code. |
| `list_interpreters()` | List interpreter settings (id, name, group). |
| `restart_interpreter(setting_id, scope_to_notebook=False)` | Restart a hung/broken interpreter. |
| `write_summary(summary_markdown, title="")` | Insert a Markdown summary as the first paragraph; optionally rename the note. |

A typical agent loop: `set_notebook` (or `create_notebook`) → one or more
`run_code` calls → `read_notebook` to inspect results → `write_summary` with the
agent's own data-driven findings.

---

## 7. Troubleshooting

| Symptom | Likely cause & fix |
| --- | --- |
| Agent reports the server failed to start | The `command` path is wrong or the venv isn't installed. Use the absolute path from `which zeppelin-mcp` (step 2). |
| `ModuleNotFoundError: mcp` (or `httpx`) | `pip install -e .` wasn't run in the active venv. Reinstall (step 2). |
| Tools return connection/timeout errors | `ZEPPELIN_URL` is unreachable from where the agent runs, or Zeppelin is down. Re-run the `curl` check in step 1. |
| `401`/`403` from tools | Zeppelin requires auth. Set `ZEPPELIN_AUTH=basic` (+ user/password) or `header`. |
| TLS errors against a dev server | Set `ZEPPELIN_VERIFY_TLS=false`. |
| Long Spark/SQL paragraph times out | Raise `ZEPPELIN_TIMEOUT_SECONDS`. |
| `python3: command not found` during Maven build | Set `-Dpython.executable=python` or skip Python tests with `-Dpython.test.skip=true`. |

---

## 8. Developing on the server

```bash
cd zeppelin-mcp
source .venv/bin/activate
pip install -e '.[test]'
pytest                       # unit suite + coverage
```

Tests run entirely against an in-memory fake Zeppelin (an `httpx.MockTransport`
handler in `tests/conftest.py`) — no live server needed.

See [`README.md`](README.md) for the module reference and architecture notes
(`client.py` owns REST calls, `context.py` renders results, `server.py` wires
tools to session state).

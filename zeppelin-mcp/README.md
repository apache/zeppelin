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

# Zeppelin MCP Server

A [Model Context Protocol](https://modelcontextprotocol.io) (MCP) server that lets
AI coding agents — Claude Code, Kiro, Cursor, and any other MCP client — drive an
Apache Zeppelin instance: select or create a notebook, run paragraphs in any
interpreter, read results, clean up paragraphs, restart a stuck interpreter, and
write a summary back into the notebook.

It talks to Zeppelin over the standard [REST API](https://zeppelin.apache.org/docs/latest/usage/rest_api/notebook.html),
so it works against any reachable Zeppelin server without modifying it.

## Tools

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

## Installation

Requires Python 3.10+.

```bash
# From a checkout of this module:
pip install /path/to/zeppelin/zeppelin-mcp

# Or, once published to PyPI:
pip install zeppelin-mcp
```

This installs the `zeppelin-mcp` console script, which runs the server over stdio.

## Configuration

The server is configured entirely through environment variables:

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

### Authentication modes

- **`none`** — anonymous Zeppelin (no `shiro.ini` realm). The default for local dev.
- **`basic`** — Shiro form login via `POST /api/login`; the session cookie is reused.
- **`header`** — attaches a fixed header to every request. Use this for
  reverse-proxied / SSO deployments where auth is a bearer token or session cookie.

## Connecting from an MCP client

### Claude Code

```bash
claude mcp add zeppelin \
  --env ZEPPELIN_URL=http://localhost:8080 \
  -- zeppelin-mcp
```

### Generic MCP client (`mcp.json` / `claude_desktop_config.json`)

```json
{
  "mcpServers": {
    "zeppelin": {
      "command": "zeppelin-mcp",
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

## Development

```bash
cd zeppelin-mcp
python3 -m venv .venv && source .venv/bin/activate
pip install -e '.[test]'
pytest                       # runs the unit suite with coverage
```

The tests run entirely against an in-memory fake Zeppelin (an `httpx.MockTransport`
handler in `tests/conftest.py`) — no live server is needed.

### Building with Maven

The module is wired into the Zeppelin reactor build. `mvn test -pl zeppelin-mcp`
creates a virtualenv under `target/venv` and runs pytest. A standard
`./mvnw clean package -DskipTests` skips the Python tests like every other module.

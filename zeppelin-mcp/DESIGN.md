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

# Design: Zeppelin MCP Server (ZEPPELIN-6434)

**Status:** Proposal / Request for comment
**JIRA:** [ZEPPELIN-6434](https://issues.apache.org/jira/browse/ZEPPELIN-6434)
**PR:** [apache/zeppelin#5277](https://github.com/apache/zeppelin/pull/5277)
**Author:** Kalyan Kanuri

---

## 1. Summary

This proposes `zeppelin-mcp`, a [Model Context Protocol](https://modelcontextprotocol.io)
(MCP) server that lets AI coding agents — Claude Code, Kiro, Cursor, Codex, and any
other MCP client — operate an Apache Zeppelin instance: select or create a notebook,
run paragraphs in any interpreter, read results, delete paragraphs, clear output,
restart a stuck interpreter, and write a summary back into the notebook.

The immediate deliverable is a **standalone server that speaks the stdio transport**
and calls Zeppelin's existing REST API — no server-side changes required. This document
also lays out the **longer-term path to a remote, in-instance MCP endpoint** hosted by
`zeppelin-server` (raised by @jongyoul in the PR review), and argues the two are phases
of one design rather than competing options.

## 2. Motivation

Notebook work is increasingly agent-assisted. An analyst using an AI coding agent wants
to say "profile this table and chart the daily trend" and have the agent actually run
paragraphs in Zeppelin, read the results, iterate, and leave a written summary behind —
the same loop a human runs, but driven by the agent.

MCP is the emerging standard interface for exactly this: it is how agents discover and
call external tools. Adding an MCP server to Zeppelin makes every MCP-capable agent a
first-class Zeppelin client without each agent having to hand-roll REST integration.

### 2.1 Why an MCP server, not "just the REST API"

A fair question is: Zeppelin already has a REST API — why add anything? The answer is
that a useful MCP server is **not** a 1:1 re-serialization of the REST API. It is an
agent-facing *orchestration* layer. The value lives in behavior the REST API
deliberately does not encode, for example:

- **Synchronous run semantics.** The REST API is fire-and-forget: `POST /notebook/run`
  then poll paragraph status. An agent needs "run this and give me the result or the
  error." The MCP tool owns the polling loop, terminal-state detection, and output
  extraction so the agent sees one call.
- **Failure recovery and actionable errors.** Detecting a stuck/restarting interpreter,
  turning a raw stack trace into a next step ("interpreter restarting, retry in ~60s"),
  or falling back to a second engine on error — none of that is an API concept.
- **Output shaping for a model.** Tabular results parsed into compact text, long output
  truncated, prior paragraphs summarized into a context blob the model can consume.
- **Cross-system fusion.** A tool can enrich a slow query with Spark UI diagnostics
  (jobs/stages/executors/GC) pulled from a *different* endpoint — something no single
  Zeppelin REST call returns.

These are proven in practice: two internal Zeppelin MCP servers the author operates
(`swift-zepl`, `emr-zepl`) implement exactly this orchestration on top of the same REST
API, and it is where essentially all of their value sits. Critically, **this
orchestration logic is independent of where the server runs or which transport it
speaks** — which is what makes the phased plan in §5 safe.

## 3. MCP roles and hosting — terminology

To keep the architecture discussion precise, the MCP spec fixes three roles:

- **MCP Host** — the AI application (Claude Code, Cursor, Kiro). Not ours.
- **MCP Client** — a connector the host instantiates, one per server. Not ours; the
  host provides it.
- **MCP Server** — the program that exposes tools. **This is what we build**, in every
  option below.

So the design choice is *not* "server vs client" — we always write the server. The real
axis is **how the server is hosted and what transport it uses**:

| | Local server | Remote server |
| --- | --- | --- |
| Transport | stdio | Streamable HTTP (SSE) |
| How it runs | host launches it as a subprocess on the user's machine | long-lived HTTP service; agents connect over the network |
| Clients served | one | many, concurrently |
| Auth | env vars / local creds | HTTP bearer / OAuth, shared session |
| This proposal, phase 1 | ✅ | |
| @jongyoul's suggestion | | ✅ ("remote MCP server in the instance") |

The MCP spec endorses both: *"Local MCP servers that use STDIO typically serve a single
client, whereas remote MCP servers that use Streamable HTTP will typically serve many
clients."*

## 4. Options considered

### Option A — Standalone stdio server (this PR, phase 1)

Each user runs `zeppelin-mcp` as a local subprocess; it holds their `ZEPPELIN_URL` and
credentials and calls the REST API. Implemented in Python on the official MCP SDK
(FastMCP), mirroring the precedent of the existing `python/` module.

**Pros**
- Zero server-side changes; works against **any** reachable Zeppelin, including older
  releases already deployed in the wild.
- Uses the mature MCP SDK (Python FastMCP).
- Can fuse in signals the server itself cannot cleanly reach (e.g. Spark UI on :4040 via
  a proxy), as the internal `emr-zepl` server does today.
- Lets the community iterate on the **tool surface** (which tools, argument shapes,
  output formats) before committing it to the server's release cadence.

**Cons**
- Every user installs and configures a process; N users ⇒ N processes and N credential
  copies.
- It is not "the Zeppelin instance offers MCP" — it is a client-side tool.

### Option B — Remote in-instance HTTP endpoint (@jongyoul's suggestion, phase 2 target)

`zeppelin-server` hosts a single `/mcp` endpoint over Streamable HTTP. Agents point at
`https://<zeppelin-host>/mcp`; nothing to install.

**Pros**
- One deployment, one auth surface — reuse the existing Shiro/Knox session; adopt the
  HTTP-standard bearer/OAuth the MCP spec recommends for remote servers.
- Multi-user by construction.
- Can expose **in-process state the REST API does not publish** — live interpreter
  status events, running-paragraph streams — and can **stream** long-running paragraph
  output over SSE instead of client-side polling.

**Cons**
- Requires server-side work in Java, and couples MCP releases to Zeppelin releases.
- Only works on servers new enough to ship it — loses Option A's "works against any
  existing Zeppelin" property.
- The mature MCP SDK is Python; an in-JVM server means either a Java MCP implementation
  or a Python sidecar the server supervises.

### Option C — Agent skill instead of a server

For reference: some notebook tools (e.g. marimo's "pair") ship an *agent skill* — docs
plus a couple of scripts that teach the agent to drive a live kernel directly — rather
than an MCP server. This is powerful for a single local kernel but is host-specific
(not a portable MCP interface) and does not fit Zeppelin's multi-interpreter,
server-mediated model. Not pursued, but noted so the tradeoff is on record.

## 5. Recommendation — phased, not either/or

1. **Phase 1 (this PR): land Option A**, the standalone stdio server, as the reference
   implementation during the experimental phase. It ships now, needs no server changes,
   works against every existing Zeppelin, and lets us stabilize the tool surface.
2. **Phase 2 (follow-up): add Option B**, a remote Streamable-HTTP endpoint hosted by
   `zeppelin-server`, reusing Shiro auth, once the tool surface is proven.

This is safe precisely because the **tool definitions and orchestration logic are
transport-agnostic**: the same tools move from stdio to HTTP without redesign. Phase 1
is therefore a stepping stone to Phase 2, not throwaway work. Agreeing the destination
(Option B) up front lets us design the Phase-1 tool boundary so it ports cleanly.

## 6. Tool surface (phase 1)

| Tool | What it does |
| --- | --- |
| `set_notebook(notebook)` | Select a notebook (bare id or pasted URL); returns rendered contents. |
| `create_notebook(name, default_interpreter_group="")` | Create a notebook and select it. |
| `run_code(code, interpreter="")` | Add a paragraph, run it synchronously, return its output. |
| `read_notebook()` | Render the selected notebook (paragraph ids, code, truncated output). |
| `delete_paragraph(paragraph_id)` | Delete a paragraph by id. |
| `clear_output()` | Clear all paragraph output, keeping the code. |
| `list_interpreters()` | List interpreter settings (id, name, group). |
| `restart_interpreter(setting_id, scope_to_notebook=False)` | Restart a hung/broken interpreter. |
| `write_summary(summary_markdown, title="")` | Insert a Markdown summary as the first paragraph; optionally rename the note. |

Typical agent loop: `set_notebook` (or `create_notebook`) → one or more `run_code` →
`read_notebook` → `write_summary`.

## 7. Security considerations

- **Phase 1** inherits the caller's Zeppelin credentials via env vars and supports
  `none` / `basic` (Shiro form login) / `header` (reverse-proxy / SSO bearer) auth
  modes. It grants an agent exactly the permissions of the configured user — no
  escalation. TLS verification is on by default.
- **Phase 2** must authenticate every connection (the spec requires it for remote
  servers): validate the `Origin` header against DNS-rebinding, bind appropriately, and
  reuse Zeppelin's existing authentication rather than inventing a parallel one.

## 8. Documentation & distribution

- **User docs:** a page under `docs/` (Jekyll site) alongside the existing module
  `README.md` and `GETTING_STARTED.md`.
- **Distribution (open item):** build-from-source today; the choice between publishing
  to PyPI (`pip install zeppelin-mcp`) and/or bundling the console script in the Zeppelin
  binary distribution is deferred to the community discussion.

## 9. Open questions for the community

1. Do we agree Option B (remote in-instance endpoint) is the right long-term home, with
   Option A as phase 1?
2. Distribution channel: PyPI, binary-distribution bundle, or both?
3. Preferred implementation language for a future Phase-2 endpoint (Java MCP impl vs.
   supervised Python sidecar)?
4. Should a future `search_notebooks` (semantic discovery over notebook/paragraph text +
   interpreter metadata) be in scope, or a separate proposal?

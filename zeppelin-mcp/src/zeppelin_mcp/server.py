#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

"""MCP server exposing Apache Zeppelin notebook operations to AI agents.

The server is deliberately thin: every tool maps to one or two REST calls via
:class:`~zeppelin_mcp.client.ZeppelinClient` and returns plain text suitable
for an LLM. Notebook-mutating logic lives in the client; result rendering lives
in :mod:`zeppelin_mcp.context`. This module wires those together, owns the
"currently selected notebook" session state, and reads connection settings
from the environment.

Environment variables
----------------------
ZEPPELIN_URL              Base server URL, e.g. ``http://localhost:8080`` (required).
ZEPPELIN_AUTH             ``none`` (default), ``basic``, or ``header``.
ZEPPELIN_USER             Username for ``basic`` auth.
ZEPPELIN_PASSWORD         Password for ``basic`` auth.
ZEPPELIN_AUTH_HEADER      Header name for ``header`` auth (default ``Authorization``).
ZEPPELIN_AUTH_HEADER_VALUE  Header value for ``header`` auth.
ZEPPELIN_TIMEOUT_SECONDS  Per-request timeout (default 600).
ZEPPELIN_VERIFY_TLS       ``false`` to disable TLS verification (default ``true``).
ZEPPELIN_NOTEBOOK         Optional notebook id or URL to pre-select on startup.
"""

from __future__ import annotations

import os
import re
from typing import Any, Optional

from mcp.server.fastmcp import FastMCP

from .client import AuthMode, ZeppelinClient, ZeppelinConfig, ZeppelinError
from .context import build_context

mcp = FastMCP("zeppelin")

# Matches a note id in a Zeppelin notebook URL (``.../#/notebook/<id>``) so a
# user can paste either a bare id or the URL from their browser.
_NOTE_URL_RE = re.compile(r"#/notebook/([^/?#]+)")

# Lazily-created client + the currently selected note id. Kept module-level so
# all tool invocations in one MCP session share connection state and the
# "current notebook" the user configured.
_state: dict[str, Any] = {"client": None, "note_id": None}


# -- configuration -----------------------------------------------------------

def _env_bool(name: str, default: bool) -> bool:
    raw = os.getenv(name)
    if raw is None:
        return default
    return raw.strip().lower() in ("1", "true", "yes", "on")


def _config_from_env() -> ZeppelinConfig:
    base_url = os.getenv("ZEPPELIN_URL")
    if not base_url:
        raise ZeppelinError("ZEPPELIN_URL is not set")
    auth_mode = AuthMode(os.getenv("ZEPPELIN_AUTH", "none").strip().lower())
    timeout = float(os.getenv("ZEPPELIN_TIMEOUT_SECONDS", "600"))
    return ZeppelinConfig(
        base_url=base_url,
        auth_mode=auth_mode,
        username=os.getenv("ZEPPELIN_USER"),
        password=os.getenv("ZEPPELIN_PASSWORD"),
        auth_header_name=os.getenv("ZEPPELIN_AUTH_HEADER", "Authorization"),
        auth_header_value=os.getenv("ZEPPELIN_AUTH_HEADER_VALUE"),
        timeout_seconds=timeout,
        verify_tls=_env_bool("ZEPPELIN_VERIFY_TLS", True),
    )


def _client() -> ZeppelinClient:
    """Return the shared client, creating it from the environment on first use."""
    if _state["client"] is None:
        _state["client"] = ZeppelinClient(_config_from_env())
    return _state["client"]


def _resolve_note_id(note: str) -> str:
    """Accept either a bare note id or a full notebook URL and return the id."""
    match = _NOTE_URL_RE.search(note)
    return match.group(1) if match else note.strip()


def _require_note() -> str:
    """Return the selected note id or raise a helpful error if none is set."""
    note_id = _state["note_id"]
    if not note_id:
        raise ZeppelinError(
            "No notebook selected. Call set_notebook(...) or create_notebook(...) first."
        )
    return note_id


def _format_result(result: dict[str, Any]) -> str:
    """Render an InterpreterResult (``{code, msg:[{type, data}]}``) as text."""
    code = result.get("code", "UNKNOWN")
    messages = result.get("msg") or []
    rendered = [m.get("data", "") for m in messages if m.get("data")]
    body = "\n".join(rendered) if rendered else "(no output)"
    return f"[{code}]\n{body}"


# -- tools --------------------------------------------------------------------

@mcp.tool()
def set_notebook(notebook: str) -> str:
    """Select the notebook that subsequent tools operate on.

    Accepts a bare note id (e.g. ``2MBCDQ2PV``) or a full notebook URL pasted
    from the browser. Returns the rendered contents of the notebook so the
    agent immediately has context (paragraph ids, code, and recent output).
    """
    note_id = _resolve_note_id(notebook)
    note = _client().get_note(note_id)
    _state["note_id"] = note_id
    return f"Selected notebook {note_id}.\n\n{build_context(note)}"


@mcp.tool()
def create_notebook(name: str, default_interpreter_group: str = "") -> str:
    """Create a new notebook and select it. Returns the new note id.

    ``name`` is the notebook path; Zeppelin groups notebooks by ``/`` so a name
    like ``analysis/2024-traffic`` nests it under an ``analysis`` folder.
    ``default_interpreter_group`` optionally pins the note's default interpreter
    (e.g. ``spark``); leave blank to use the server default.
    """
    note_id = _client().create_note(name, default_interpreter_group or None)
    _state["note_id"] = note_id
    return f"Created and selected notebook '{name}' (id={note_id})."


@mcp.tool()
def run_code(code: str, interpreter: str = "") -> str:
    """Add a paragraph to the selected notebook, run it, and return its output.

    ``interpreter`` is the magic without the ``%`` (e.g. ``sql``, ``python``,
    ``md``, ``spark``). If given, it is prepended as ``%interpreter``. If the
    code already starts with a ``%`` magic, pass ``interpreter`` empty to keep
    that magic as-is.

    The paragraph runs synchronously; this call blocks until the interpreter
    finishes (or the configured timeout elapses) and returns the result text.
    """
    note_id = _require_note()
    client = _client()

    text = code
    if interpreter:
        magic = interpreter if interpreter.startswith("%") else f"%{interpreter}"
        text = f"{magic}\n{code}"

    paragraph_id = client.add_paragraph(note_id, text)
    result = client.run_paragraph_sync(note_id, paragraph_id)
    return f"Paragraph {paragraph_id} finished.\n\n{_format_result(result)}"


@mcp.tool()
def read_notebook() -> str:
    """Return the full rendered contents of the selected notebook.

    Useful before writing a summary or deciding which paragraphs to delete:
    shows each paragraph's id, status, code, and (truncated) output.
    """
    note_id = _require_note()
    return build_context(_client().get_note(note_id))


@mcp.tool()
def delete_paragraph(paragraph_id: str) -> str:
    """Delete a paragraph from the selected notebook by its id.

    Paragraph ids look like ``paragraph_1778857961597_888823855`` and can be
    found via read_notebook / set_notebook output.
    """
    note_id = _require_note()
    _client().delete_paragraph(note_id, paragraph_id)
    return f"Deleted paragraph {paragraph_id}."


@mcp.tool()
def clear_output() -> str:
    """Clear the output of every paragraph in the selected notebook.

    Removes results but keeps the code, leaving a clean notebook to re-run.
    """
    note_id = _require_note()
    _client().clear_all_output(note_id)
    return f"Cleared all paragraph output in notebook {note_id}."


@mcp.tool()
def list_interpreters() -> str:
    """List configured interpreter settings (id, name, group).

    Use the ``id`` field with restart_interpreter when an interpreter is stuck.
    """
    settings = _client().list_interpreter_settings()
    if not settings:
        return "No interpreter settings found."
    lines = [
        f"- {s.get('name', '?')} (id={s.get('id', '?')}, group={s.get('group', '?')})"
        for s in settings
    ]
    return "Interpreter settings:\n" + "\n".join(lines)


@mcp.tool()
def restart_interpreter(setting_id: str, scope_to_notebook: bool = False) -> str:
    """Restart an interpreter setting by id (recovers a hung/broken interpreter).

    By default this restarts the interpreter globally. Set
    ``scope_to_notebook=True`` to restart it only for the selected notebook
    (relevant for per-note scoped interpreters).
    """
    note_id = _require_note() if scope_to_notebook else None
    _client().restart_interpreter(setting_id, note_id)
    scope = f" for notebook {note_id}" if note_id else ""
    return f"Restarted interpreter {setting_id}{scope}."


@mcp.tool()
def write_summary(summary_markdown: str, title: str = "") -> str:
    """Write a Markdown summary paragraph at the top of the selected notebook.

    Call read_notebook first to gather the data, then pass your own
    data-driven summary text here (highlight key numbers, trends, and
    anomalies rather than restating which queries ran). The summary is inserted
    as the first paragraph and rendered immediately. Optionally pass ``title``
    to also rename the notebook.
    """
    note_id = _require_note()
    client = _client()
    if title:
        client.rename_note(note_id, title)

    md = f"%md\n## Summary\n{summary_markdown}"
    paragraph_id = client.add_paragraph(note_id, md, index=0)
    client.run_paragraph_sync(note_id, paragraph_id)
    renamed = f" Notebook renamed to '{title}'." if title else ""
    return f"Summary written as paragraph {paragraph_id}.{renamed}"


def main() -> None:
    """Console-script entry point: run the MCP server over stdio."""
    mcp.run()


if __name__ == "__main__":
    main()

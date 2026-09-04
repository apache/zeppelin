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

"""Render a Zeppelin note into compact, agent-readable text.

The MCP server hands these renderings back to the calling LLM so it can reason
about what is already in a notebook (to write a summary, decide what to delete,
or continue an analysis) without the agent having to fetch and parse the raw
note JSON itself.
"""

from __future__ import annotations

from typing import Any

# Cap result rows per paragraph so a single wide query cannot blow the model's
# context window. The header row is always kept; this bounds the data rows.
MAX_RESULT_ROWS = 20

# Result message types that carry no useful text for an LLM (images, HTML
# widgets, Angular bindings). We note their presence but never inline them.
_NON_TEXT_RESULT_TYPES = {"IMG", "HTML", "ANGULAR", "NETWORK"}


def render_paragraph(index: int, paragraph: dict[str, Any]) -> str:
    """Render one paragraph as a labelled block: id, status, code, and output."""
    pid = paragraph.get("id", "<unknown>")
    status = paragraph.get("status", "<unknown>")
    text = (paragraph.get("text") or "").strip()

    lines = [f"--- Paragraph {index} [id={pid}] (status={status}) ---"]
    if text:
        lines.append("Code:")
        lines.append(text)

    results = paragraph.get("results") or {}
    messages = results.get("msg") or []
    rendered_any = False
    for msg in messages:
        block = _render_result_message(msg)
        if block:
            lines.append(block)
            rendered_any = True
    if not rendered_any:
        lines.append("(no output)")

    return "\n".join(lines)


def _render_result_message(msg: dict[str, Any]) -> str:
    """Render a single result message, truncating tabular data to MAX_RESULT_ROWS."""
    msg_type = (msg.get("type") or "TEXT").upper()
    data = msg.get("data") or ""

    if msg_type in _NON_TEXT_RESULT_TYPES:
        return f"Output [{msg_type}]: (non-text output omitted)"

    if not data:
        return ""

    if msg_type == "TABLE":
        return _render_table(data)

    # TEXT and anything else: pass through verbatim.
    return f"Output:\n{data.rstrip()}"


def _render_table(data: str) -> str:
    """Render a TSV TABLE result, keeping the header plus up to MAX_RESULT_ROWS rows."""
    rows = [row for row in data.split("\n") if row != ""]
    if not rows:
        return "Output [TABLE]: (empty)"

    header, body = rows[0], rows[1:]
    out = ["Output [TABLE]:", f"Columns: {header}"]
    out.extend(body[:MAX_RESULT_ROWS])
    if len(body) > MAX_RESULT_ROWS:
        out.append(f"... ({len(body)} total rows, {MAX_RESULT_ROWS} shown)")
    return "\n".join(out)


def build_context(note: dict[str, Any]) -> str:
    """Render a whole note (its name + every paragraph) into one text block."""
    name = note.get("name") or note.get("id") or "<unnamed>"
    paragraphs = note.get("paragraphs") or []

    parts = [f"Notebook: {name}", f"Paragraphs: {len(paragraphs)}", ""]
    for index, paragraph in enumerate(paragraphs):
        parts.append(render_paragraph(index, paragraph))
        parts.append("")
    return "\n".join(parts).rstrip() + "\n"

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

"""Unit tests for :mod:`zeppelin_mcp.context` rendering."""

from __future__ import annotations

from zeppelin_mcp.context import MAX_RESULT_ROWS, build_context, render_paragraph


def _table(rows: int) -> dict:
    header = "col_a\tcol_b"
    data = "\n".join([header] + [f"{i}\tv{i}" for i in range(rows)])
    return {
        "id": "paragraph_1",
        "status": "FINISHED",
        "text": "%sql\nselect *",
        "results": {"code": "SUCCESS", "msg": [{"type": "TABLE", "data": data}]},
    }


def test_render_paragraph_includes_id_status_code():
    para = {
        "id": "paragraph_9",
        "status": "FINISHED",
        "text": "%md\n# Title",
        "results": {"msg": [{"type": "TEXT", "data": "rendered"}]},
    }
    out = render_paragraph(0, para)
    assert "id=paragraph_9" in out
    assert "status=FINISHED" in out
    assert "%md\n# Title" in out
    assert "rendered" in out


def test_render_paragraph_no_output():
    para = {"id": "p", "status": "READY", "text": "code", "results": {"msg": []}}
    assert "(no output)" in render_paragraph(0, para)


def test_table_truncated_to_max_rows():
    out = render_paragraph(0, _table(rows=100))
    # Header + MAX_RESULT_ROWS data rows + the truncation notice.
    assert "Columns: col_a\tcol_b" in out
    assert f"... (100 total rows, {MAX_RESULT_ROWS} shown)" in out
    assert out.count("\tv") == MAX_RESULT_ROWS


def test_table_under_limit_not_truncated():
    out = render_paragraph(0, _table(rows=3))
    assert "total rows" not in out
    assert out.count("\tv") == 3


def test_non_text_output_omitted():
    para = {
        "id": "p",
        "status": "FINISHED",
        "text": "%python\nplot()",
        "results": {"msg": [{"type": "IMG", "data": "base64...."}]},
    }
    out = render_paragraph(0, para)
    assert "non-text output omitted" in out
    assert "base64" not in out


def test_build_context_lists_all_paragraphs():
    note = {
        "id": "note_1",
        "name": "Analysis",
        "paragraphs": [
            {"id": "p1", "status": "FINISHED", "text": "a", "results": {"msg": []}},
            {"id": "p2", "status": "FINISHED", "text": "b", "results": {"msg": []}},
        ],
    }
    out = build_context(note)
    assert "Notebook: Analysis" in out
    assert "Paragraphs: 2" in out
    assert "id=p1" in out and "id=p2" in out


def test_build_context_empty_notebook():
    out = build_context({"id": "n", "name": "Empty", "paragraphs": []})
    assert "Paragraphs: 0" in out


def test_build_context_falls_back_to_id_when_unnamed():
    out = build_context({"id": "note_xyz", "paragraphs": []})
    assert "Notebook: note_xyz" in out


def test_empty_table_rendered_as_empty():
    para = {
        "id": "p",
        "status": "FINISHED",
        "text": "%sql\nselect *",
        "results": {"msg": [{"type": "TABLE", "data": ""}]},
    }
    # Empty data short-circuits to "(no output)" since the message carries no data.
    assert "(no output)" in render_paragraph(0, para)


def test_table_with_only_blank_lines_is_empty():
    para = {
        "id": "p",
        "status": "FINISHED",
        "text": "x",
        "results": {"msg": [{"type": "TABLE", "data": "\n\n"}]},
    }
    assert "(empty)" in render_paragraph(0, para)

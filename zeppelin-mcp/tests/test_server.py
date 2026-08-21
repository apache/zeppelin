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

"""Tests for the MCP tool layer in :mod:`zeppelin_mcp.server`.

The tools read a shared module-level client/notebook state. Each test injects
the fake-backed client into that state via the ``server_state`` fixture so the
tool bodies exercise the real client against the in-memory Zeppelin.
"""

from __future__ import annotations

import pytest

from zeppelin_mcp import server
from zeppelin_mcp.client import (
    AuthMode,
    ZeppelinClient,
    ZeppelinConfig,
    ZeppelinError,
)


@pytest.fixture
def server_state(client, monkeypatch):
    """Point the server's shared state at the fake-backed client and reset it."""
    monkeypatch.setitem(server._state, "client", client)
    monkeypatch.setitem(server._state, "note_id", None)
    return server._state


# -- configuration -----------------------------------------------------------

def test_config_from_env_requires_url(monkeypatch):
    monkeypatch.delenv("ZEPPELIN_URL", raising=False)
    with pytest.raises(ZeppelinError, match="ZEPPELIN_URL is not set"):
        server._config_from_env()


def test_config_from_env_reads_all_fields(monkeypatch):
    monkeypatch.setenv("ZEPPELIN_URL", "http://host:8080")
    monkeypatch.setenv("ZEPPELIN_AUTH", "basic")
    monkeypatch.setenv("ZEPPELIN_USER", "admin")
    monkeypatch.setenv("ZEPPELIN_PASSWORD", "pw")
    monkeypatch.setenv("ZEPPELIN_TIMEOUT_SECONDS", "42")
    monkeypatch.setenv("ZEPPELIN_VERIFY_TLS", "false")
    config = server._config_from_env()
    assert config.base_url == "http://host:8080"
    assert config.auth_mode == AuthMode.BASIC
    assert config.username == "admin"
    assert config.timeout_seconds == 42
    assert config.verify_tls is False


@pytest.mark.parametrize(
    "raw,expected",
    [("true", True), ("1", True), ("yes", True), ("false", False), ("no", False)],
)
def test_env_bool(monkeypatch, raw, expected):
    monkeypatch.setenv("FLAG", raw)
    assert server._env_bool("FLAG", default=True) is expected


def test_env_bool_default_when_unset(monkeypatch):
    monkeypatch.delenv("FLAG", raising=False)
    assert server._env_bool("FLAG", default=True) is True


# -- note id resolution ------------------------------------------------------

@pytest.mark.parametrize(
    "value,expected",
    [
        ("2MBCDQ2PV", "2MBCDQ2PV"),
        ("  2MBCDQ2PV  ", "2MBCDQ2PV"),
        ("http://host:8080/#/notebook/2MBCDQ2PV", "2MBCDQ2PV"),
        ("http://host/#/notebook/2MBCDQ2PV?paragraph=x", "2MBCDQ2PV"),
    ],
)
def test_resolve_note_id(value, expected):
    assert server._resolve_note_id(value) == expected


# -- tool behaviour ----------------------------------------------------------

def test_create_notebook_selects_it(server_state, fake):
    out = server.create_notebook("My Analysis")
    note_id = server_state["note_id"]
    assert note_id in fake.notes
    assert note_id in out


def test_set_notebook_accepts_url_and_returns_context(server_state, fake):
    note_id = fake.add_note("Preexisting")
    out = server.set_notebook(f"http://host/#/notebook/{note_id}")
    assert server_state["note_id"] == note_id
    assert "Notebook: Preexisting" in out


def test_run_code_prepends_interpreter_magic(server_state, fake):
    server.create_notebook("n")
    out = server.run_code("select 1", interpreter="sql")
    note = fake.notes[server_state["note_id"]]
    assert note["paragraphs"][0]["text"] == "%sql\nselect 1"
    assert "SUCCESS" in out


def test_run_code_keeps_existing_magic(server_state, fake):
    server.create_notebook("n")
    server.run_code("%md\n# Title", interpreter="")
    note = fake.notes[server_state["note_id"]]
    assert note["paragraphs"][0]["text"] == "%md\n# Title"


def test_run_code_without_notebook_errors(server_state):
    with pytest.raises(ZeppelinError, match="No notebook selected"):
        server.run_code("select 1", interpreter="sql")


def test_read_notebook_renders_current(server_state, fake):
    note_id = fake.add_note("Readme", paragraphs=[
        {"id": "p1", "status": "FINISHED", "text": "code", "results": {"msg": []}},
    ])
    server.set_notebook(note_id)
    out = server.read_notebook()
    assert "Notebook: Readme" in out
    assert "id=p1" in out


def test_delete_paragraph(server_state, fake):
    server.create_notebook("n")
    note_id = server_state["note_id"]
    server.run_code("x", interpreter="md")
    pid = fake.notes[note_id]["paragraphs"][0]["id"]
    server.delete_paragraph(pid)
    assert fake.notes[note_id]["paragraphs"] == []


def test_clear_output(server_state, fake):
    server.create_notebook("n")
    note_id = server_state["note_id"]
    server.run_code("x", interpreter="md")
    server.clear_output()
    assert fake.notes[note_id]["paragraphs"][0]["results"]["msg"] == []


def test_list_interpreters(server_state):
    out = server.list_interpreters()
    assert "spark" in out and "id=spark" in out


def test_restart_interpreter_global(server_state, fake):
    server.create_notebook("n")
    server.restart_interpreter("spark")
    assert fake.restarted == [("spark", None)]


def test_restart_interpreter_scoped(server_state, fake):
    server.create_notebook("n")
    note_id = server_state["note_id"]
    server.restart_interpreter("spark", scope_to_notebook=True)
    assert fake.restarted == [("spark", {"noteId": note_id})]


def test_write_summary_inserts_first_paragraph(server_state, fake):
    server.create_notebook("n")
    note_id = server_state["note_id"]
    server.run_code("data", interpreter="sql")
    server.write_summary("Revenue up 12% WoW.", title="Weekly Review")
    note = fake.notes[note_id]
    # Summary is inserted at index 0 and the note is renamed.
    assert note["paragraphs"][0]["text"].startswith("%md\n## Summary")
    assert "Revenue up 12% WoW." in note["paragraphs"][0]["text"]
    assert note["name"] == "Weekly Review"


def test_write_summary_without_title_keeps_name(server_state, fake):
    server.create_notebook("Original")
    note_id = server_state["note_id"]
    server.write_summary("done")
    assert fake.notes[note_id]["name"] == "Original"


def test_list_interpreters_empty(server_state, fake):
    fake.interpreter_settings = []
    assert server.list_interpreters() == "No interpreter settings found."


def test_client_lazily_created_from_env(monkeypatch, fake):
    # Reset shared state so _client() must build a fresh client from the env.
    monkeypatch.setitem(server._state, "client", None)
    monkeypatch.setitem(server._state, "note_id", None)
    monkeypatch.setenv("ZEPPELIN_URL", "http://zeppelin.test")
    monkeypatch.setenv("ZEPPELIN_AUTH", "none")

    captured = {}

    def fake_ctor(config):
        captured["base_url"] = config.base_url
        import httpx
        from zeppelin_mcp.client import ZeppelinClient
        return ZeppelinClient(config, http_client=httpx.Client(
            transport=httpx.MockTransport(fake.handler)))

    monkeypatch.setattr(server, "ZeppelinClient", fake_ctor)
    created = server._client()
    assert captured["base_url"] == "http://zeppelin.test"
    # Subsequent calls reuse the cached client.
    assert server._client() is created

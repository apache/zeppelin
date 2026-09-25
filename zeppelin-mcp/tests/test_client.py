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

"""Unit tests for :class:`zeppelin_mcp.client.ZeppelinClient`."""

from __future__ import annotations

import httpx
import pytest

from zeppelin_mcp.client import (
    AuthMode,
    ZeppelinClient,
    ZeppelinConfig,
    ZeppelinError,
)


def test_api_root_strips_trailing_slash():
    config = ZeppelinConfig(base_url="http://host:8080/")
    assert config.api_root() == "http://host:8080/api"


def test_create_note_returns_id(client, fake):
    note_id = client.create_note("my/note")
    assert note_id in fake.notes
    assert fake.notes[note_id]["name"] == "my/note"


def test_create_note_passes_default_interpreter_group(client, fake):
    client.create_note("n", default_interpreter_group="spark")
    create_req = [r for r in fake.requests if r.method == "POST"][-1]
    assert b"defaultInterpreterGroup" in create_req.content
    assert b"spark" in create_req.content


def test_get_note_returns_document(client, fake):
    note_id = fake.add_note("hello")
    note = client.get_note(note_id)
    assert note["name"] == "hello"
    assert note["paragraphs"] == []


def test_get_note_missing_raises(client):
    with pytest.raises(ZeppelinError) as exc:
        client.get_note("does-not-exist")
    assert exc.value.status_code == 404


def test_add_and_get_paragraph(client, fake):
    note_id = fake.add_note()
    pid = client.add_paragraph(note_id, "%md\nhello")
    para = client.get_paragraph(note_id, pid)
    assert para["text"] == "%md\nhello"


def test_add_paragraph_at_index(client, fake):
    note_id = fake.add_note()
    first = client.add_paragraph(note_id, "first")
    second = client.add_paragraph(note_id, "inserted", index=0)
    paragraphs = client.get_note(note_id)["paragraphs"]
    assert [p["id"] for p in paragraphs] == [second, first]


def test_update_paragraph_text(client, fake):
    note_id = fake.add_note()
    pid = client.add_paragraph(note_id, "old")
    client.update_paragraph_text(note_id, pid, "new")
    assert client.get_paragraph(note_id, pid)["text"] == "new"


def test_delete_paragraph(client, fake):
    note_id = fake.add_note()
    pid = client.add_paragraph(note_id, "doomed")
    client.delete_paragraph(note_id, pid)
    assert client.get_note(note_id)["paragraphs"] == []


def test_run_paragraph_sync_returns_interpreter_result(client, fake):
    note_id = fake.add_note()
    pid = client.add_paragraph(note_id, "%md\nhi")
    result = client.run_paragraph_sync(note_id, pid)
    assert result["code"] == "SUCCESS"
    assert result["msg"][0]["data"] == "ran: %md\nhi"


def test_rename_note(client, fake):
    note_id = fake.add_note("before")
    client.rename_note(note_id, "after")
    assert fake.notes[note_id]["name"] == "after"


def test_clear_all_output(client, fake):
    note_id = fake.add_note()
    pid = client.add_paragraph(note_id, "x")
    client.run_paragraph_sync(note_id, pid)
    client.clear_all_output(note_id)
    para = client.get_paragraph(note_id, pid)
    assert para["results"]["msg"] == []


def test_list_interpreter_settings(client):
    settings = client.list_interpreter_settings()
    ids = {s["id"] for s in settings}
    assert {"spark", "md"} <= ids


def test_restart_interpreter_global(client, fake):
    client.restart_interpreter("spark")
    assert fake.restarted == [("spark", None)]


def test_restart_interpreter_scoped_to_note(client, fake):
    client.restart_interpreter("spark", note_id="note_42")
    assert fake.restarted == [("spark", {"noteId": "note_42"})]


# -- auth --------------------------------------------------------------------

def test_basic_auth_logs_in_once(fake):
    transport = httpx.MockTransport(fake.handler)
    http = httpx.Client(transport=transport)
    config = ZeppelinConfig(
        base_url="http://zeppelin.test",
        auth_mode=AuthMode.BASIC,
        username="admin",
        password="secret",
    )
    client = ZeppelinClient(config, http_client=http)
    client.create_note("a")
    client.create_note("b")
    logins = [r for r in fake.requests if r.url.path.endswith("/api/login")]
    assert len(logins) == 1  # login performed once and cached


def test_basic_auth_requires_credentials():
    config = ZeppelinConfig(base_url="http://zeppelin.test", auth_mode=AuthMode.BASIC)
    client = ZeppelinClient(config, http_client=httpx.Client())
    with pytest.raises(ZeppelinError, match="requires both username and password"):
        client.create_note("a")


def test_basic_auth_login_failure_raises(fake):
    fake.overrides["login"] = lambda _r: httpx.Response(401, text="bad creds")
    transport = httpx.MockTransport(fake.handler)
    http = httpx.Client(transport=transport)
    config = ZeppelinConfig(
        base_url="http://zeppelin.test",
        auth_mode=AuthMode.BASIC,
        username="admin",
        password="wrong",
    )
    client = ZeppelinClient(config, http_client=http)
    with pytest.raises(ZeppelinError) as exc:
        client.create_note("a")
    assert exc.value.status_code == 401


def test_header_auth_attaches_header(fake):
    transport = httpx.MockTransport(fake.handler)
    http = httpx.Client(transport=transport)
    config = ZeppelinConfig(
        base_url="http://zeppelin.test",
        auth_mode=AuthMode.HEADER,
        auth_header_name="Authorization",
        auth_header_value="Bearer token123",
    )
    client = ZeppelinClient(config, http_client=http)
    client.create_note("a")
    assert fake.requests[-1].headers["Authorization"] == "Bearer token123"


# -- transport / error handling ---------------------------------------------

def test_non_ok_envelope_raises():
    def handler(_request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={"status": "NOT_FOUND", "message": "nope"})

    http = httpx.Client(transport=httpx.MockTransport(handler))
    client = ZeppelinClient(ZeppelinConfig(base_url="http://t"), http_client=http)
    with pytest.raises(ZeppelinError, match="status=NOT_FOUND"):
        client.get_note("x")


def test_http_error_status_raises_with_code():
    def handler(_request: httpx.Request) -> httpx.Response:
        return httpx.Response(500, text="boom")

    http = httpx.Client(transport=httpx.MockTransport(handler))
    client = ZeppelinClient(ZeppelinConfig(base_url="http://t"), http_client=http)
    with pytest.raises(ZeppelinError) as exc:
        client.create_note("x")
    assert exc.value.status_code == 500


def test_transport_failure_wrapped():
    def handler(_request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("connection refused")

    http = httpx.Client(transport=httpx.MockTransport(handler))
    client = ZeppelinClient(ZeppelinConfig(base_url="http://t"), http_client=http)
    with pytest.raises(ZeppelinError, match="failed"):
        client.get_note("x")


def test_non_json_body_raises():
    def handler(_request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, text="<html>not json</html>")

    http = httpx.Client(transport=httpx.MockTransport(handler))
    client = ZeppelinClient(ZeppelinConfig(base_url="http://t"), http_client=http)
    with pytest.raises(ZeppelinError, match="non-JSON"):
        client.get_note("x")


def test_context_manager_closes(fake):
    transport = httpx.MockTransport(fake.handler)
    http = httpx.Client(transport=transport)
    with ZeppelinClient(ZeppelinConfig(base_url="http://t"), http_client=http) as c:
        c.create_note("a")
    assert http.is_closed

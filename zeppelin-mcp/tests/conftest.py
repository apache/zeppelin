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

"""Shared pytest fixtures: an in-memory fake Zeppelin REST server.

Tests run against a :class:`FakeZeppelin` recording handler mounted on an
``httpx.MockTransport``. This exercises the real request building, URL
construction, JSON envelope unwrapping, and error handling in
:class:`~zeppelin_mcp.client.ZeppelinClient` without a live server.
"""

from __future__ import annotations

import json
from typing import Any, Callable

import httpx
import pytest

from zeppelin_mcp.client import ZeppelinClient, ZeppelinConfig


def _ok(body: Any = "") -> httpx.Response:
    return httpx.Response(200, json={"status": "OK", "message": "", "body": body})


class FakeZeppelin:
    """A minimal in-memory Zeppelin used as an httpx mock transport handler.

    It stores notebooks keyed by id and implements just enough of the REST
    contract for the client and server tests. Each instance records every
    request it received on :attr:`requests` for assertions.
    """

    def __init__(self) -> None:
        self.notes: dict[str, dict[str, Any]] = {}
        self.interpreter_settings: list[dict[str, Any]] = [
            {"id": "spark", "name": "spark", "group": "spark"},
            {"id": "md", "name": "md", "group": "md"},
        ]
        self.requests: list[httpx.Request] = []
        self.restarted: list[tuple[str, Any]] = []
        self._seq = 0
        # Optional override hook: name -> handler, used to inject failures.
        self.overrides: dict[str, Callable[[httpx.Request], httpx.Response]] = {}

    # -- helpers ----------------------------------------------------------

    def _next_id(self, prefix: str) -> str:
        self._seq += 1
        return f"{prefix}_{self._seq}"

    def add_note(self, name: str = "test", paragraphs: list | None = None) -> str:
        note_id = self._next_id("note")
        self.notes[note_id] = {
            "id": note_id,
            "name": name,
            "paragraphs": paragraphs or [],
        }
        return note_id

    # -- transport handler ------------------------------------------------

    def handler(self, request: httpx.Request) -> httpx.Response:
        self.requests.append(request)
        path = request.url.path
        method = request.method

        # /api/login
        if path.endswith("/api/login") and method == "POST":
            return self.overrides.get("login", lambda _r: _ok())(request)

        # /api/interpreter/setting
        if path.endswith("/api/interpreter/setting") and method == "GET":
            return _ok(self.interpreter_settings)

        # /api/interpreter/setting/restart/{id}
        if "/api/interpreter/setting/restart/" in path and method == "PUT":
            setting_id = path.rsplit("/", 1)[-1]
            payload = json.loads(request.content) if request.content else None
            self.restarted.append((setting_id, payload))
            return _ok()

        # /api/notebook  (create)
        if path.endswith("/api/notebook") and method == "POST":
            payload = json.loads(request.content)
            note_id = self.add_note(payload.get("name", "untitled"))
            return _ok(note_id)

        # /api/notebook/run/{noteId}/{paragraphId}  (synchronous run)
        if "/api/notebook/run/" in path and method == "POST":
            return self._run_paragraph(path)

        # /api/notebook/{noteId}/rename
        if path.endswith("/rename") and method == "PUT":
            note_id = path.split("/api/notebook/")[1].split("/")[0]
            payload = json.loads(request.content)
            self.notes[note_id]["name"] = payload["name"]
            return _ok()

        # /api/notebook/{noteId}/clear
        if path.endswith("/clear") and method == "PUT":
            note_id = path.split("/api/notebook/")[1].split("/")[0]
            for para in self.notes[note_id]["paragraphs"]:
                para["results"] = {"code": "SUCCESS", "msg": []}
            return _ok()

        # /api/notebook/{noteId}/paragraph  (insert)
        if path.endswith("/paragraph") and method == "POST":
            return self._insert_paragraph(path, request)

        # /api/notebook/{noteId}/paragraph/{paragraphId}
        if "/paragraph/" in path:
            return self._paragraph_crud(path, method, request)

        # /api/notebook/{noteId}  (get)
        if "/api/notebook/" in path and method == "GET":
            note_id = path.rsplit("/", 1)[-1]
            note = self.notes.get(note_id)
            if note is None:
                return httpx.Response(404, text="Note not found")
            return _ok(note)

        return httpx.Response(404, text=f"unhandled {method} {path}")

    # -- paragraph operations --------------------------------------------

    def _insert_paragraph(self, path: str, request: httpx.Request) -> httpx.Response:
        note_id = path.split("/api/notebook/")[1].split("/")[0]
        payload = json.loads(request.content)
        para = {
            "id": self._next_id("paragraph"),
            "text": payload.get("text", ""),
            "status": "READY",
            "results": {"code": "SUCCESS", "msg": []},
        }
        paragraphs = self.notes[note_id]["paragraphs"]
        index = payload.get("index")
        if index is None:
            paragraphs.append(para)
        else:
            paragraphs.insert(int(index), para)
        return _ok(para["id"])

    def _paragraph_crud(self, path: str, method: str,
                        request: httpx.Request) -> httpx.Response:
        note_id = path.split("/api/notebook/")[1].split("/")[0]
        paragraph_id = path.rsplit("/", 1)[-1]
        note = self.notes.get(note_id, {"paragraphs": []})
        para = next(
            (p for p in note["paragraphs"] if p["id"] == paragraph_id), None
        )

        if method == "GET":
            if para is None:
                return httpx.Response(404, text="Paragraph not found")
            return _ok(para)
        if method == "PUT":
            payload = json.loads(request.content)
            if para is not None and "text" in payload:
                para["text"] = payload["text"]
            return _ok()
        if method == "DELETE":
            note["paragraphs"] = [
                p for p in note["paragraphs"] if p["id"] != paragraph_id
            ]
            return _ok()
        return httpx.Response(404, text=f"unhandled {method} {path}")

    def _run_paragraph(self, path: str) -> httpx.Response:
        # path: /api/notebook/run/{noteId}/{paragraphId}
        tail = path.split("/api/notebook/run/")[1]
        note_id, paragraph_id = tail.split("/")
        note = self.notes.get(note_id, {"paragraphs": []})
        para = next(
            (p for p in note["paragraphs"] if p["id"] == paragraph_id), None
        )
        if para is None:
            return httpx.Response(404, text="Paragraph not found")
        para["status"] = "FINISHED"
        # Echo a deterministic table result so run output can be asserted.
        result = {"code": "SUCCESS", "msg": [{"type": "TEXT", "data": "ran: " + para["text"]}]}
        para["results"] = result
        return _ok(result)


@pytest.fixture
def fake() -> FakeZeppelin:
    return FakeZeppelin()


@pytest.fixture
def client(fake: FakeZeppelin) -> ZeppelinClient:
    transport = httpx.MockTransport(fake.handler)
    http = httpx.Client(transport=transport, base_url="http://zeppelin.test")
    config = ZeppelinConfig(base_url="http://zeppelin.test")
    return ZeppelinClient(config, http_client=http)

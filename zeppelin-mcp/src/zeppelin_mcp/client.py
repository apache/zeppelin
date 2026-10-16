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

"""Thin synchronous client for the Apache Zeppelin REST API.

This module wraps the subset of the Zeppelin REST API that the MCP server
needs and is intentionally free of any MCP concepts so it can be unit tested
in isolation. All responses from Zeppelin share the envelope::

    {"status": "OK", "message": "", "body": <payload>}

``body`` is the only interesting field; :meth:`ZeppelinClient._body` unwraps it
and raises :class:`ZeppelinError` on any non-OK status or transport failure.
"""

from __future__ import annotations

import dataclasses
import enum
from typing import Any, Optional

import httpx

# Default time budget for a single HTTP request. Synchronous paragraph runs can
# take a while (a Spark job, a slow SQL query), so this is deliberately generous
# and overridable from the environment by the server.
DEFAULT_TIMEOUT_SECONDS = 600.0


class ZeppelinError(RuntimeError):
    """Raised when Zeppelin returns a non-OK status or a request fails.

    The original HTTP status code (when available) is preserved on
    :attr:`status_code` so callers can distinguish auth failures (401/403)
    from missing notebooks (404) without parsing the message string.
    """

    def __init__(self, message: str, status_code: Optional[int] = None):
        super().__init__(message)
        self.status_code = status_code


class AuthMode(str, enum.Enum):
    """Supported authentication strategies.

    NONE
        Anonymous Zeppelin (no ``shiro.ini`` realm configured). The default.
    BASIC
        Shiro form login via ``POST /api/login``; the resulting session cookie
        is reused for subsequent calls.
    HEADER
        A pre-supplied header (e.g. ``Authorization: Bearer ...`` or a session
        cookie) is attached verbatim. Covers reverse-proxied / SSO deployments
        without baking any specific scheme into this client.
    """

    NONE = "none"
    BASIC = "basic"
    HEADER = "header"


@dataclasses.dataclass
class ZeppelinConfig:
    """Connection settings for a Zeppelin server."""

    base_url: str
    auth_mode: AuthMode = AuthMode.NONE
    username: Optional[str] = None
    password: Optional[str] = None
    # For AuthMode.HEADER, the literal header name/value to attach to requests.
    auth_header_name: str = "Authorization"
    auth_header_value: Optional[str] = None
    timeout_seconds: float = DEFAULT_TIMEOUT_SECONDS
    # When False, TLS certificate verification is disabled (self-signed dev
    # servers). Mirrors httpx's ``verify`` argument.
    verify_tls: bool = True

    def api_root(self) -> str:
        """Return the ``/api`` root with any trailing slash on base_url removed."""
        return f"{self.base_url.rstrip('/')}/api"


class ZeppelinClient:
    """Synchronous wrapper around the Zeppelin REST API.

    The client owns a single :class:`httpx.Client` (connection pooling, cookie
    persistence) and is safe to reuse across many calls. It is *not* designed
    for concurrent use from multiple threads.
    """

    def __init__(self, config: ZeppelinConfig, http_client: Optional[httpx.Client] = None):
        self._config = config
        # Allow injection of a pre-built client (used by tests to mount a mock
        # transport); otherwise construct one from the config.
        self._http = http_client or httpx.Client(
            timeout=config.timeout_seconds,
            verify=config.verify_tls,
            follow_redirects=True,
        )
        self._logged_in = False

    # -- lifecycle ---------------------------------------------------------

    def close(self) -> None:
        self._http.close()

    def __enter__(self) -> "ZeppelinClient":
        return self

    def __exit__(self, *_exc: object) -> None:
        self.close()

    # -- auth --------------------------------------------------------------

    def _ensure_auth(self) -> None:
        """Perform a one-time Shiro login for BASIC auth.

        NONE and HEADER modes need no handshake. For BASIC we POST the
        credentials once; httpx persists the returned ``JSESSIONID`` cookie on
        the shared client for the lifetime of the process.
        """
        if self._config.auth_mode != AuthMode.BASIC or self._logged_in:
            return
        if not self._config.username or not self._config.password:
            raise ZeppelinError("BASIC auth requires both username and password")
        resp = self._http.post(
            f"{self._config.api_root()}/login",
            data={"userName": self._config.username, "password": self._config.password},
        )
        if resp.status_code != httpx.codes.OK:
            raise ZeppelinError(
                f"Login failed for user '{self._config.username}' "
                f"(HTTP {resp.status_code})",
                status_code=resp.status_code,
            )
        self._logged_in = True

    def _headers(self) -> dict[str, str]:
        if self._config.auth_mode == AuthMode.HEADER and self._config.auth_header_value:
            return {self._config.auth_header_name: self._config.auth_header_value}
        return {}

    # -- transport ---------------------------------------------------------

    def _request(self, method: str, path: str, *, json: Any = None,
                 params: Optional[dict[str, Any]] = None) -> Any:
        """Issue a request to ``/api`` + ``path`` and return the ``body`` field.

        Raises :class:`ZeppelinError` on transport errors, non-2xx responses,
        or a JSON envelope whose ``status`` is not ``OK``.
        """
        self._ensure_auth()
        url = f"{self._config.api_root()}{path}"
        try:
            resp = self._http.request(
                method, url, json=json, params=params, headers=self._headers()
            )
        except httpx.HTTPError as exc:  # connection refused, DNS, timeout, ...
            raise ZeppelinError(f"Request to {url} failed: {exc}") from exc

        if resp.status_code >= 400:
            raise ZeppelinError(
                f"{method} {path} returned HTTP {resp.status_code}: "
                f"{_truncate(resp.text)}",
                status_code=resp.status_code,
            )

        # Some endpoints (e.g. login) return an empty body; treat as no payload.
        if not resp.content:
            return None
        try:
            envelope = resp.json()
        except ValueError as exc:
            raise ZeppelinError(
                f"{method} {path} returned non-JSON body: {_truncate(resp.text)}"
            ) from exc

        status = envelope.get("status")
        if status and status != "OK":
            raise ZeppelinError(
                f"{method} {path} returned status={status}: "
                f"{envelope.get('message', '')}",
                status_code=resp.status_code,
            )
        return envelope.get("body")

    # -- notebook operations ----------------------------------------------

    def create_note(self, note_path: str,
                    default_interpreter_group: Optional[str] = None) -> str:
        """Create a notebook and return its new note id.

        ``note_path`` is the full path (Zeppelin nests notes by ``/``); the
        REST API calls this field ``name`` in older docs but the server reads
        ``NewNoteRequest.notePath``.
        """
        payload: dict[str, Any] = {"name": note_path}
        if default_interpreter_group:
            payload["defaultInterpreterGroup"] = default_interpreter_group
        body = self._request("POST", "/notebook", json=payload)
        if not body:
            raise ZeppelinError("Note creation returned no note id")
        return str(body)

    def get_note(self, note_id: str) -> dict[str, Any]:
        """Return the full note document (includes ``paragraphs``)."""
        body = self._request("GET", f"/notebook/{note_id}")
        if not isinstance(body, dict):
            raise ZeppelinError(f"Note {note_id} not found")
        return body

    def rename_note(self, note_id: str, new_name: str) -> None:
        self._request("PUT", f"/notebook/{note_id}/rename", json={"name": new_name})

    def add_paragraph(self, note_id: str, text: str,
                      index: Optional[int] = None) -> str:
        """Append (or insert at ``index``) a paragraph; return its id."""
        payload: dict[str, Any] = {"text": text}
        if index is not None:
            payload["index"] = index
        body = self._request("POST", f"/notebook/{note_id}/paragraph", json=payload)
        if not body:
            raise ZeppelinError("Paragraph creation returned no paragraph id")
        return str(body)

    def update_paragraph_text(self, note_id: str, paragraph_id: str, text: str) -> None:
        self._request(
            "PUT", f"/notebook/{note_id}/paragraph/{paragraph_id}", json={"text": text}
        )

    def get_paragraph(self, note_id: str, paragraph_id: str) -> dict[str, Any]:
        body = self._request("GET", f"/notebook/{note_id}/paragraph/{paragraph_id}")
        if not isinstance(body, dict):
            raise ZeppelinError(f"Paragraph {paragraph_id} not found")
        return body

    def delete_paragraph(self, note_id: str, paragraph_id: str) -> None:
        self._request("DELETE", f"/notebook/{note_id}/paragraph/{paragraph_id}")

    def clear_all_output(self, note_id: str) -> None:
        self._request("PUT", f"/notebook/{note_id}/clear")

    def run_paragraph_sync(self, note_id: str, paragraph_id: str) -> dict[str, Any]:
        """Run a paragraph and block until it finishes.

        Uses Zeppelin's synchronous endpoint ``POST /api/notebook/run/{}/{}``,
        whose ``body`` is the ``InterpreterResult`` (``{"code", "msg": [...]}``).
        This avoids the submit-then-poll loop the async ``job`` endpoint needs.
        """
        body = self._request("POST", f"/notebook/run/{note_id}/{paragraph_id}")
        # The sync endpoint returns the InterpreterResult directly. Normalize a
        # null body (paragraph produced no output) to an empty result.
        return body if isinstance(body, dict) else {"code": "SUCCESS", "msg": []}

    # -- interpreter operations -------------------------------------------

    def list_interpreter_settings(self) -> list[dict[str, Any]]:
        body = self._request("GET", "/interpreter/setting")
        return body if isinstance(body, list) else []

    def restart_interpreter(self, setting_id: str,
                            note_id: Optional[str] = None) -> None:
        """Restart an interpreter setting, optionally scoped to one note."""
        payload = {"noteId": note_id} if note_id else None
        self._request("PUT", f"/interpreter/setting/restart/{setting_id}", json=payload)


def _truncate(text: str, limit: int = 500) -> str:
    """Clamp server error bodies so exception messages stay readable."""
    text = text.strip()
    return text if len(text) <= limit else text[:limit] + "..."

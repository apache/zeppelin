<!--
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to you under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Apache Zeppelin — Security Threat Model

## §1 Header

- **Project:** Apache Zeppelin
- **Modeled against:** `master` HEAD as of 2026-06-05 (latest released docs line).
- **Authors:** ASF Security team (v0 draft, generated via the
  `threat-model-producer` rubric), for the Apache Zeppelin PMC to review.
- **Status:** **v0 — PMC-reviewed.** The Apache Zeppelin PMC has reviewed the
  framing (confirmed) and answered the §14 questions; the answers are recorded
  inline in §14 and folded into the sections below, with confirmed claims
  re-tagged *(maintainer)*.
- **Version binding:** this model is versioned with the project. A report
  against Zeppelin release *N* is triaged against the model as it stood at *N*.
- **Reporting cross-reference:** findings that violate a §8 property should be
  reported privately per the project's disclosure channel
  (`security@apache.org`); findings that fall under §3 or §9 are closed citing
  this document.
- **Provenance legend:** *(documented)* = stated in Zeppelin's own docs/site;
  *(maintainer)* = confirmed by a Zeppelin PMC member; *(inferred)* = reasoned
  from code/docs/domain knowledge, not yet confirmed (each has a §14 question).
- **Confidence:** ~18 documented; the §14 answers fold the bulk of the former
  *(inferred)* claims to *(maintainer)*; a few loose environmental assumptions
  remain *(inferred)*.

**What Zeppelin is.** Apache Zeppelin is a web-based, multi-user notebook
server for interactive data analytics. Users open notebooks in a browser and
run "paragraphs" of code against pluggable *interpreters* (Spark, Flink,
Python, JDBC, shell, etc.); the Zeppelin server launches and talks to
interpreter processes over a Thrift IPC channel and returns results to the
browser over a websocket. **Executing user-supplied code on the server is the
product's primary function, not a vulnerability** — the security model is
about *who is allowed to reach that capability and with what isolation*, not
about preventing code execution.

## §2 Scope and intended use

- **Primary intended use** *(inferred)*: an operator-deployed, multi-tenant
  analytics notebook server, run inside an organization's trusted network and
  fronted by authentication, where authorized analysts author and run
  notebooks against backend compute (Spark/Flink/etc.).
- **Deployment shape** *(documented)*: a long-running JVM server
  (`zeppelin-server`) plus one or more interpreter processes; reached via HTTP
  + websocket, optionally behind a reverse proxy (NGINX).
- **Caller roles** (a network service, so the role splits):
  - **client / notebook user** — untrusted until authenticated; once
    authenticated, trusted only up to their notebook/role permissions.
  - **operator / admin** — trusted for the instance; owns `shiro.ini`,
    interpreter settings, `zeppelin-site.xml`, the host.
  - **anonymous visitor** — present **by default** (see §5a); trusted at
    whatever level the deployment's authorization grants anonymous, which by
    default is full access.

**Component-family table** *(maintainer — §14.8)*:

| Family | Entry point | Touches outside process? | In model? |
| --- | --- | --- | --- |
| Web/REST/websocket server | `org.apache.zeppelin.rest.*`, websocket | network | **yes** |
| AuthN/AuthZ (Shiro + notebook ACL + URL ACL) | `shiro.ini`, `NotebookAuthorization`, `SecurityRestApi` | filesystem (config) | **yes** |
| Interpreter launch + IPC | Thrift `RemoteInterpreterServer`, process launcher | child processes, network | **yes** (the launch/isolation boundary) |
| Interpreter-executed user code | `%spark`, `%sh`, `%python`, `%jdbc`, … | arbitrary (by design) | **boundary only** — the *code* is by-design; reaching/isolating it is in model |
| Credentials / datasource auth | `CredentialRestApi`, credential injection | filesystem, backends | **yes** |
| Notebook storage / repos | `NotebookRepo` (local FS, S3, Git, etc.) | filesystem / cloud | **yes** |
| Bundled interpreters / examples / web UI assets | `*-interpreter` modules, demos | varies | **all bundled interpreters first-class** for security purposes (§14.8); demo/example notebooks are a separate category |

## §3 Out of scope (explicit non-goals)

- **Sandboxing the code a permitted user runs.** A user with run permission on
  a notebook can execute arbitrary code (`%sh`, Spark driver code, etc.) by
  design; Zeppelin does not attempt to confine what that code does on the host
  or backend. *(maintainer — §14.3)*
- **Defending a deployment that disables authentication and is exposed to an
  untrusted network.** The docs direct operators to enable Shiro *or* deploy
  only in a secured/trusted environment *(documented)*; an unauthenticated,
  internet-exposed instance is an operator misconfiguration, not a Zeppelin
  defect (pending the §5a/§14 ruling on whether anonymous is a supported
  posture).
- **Security of third-party interpreter backends** (the Spark cluster, the
  JDBC database, the host shell) — Zeppelin brokers access; it does not own
  those systems' security. *(inferred)*
- **Bundled demo / example notebooks** — a separate category from the
  interpreters, threat-modeled separately if at all; integrators should not
  extend core guarantees to them. (All bundled *interpreters* are first-class;
  see §2 and §14.8.) *(maintainer — §14.8)*

## §4 Trust boundaries and data flow

The **primary trust boundary is the authentication + authorization layer**
(Shiro realm → notebook ACL → URL ACL), not the API surface itself. Data flow
and the trust transitions it crosses:

1. Browser → **HTTP/websocket** → server: crosses the network boundary.
   Untrusted until Shiro authenticates the session. *(documented)*
2. Authenticated session → **notebook operation** (read/write/run): crosses
   the notebook-ACL boundary (owner/reader/writer/runner). *(documented)*
3. Run request → **interpreter process** over Thrift IPC: the server hands
   user code to an interpreter. Whether this crosses an OS-user boundary
   depends on **impersonation** (off by default → runs as the *server* OS
   user). *(documented)*
4. Interpreter → backend (Spark/JDBC/FS/shell): leaves Zeppelin's boundary
   entirely. *(inferred)*

**Reachability preconditions per family** (the triager's first test):
- A finding in the web/REST/websocket family is in-model only if reachable by
  a network client **before** the Shiro auth gate, or by an authenticated user
  **beyond** their granted role/notebook permission.
- A finding in interpreter-launch/IPC is in-model only if it lets a user cross
  a boundary the model claims (e.g., one tenant reaching another tenant's
  interpreter/credentials, or escaping the impersonation user when impersonation
  is on).
- A finding that is "authenticated run-capable user executes code / reads files
  as the interpreter's OS user" is **out of model** (that is the granted
  capability) unless it crosses into another tenant or the operator boundary.

## §5 Assumptions about the environment

- **Operator-controlled host and config** *(inferred)*: `shiro.ini`,
  `zeppelin-site.xml`, credential stores, and the interpreter settings are
  trusted inputs written by the operator, not attacker-controllable.
- **Network placement** *(documented)*: Zeppelin expects to sit in a secured/
  trusted network or behind an authenticating proxy; HTTPS is "highly
  recommended" for the web/websocket channel.
- **Backend trust** *(inferred)*: the Spark/Flink/JDBC/SSH backends an
  interpreter reaches are provisioned by the operator.
- **What the server does to its host** *(inferred — §14)*: launches child
  interpreter processes; reads config + notebook storage; opens listening
  sockets (HTTP/websocket, Thrift); with impersonation, performs SSH/`setuid`-
  style user switching. It is *not* expected to run as root.

## §5a Build-time and configuration variants (the security-envelope knobs)

This is the heart of Zeppelin's model — several **defaults are the less-secure
value**, so the model is ambiguous until the PMC rules on each (see §14 wave 1):

| Knob | Default | Effect on model | Maintainer stance |
| --- | --- | --- | --- |
| Shiro authentication (`conf/shiro.ini`) | **absent → anonymous** *(documented)* | No auth boundary at all; every §8 authn/authz property is void | **dev-convenience** *(maintainer — §14.1)*: anonymous is *not* the supported posture; reports against an exposed anonymous instance are `OUT-OF-MODEL: non-default-build` |
| `zeppelin.notebook.public` / `ZEPPELIN_NOTEBOOK_PUBLIC` | **`true` → new notes public** *(documented)* | Empty-ACL note is readable/runnable by any authenticated (or anonymous) user | **by-design** *(maintainer — §14.2)*: public-by-default is intended; an empty-ACL note being readable/runnable is not a bug |
| Interpreter user impersonation | **off → runs as server OS user** *(documented)* | Without it, every run-capable user's code shares the *server's* OS identity/privileges and filesystem | **by-design** *(maintainer — §14.3)*: running as the server OS user is the documented default; OS isolation requires enabling impersonation |
| Interpreter binding mode (shared / scoped / isolated) | **shared** *(maintainer — §14.4)* | Process-level separation between users/notes; "isolated" is a *stability/resource* boundary, **not** a security sandbox | **maintainer — §14.4**: default is `shared`; no binding mode is a security sandbox |
| URL ACLs (`[urls]` in shiro.ini) gating `/interpreter`, `/credential`, `/configurations` | **not restricted unless operator adds them** *(documented)* | Sensitive admin endpoints open to any authenticated role absent explicit `[urls]` rules | **maintainer — §14.5**: no built-in admin gate; protection relies entirely on `shiro.ini [urls]` |
| HTTPS / security headers (`http_security_headers`) | **off/plain unless configured** *(documented)* | Credentials + session over plaintext; missing CSP/XFO | operator responsibility (§10); no CSP and Origin-based CSRF only — `VALID-HARDENING` *(maintainer — §14.10)* |

**Insecure-default ruling (recorded).** The PMC has ruled that every insecure
§5a default above is a *dev-convenience / by-design* choice, not the supported
production posture: Zeppelin's stance is "open by default, secure by
configuration" (enable Shiro, or deploy only in a secured/trusted network). A
report that only manifests under one of these defaults is therefore
`OUT-OF-MODEL: non-default-build` (or `BY-DESIGN`), with the requirement living
in §10. See §14 wave 1 for the per-knob answers.

## §6 Assumptions about inputs

Inputs and their trust (network-service shape — rows are endpoints/messages):

| Surface | Input | Attacker-controllable? | Caller/operator must enforce |
| --- | --- | --- | --- |
| `POST` login / Shiro filter | credentials | **yes** (pre-auth) | strong realm config; lockout/rate-limit at proxy *(inferred)* |
| Websocket ops (run/edit/move paragraph) | notebook + paragraph payload | **yes** (authenticated user) | notebook ACL + run permission enforced server-side *(documented)* |
| `NotebookRestApi` / `InterpreterRestApi` | note id, interpreter settings | **yes** (authenticated user) | URL ACL + ownership checks, enforced server-side *(maintainer — §14.6)* |
| `CredentialRestApi` | per-user credentials | **yes** (authenticated user) | per-user credential isolation *(maintainer — §14.7)* |
| Paragraph code body | arbitrary code | **yes — by design** | this is the granted capability, not validated input |
| `shiro.ini`, `zeppelin-site.xml`, interpreter JSON | config | **no — operator-trusted** | filesystem perms on config/secret files *(inferred)* |
| Notebook storage backend contents | persisted notes | **mostly trusted** (written via the app) | integrity of the repo (S3/Git/FS) *(inferred)* |

Size/shape/rate: *(maintainer — §14.9)* there is no rate limit and no
concurrent-interpreter-launch cap today; the PMC treats this as
`VALID-HARDENING` and welcomes the scan surfacing concrete limits.

## §7 Adversary model

**In scope:**
- **Unauthenticated network client** (when Shiro is enabled): tries to reach
  any authenticated capability without valid credentials — bypass the login
  filter, forge/steal a session, reach a websocket/REST op pre-auth.
- **Authenticated lower-privileged user**: a legitimate user trying to exceed
  their grant — read/edit/run another user's note they lack ACL for, read
  another user's credentials, reach admin endpoints (`/interpreter`,
  `/credential`) their role shouldn't, or (impersonation on) break out of their
  impersonation identity into another user's or the server's.

**Capabilities:** can send arbitrary HTTP/websocket traffic; can author
arbitrary code in paragraphs they may run; cannot (assumed) read the operator's
config/secret files or the host outside what their interpreter identity grants.

**Explicitly out of scope:**
- The **operator/admin** and anyone with write access to `shiro.ini` /
  interpreter config / the host — they have already won.
- A user **executing code within their own granted run permission** — that is
  the product working as designed, even though the code can be `%sh rm -rf`.
- Anyone reaching a **default anonymous** instance over an untrusted network —
  out of model pending the §5a/§14 ruling.

## §8 Security properties the project provides

Each conditional on the relevant §5a knob being set securely. The PMC has
confirmed properties 1–5 below as committed properties (§14.5–§14.7), now
tagged *(maintainer)*; property 6 (resource/availability) is **not** a committed
property today (§14.9).

1. **Authentication of the web/REST/websocket surface** *when Shiro is
   configured*. Violation symptom: an unauthenticated client performs an
   operation requiring a session. Severity: **critical**. *(maintainer —
   §14.6)*
2. **Authorization of notebook operations per the owner/reader/writer/runner
   ACL** *when auth is on*. Violation symptom: a user reads/edits/runs a note
   they lack permission for. Severity: **critical**. *(maintainer — §14.6:
   enforced server-side for every websocket/REST op, not client-side only)*
3. **URL-level access control** for sensitive endpoints via `[urls]`.
   Violation symptom: a non-admin reaches `/interpreter`, `/credential`, or
   `/configurations` despite a restricting rule. Severity: **high**.
   *(documented mechanism; maintainer — §14.5: no built-in admin gate, so this
   property holds only when the operator adds `[urls]` rules)*
4. **Per-user credential isolation** (one user cannot read another's injected
   datasource credentials). Violation symptom: cross-user credential read.
   Severity: **critical**. *(maintainer — §14.7)*
5. **Impersonation confinement** *when enabled*: interpreter code runs as the
   logged-in user, not the server user, and not as another user. Violation
   symptom: code runs as a different identity than the session's. Severity:
   **high**. *(maintainer — §14.3)*
6. **Resource/availability** — **not a committed property today** *(maintainer —
   §14.9)*. There is no rate limit or concurrent-launch cap. The PMC treats
   hardening here as `VALID-HARDENING` and welcomes concrete recommendations
   from the scan rather than suppressing them.

## §9 Security properties the project does *not* provide

- **No sandbox of permitted code.** A run-capable user's paragraph executes
  with the full authority of the interpreter's OS identity (the *server* user
  unless impersonation is on). "Zeppelin runs my shell command" is not a
  vulnerability. *(inferred — §14.3)*
- **No protection in anonymous/default mode.** With no `shiro.ini`, there is no
  authn/authz boundary; everything in §8 is void. *(documented)*
- **No transport security by default.** Plaintext HTTP/websocket unless the
  operator configures TLS; credentials transit in the clear otherwise.
  *(documented)*

**False-friend properties (call out explicitly):**
- **Interpreter "isolated" binding mode is not a security sandbox.** It gives
  each user/note a separate interpreter *process* for stability and resource
  separation; it does **not** confine what the code in that process can do to
  the host or to shared backends, and absent impersonation all those processes
  still run as the **same server OS user**. *(maintainer — §14.4)*
- **Notebook permissions are an application-layer ACL, not OS isolation.** A
  user denied *read* on a note in the UI may still reach data through an
  interpreter they *can* run if backends aren't separately access-controlled.
  *(maintainer)*

**Well-known attack classes left to the operator/integrator:** SSRF from
interpreter code reaching internal services; secrets-in-notebooks; XSS/CSRF on
the notebook web UI — *(maintainer — §14.10)* there is **no Content-Security-
Policy** and CSRF protection is **Origin-header-based only**, so strengthening
these (CSP, stronger CSRF) is welcome `VALID-HARDENING`; websocket cross-origin.
The point is to put integrators on notice.

## §10 Downstream / operator responsibilities

For Zeppelin the "user" is the **operator** deploying it:
- **Enable Shiro authentication** (or keep Zeppelin strictly inside a trusted,
  network-isolated perimeter). *(documented)*
- **Add `[urls]` rules** restricting `/interpreter`, `/credential`,
  `/configurations` (and other admin paths) to admin roles. *(documented)*
- **Set `zeppelin.notebook.public=false`** if notebooks should default to
  private. *(documented)*
- **Enable interpreter impersonation** for genuine multi-tenant isolation; do
  not rely on binding mode alone. *(documented)*
- **Terminate TLS** and enable `http_security_headers`. *(documented)*
- **Protect `shiro.ini`, credential stores, and notebook storage** with host
  filesystem permissions; do not run the server as root. *(inferred)*
- **Treat backends (Spark/JDBC/SSH) as reachable by any run-capable user** and
  access-control them independently. *(inferred)*

## §11 Known misuse patterns

- Exposing a **default (anonymous) Zeppelin to the public internet** — turns
  the by-design code-execution surface into unauthenticated RCE-equivalent.
- Relying on **notebook ACLs while interpreters run shared/as-server-user**, so
  a run-capable user reaches data the ACL meant to hide.
- Treating **"isolated" binding as a security boundary** between tenants.
- Storing **long-lived secrets in notebook source** instead of the credential
  store, then sharing the note.
- Leaving **`/interpreter` and `/credential` reachable** by all authenticated
  users (no `[urls]` rules).

## §11a Known non-findings (recurring false positives)

The highest-leverage section for keeping scan output signal-heavy:

- **"`%sh` / interpreter executes arbitrary shell or driver code → RCE."**
  By design for a run-capable user; `OUT-OF-MODEL` / `BY-DESIGN` unless it
  crosses a tenant or the operator boundary. (§3, §9) *(maintainer — §14.3)*
- **"Interpreter process runs as the Zeppelin server OS user / can read server
  files."** Documented default behavior without impersonation; operator config,
  not a defect. (§5a, §10) *(documented; maintainer — §14.3)*
- **"Anonymous user can do X"** reported against a deployment with **no
  `shiro.ini`.** Out of model — auth is operator-enabled. (§5a, §9)
  *(maintainer — §14.1)*
- **"No TLS / credentials in plaintext"** against a deployment the operator did
  not configure for HTTPS. Operator responsibility. (§10) *(documented)*
- **Static-analysis "command injection / code execution" hits on the
  interpreter execution path.** That path *is* the feature; in-model only if it
  bypasses the authn/authz gate. (§4 reachability test) *(maintainer)*

## §12 Conditions that would change this model

- A new network surface or REST/websocket endpoint; a new first-class
  interpreter; a change of any §5a default (e.g., shipping `shiro.ini` enabled,
  or `notebook.public=false` by default); adding a built-in sandbox for
  interpreter code; a new notebook-storage backend with different trust.
- **A report that cannot be routed to one §13 disposition** is itself evidence
  the model is incomplete — revise §8/§9 rather than make an ad-hoc call.

## §13 Triage dispositions

| Disposition | Meaning | Licensed by |
| --- | --- | --- |
| `VALID` | Bypasses the authn/authz gate, or lets a user exceed their notebook/role grant, or crosses a tenant/credential/impersonation boundary, with auth configured. | §8, §6, §7 |
| `VALID-HARDENING` | No §8 property broken, but the API makes a §11 misuse too easy; hardened at maintainer discretion. | §11 |
| `OUT-OF-MODEL: trusted-input` | Requires control of operator config (`shiro.ini`, interpreter JSON, host). | §6 |
| `OUT-OF-MODEL: adversary-not-in-scope` | Requires operator/admin privilege, or is a run-capable user executing code within their grant. | §7 |
| `OUT-OF-MODEL: non-default-build` | Only manifests under an insecure §5a default the PMC rules dev-only (e.g., anonymous mode, public notebooks, no impersonation). | §5a |
| `BY-DESIGN: property-disclaimed` | Concerns interpreter code execution or a property §9 disclaims. | §9 |
| `KNOWN-NON-FINDING` | Matches a §11a pattern. | §11a |
| `MODEL-GAP` | Cannot be routed above → revise the model. | §12 |

## §14 Open questions for the maintainers

Grouped in waves; each states the **proposed answer** followed by the **PMC
answer** (recorded by the Apache Zeppelin PMC, 2026-06-11). The core framing —
RBAC (Shiro + notebook ACL + URL ACL + impersonation) is the trust boundary,
not a sandbox, and a `%sh` from a run-capable user is the product working, not
RCE — was confirmed by the PMC and is kept.

**Wave 1 — scope & the insecure defaults (these reshape everything):**
1. **Anonymous default.** Proposed: anonymous/no-`shiro.ini` is a *dev-
   convenience*; the supported production posture requires Shiro **or** a
   trusted isolated network. So reports against an internet-exposed anonymous
   instance are `OUT-OF-MODEL: non-default-build`. Correct? (→ §5a, §3, §11a)
   **→ PMC: confirmed.**
2. **`notebook.public=true` default.** Proposed: public-by-default is intended
   convenience; operators needing isolation set it false. A "any user can read
   an empty-ACL note" report is by-design, not a bug. Correct? (→ §5a, §2)
   **→ PMC: confirmed — by-design.**
3. **Impersonation off by default.** Proposed: without impersonation, all
   interpreter code legitimately runs as the **server** OS user; this is the
   documented default and not a vulnerability; multi-tenant OS isolation
   requires enabling impersonation. Correct? (→ §3, §5a, §9, §11a)
   **→ PMC: confirmed.**
4. **Binding mode as boundary.** Proposed: shared/scoped/isolated are
   stability/resource controls, **not** security sandboxes; we should state
   that explicitly in §9. Agree? Which is the default? (→ §5a, §9)
   **→ PMC: agreed — the default is `shared`, and no binding mode is a security
   sandbox.**

**Wave 2 — properties & enforcement:**
5. **URL ACL default.** Are `/interpreter`, `/credential`, `/configurations`
   open to any authenticated role unless `[urls]` restricts them, or is there a
   built-in admin gate? (→ §5a, §8)
   **→ PMC: no built-in admin gate — it relies on `shiro.ini [urls]`.**
6. **Server-side ACL enforcement.** Are notebook ACLs + role checks enforced on
   the **server** for every websocket/REST op (not just hidden in the UI)? Any
   ops that check only client-side? (→ §6, §8)
   **→ PMC: enforced server-side; there are no client-side-only checks.**
7. **Credential isolation.** Does the credential store guarantee one user
   cannot read another user's injected credentials, including via a shared
   interpreter process? (→ §8, §9)
   **→ PMC: yes — per-user credentials are isolated.**

**Wave 3 — surfaces & limits:**
8. **First-class interpreters.** Which interpreters/modules are supported for
   security purposes vs. community/unsupported (→ §2/§3 carve-out)?
   **→ PMC: all bundled interpreters are supported for security purposes
   (first-class).**
9. **Resource limits.** Any limits on paragraph/result size, websocket rate, or
   concurrent interpreter launches? Where's the line between in-model pre-auth
   exhaustion and by-design expensive queries? (→ §6, §8)
   **→ PMC: `VALID-HARDENING` — please surface. No rate limit or
   concurrent-launch cap today; this is an area we would like the scan to flag
   and recommend improvements for.**
10. **Web-UI hardening.** Does enabling `http_security_headers` give CSRF + XSS
    + clickjacking coverage, or are those partly the operator's job? (→ §9)
    **→ PMC: `VALID-HARDENING` — please surface. No CSP, and CSRF is
    Origin-based only; concrete improvements from the scan are welcome.**
11. **Coexistence.** This is a new `THREAT_MODEL.md`; `SECURITY.md` (currently a
    stub) should point at it as canonical, and the website security pages stay
    the operator how-to. Agree? (→ meta)
    **→ PMC: agreed.**

## §15 Machine-readable companion

Deferred for v0; a `threat-model.yaml` sidecar (entry points → trust, §5a
defaults, §8 properties, §11a suppressions, §13 labels) can be generated once
the prose is ratified.

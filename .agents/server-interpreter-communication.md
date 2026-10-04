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

# Server–Interpreter Communication

> Detailed runtime reference for coding agents. The repository-root
> [`AGENTS.md`](../AGENTS.md) contains the instructions that always apply.

Zeppelin's most important architectural concept: the server and each interpreter run in **separate JVM processes** communicating via **Apache Thrift RPC**. This provides isolation, fault tolerance, and the ability to run interpreters on remote hosts or containers.

### Thrift Code Generation

The `.thrift` files are in `zeppelin-interpreter/src/main/thrift/`. Generated Java files are **checked into git** (not generated at build time) in `zeppelin-interpreter/src/main/java/org/apache/zeppelin/interpreter/thrift/`.

To regenerate after modifying `.thrift` files:
```bash
cd zeppelin-interpreter/src/main/thrift
./genthrift.sh   # requires 'thrift' compiler (v0.13.0) installed locally
```

The script runs the Thrift compiler, prepends ASF license headers, and moves files to the source tree. **Never edit the generated Java files directly** — changes will be lost on next regeneration.

### Thrift IPC — Bidirectional

**Server → Interpreter** (`RemoteInterpreterService`):
```
init(properties)                    — initialize interpreter process with config
createInterpreter(className, ...)   — instantiate an interpreter class
open(sessionId, className)          — open/initialize an interpreter
interpret(sessionId, className, code, context) — execute code (core method)
cancel(sessionId, className, ...)   — cancel running execution
getProgress(sessionId, className)   — poll execution progress (0-100)
completion(sessionId, className, buf, cursor) — code completion
close(sessionId, className)         — close an interpreter
shutdown()                          — terminate the interpreter process
```

**Interpreter → Server** (`RemoteInterpreterEventService`):
```
registerInterpreterProcess(info)    — register after process startup
appendOutput(event)                 — stream execution output incrementally
updateOutput(event)                 — replace output content
sendParagraphInfo(info)             — update paragraph metadata
updateAppStatus(event)              — Zeppelin Application status
runParagraphs(request)              — trigger paragraph execution from interpreter
getResource(resourceId)             — access ResourcePool shared state
getParagraphList(noteId)            — query notebook structure
```

### Paragraph Execution Chain

When a user runs a paragraph, the full call chain is:

```
User clicks "Run" in browser
  → WebSocket message to NotebookServer
    → NotebookServer.runParagraph()
      → Notebook.run()
        → Paragraph.execute()
          → RemoteInterpreter.interpret(code, context)
            → RemoteInterpreterProcess.callRemoteFunction()
              → [Thrift RPC over TCP]
                → RemoteInterpreterServer.interpret()
                  → actual Interpreter.interpret()  (e.g. SparkInterpreter)
                    → result returned via Thrift
          → meanwhile: interpreter calls appendOutput() to stream partial results back
```

### Interpreter Launch Chain

When an interpreter process needs to be started:

```
RemoteInterpreter.interpret()  [first call triggers launch]
  → ManagedInterpreterGroup.getOrCreateInterpreterProcess()
    → InterpreterSetting.createInterpreterProcess()
      → InterpreterSetting.createLauncher(properties)
        → PluginManager.loadInterpreterLauncher(launcherPlugin)
          → [builtin: Class.forName() / external: URLClassLoader]
            → InterpreterLauncher.launch(context)
              → new ExecRemoteInterpreterProcess(...)
      → ExecRemoteInterpreterProcess.start()
        → ProcessBuilder → "bin/interpreter.sh"
          → java -cp ... RemoteInterpreterServer  [new JVM]
            → RemoteInterpreterServer.main()
              → registerInterpreterProcess() callback to server
```

### Interpreter Process Lifecycle

1. **Launch**: Server creates `RemoteInterpreterProcess` via launcher plugin
2. **Start**: Process starts as separate JVM (`bin/interpreter.sh` → `RemoteInterpreterServer.main()`)
3. **Register**: Process calls `registerInterpreterProcess()` back to server's `RemoteInterpreterEventServer`
4. **Init**: Server calls `init(properties)` — passes all configuration as a flat `Map<String, String>`
5. **Create**: Server calls `createInterpreter(className, properties)` — instantiates interpreter via reflection
6. **Open**: First `interpret()` triggers `LazyOpenInterpreter.open()` — interpreter initializes resources
7. **Execute**: `interpret(code, context)` — runs code; partial output streams via `appendOutput()` events
8. **Shutdown**: `close()` → `shutdown()` → JVM exits
9. **Recovery**: `RecoveryStorage` persists process info; on server restart, reconnects to surviving processes

### InterpreterGroup Scoping

`InterpreterOption` controls process isolation via `perNote` and `perUser` settings:

| perNote | perUser | Behavior |
|---------|---------|----------|
| `shared` | `shared` | All users share one process (default) |
| `scoped` | `shared` | Separate interpreter instance per note, same process |
| `isolated` | `shared` | Separate process per note |
| `shared` | `scoped` | Separate interpreter instance per user, same process |
| `shared` | `isolated` | Separate process per user |
| `scoped` | `scoped` | Separate instance per user+note |
| `isolated` | `isolated` | Separate process per user+note (full isolation) |

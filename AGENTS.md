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

# AGENTS.md

> Guidance for AI coding agents working on the Apache Zeppelin codebase.
> See [AGENTS.md specification](https://github.com/agentsmd/agents.md).

## Project Overview

Apache Zeppelin is a web-based notebook for interactive data analytics. It provides a unified interface to multiple data processing backends (Spark, Flink, Python, JDBC, etc.) through a pluggable interpreter architecture. Each interpreter runs in its own JVM process and communicates with the server via Apache Thrift RPC.

- **Language**: Java 11, Scala 2.12
- **Build**: Maven multi-module (wrapper: `./mvnw`)
- **Frontend**: Angular 13 (Node 18) in `zeppelin-web-angular/`
- **Version**: 0.13.0-SNAPSHOT

## Build & Test

```bash
# Full build (skip tests)
./mvnw clean package -DskipTests

# Build single module (--am builds required upstream modules)
./mvnw clean package -pl zeppelin-server --am -DskipTests

# Run module tests
./mvnw test -pl zeppelin-interpreter --am

# Run single test class/method
./mvnw test -pl zeppelin-server --am -Dtest=NotebookServerTest
./mvnw test -pl zeppelin-server --am -Dtest=NotebookServerTest#testMethod

# Common profiles
#   -Pspark-3.5 -Pspark-scala-2.12   Spark version
#   -Pflink-117                        Flink version
#   -Pbuild-distr                      Full distribution
#   -Prat                              Apache RAT license check
#   -Pweb-classic                      Additionally builds the classic UI web module when specified
```

## Build Gotchas

### Shaded JAR Rebuild Chain

The most common build mistake: modifying `zeppelin-interpreter` without rebuilding `zeppelin-interpreter-shaded`. The shaded JAR is an uber JAR that all interpreter processes use. If it's stale, you get `ClassNotFoundException` or `NoSuchMethodError` at runtime.

```bash
# After changing zeppelin-interpreter, ALWAYS rebuild in order:
./mvnw clean package -pl zeppelin-interpreter -DskipTests
./mvnw clean package -pl zeppelin-interpreter-shaded -DskipTests
# Then rebuild affected interpreter modules

# Shorthand:
./mvnw clean package -pl zeppelin-interpreter,zeppelin-interpreter-shaded -DskipTests
```

The shaded JAR is also copied to `interpreter/` directory by maven-antrun-plugin after packaging. If this directory has a stale JAR, interpreter processes will load old code.

### Module Build Order

Maven modules are ordered in the root `pom.xml`. Key sequence:
```
zeppelin-interpreter → zeppelin-interpreter-shaded → zeppelin-server
```

All interpreter modules build after `zeppelin-interpreter-shaded`. A second shading chain exists for Jupyter:
```
zeppelin-jupyter-interpreter → zeppelin-jupyter-interpreter-shaded → python, rlang
```

## Module Architecture

### Dependency Flow

```
zeppelin-interpreter          Base API: Interpreter, InterpreterContext, Thrift services
        ↓
zeppelin-interpreter-shaded   Uber JAR (maven-shade-plugin, relocated packages)
        ↓
zeppelin-server               Core engine + Jetty 11, REST/WebSocket APIs, HK2 DI, entry point
```

### Core Modules

#### `zeppelin-interpreter/`
The base framework that all interpreters depend on. Defines the interpreter API and the Thrift communication protocol. This module is shaded into an uber JAR (`zeppelin-interpreter-shaded`) and placed on each interpreter process's classpath.

Key classes:
- `Interpreter` (abstract) / `AbstractInterpreter` — base class every interpreter extends
- `InterpreterContext` — carries notebook/paragraph/user info into `interpret()` calls
- `InterpreterGroup` — manages a group of interpreter instances sharing one process
- `InterpreterResult` / `InterpreterOutput` — execution result model
- `RemoteInterpreterServer` — **entry point of each interpreter JVM process**; implements the Thrift `RemoteInterpreterService` server; receives RPC calls from zeppelin-server
- `InterpreterLauncher` (abstract) — how an interpreter process is started (Standard, Docker, K8s, YARN)
- `LifecycleManager` — manages interpreter process lifecycle (Null = keep alive, Timeout = idle shutdown)
- `DependencyResolver` / `AbstractDependencyResolver` — Maven artifact resolution for `%dep` paragraphs

Thrift definitions (`src/main/thrift/`):
- `RemoteInterpreterService.thrift` — server → interpreter RPCs
- `RemoteInterpreterEventService.thrift` — interpreter → server event callbacks

#### `zeppelin-server/`
The entry point and core of the Zeppelin application. Combines the web server / API layer with the core notebook engine, interpreter lifecycle management, scheduling, search, and plugin loading.

Web / API layer (`org.apache.zeppelin.server`, `rest`, `socket`):
- `ZeppelinServer` — `main()`, embedded Jetty 11 server, HK2 DI setup
- `NotebookRestApi`, `InterpreterRestApi`, `SecurityRestApi`, `ConfigurationsRestApi` — REST endpoints in `org.apache.zeppelin.rest`
- `NotebookServer` — WebSocket endpoint (`/ws`) for real-time notebook operations and paragraph execution
- `RemoteInterpreterEventServer` — Thrift server receiving callbacks from interpreter processes (output streaming, status updates)

Engine / runtime (`org.apache.zeppelin.notebook`, `interpreter`, `scheduler`, `search`, `plugin`, `storage`, `conf`):
- `Notebook` / `Note` / `Paragraph` — notebook data model and execution
- `InterpreterFactory` — creates interpreter instances
- `InterpreterSettingManager` — loads `interpreter-setting.json` from each interpreter directory, manages interpreter configurations
- `InterpreterSetting` — one interpreter's config + runtime state; creates `InterpreterLauncher` and `RemoteInterpreterProcess`
- `ManagedInterpreterGroup` — server-side `InterpreterGroup` implementation; owns the `RemoteInterpreterProcess`
- `NoteManager` — notebook CRUD, folder tree
- `SchedulerService` — Quartz-based cron scheduling
- `SearchService` — Lucene-based notebook search
- `PluginManager` — loads launcher and notebook-repo plugins (custom classloading, not Java SPI)
- `ZeppelinConfiguration` — config management (env vars → system properties → `zeppelin-site.xml` → defaults)
- `RecoveryStorage` — persists interpreter process info for server-restart recovery
- `ConfigStorage` — persists interpreter settings to JSON

#### `zeppelin-interpreter-shaded/`
Uses maven-shade-plugin to package `zeppelin-interpreter` + dependencies into an uber JAR with relocated packages (e.g., `org.apache.thrift` → `org.apache.zeppelin.shaded.org.apache.thrift`). This JAR is placed on each interpreter process's classpath.

#### `zeppelin-client/`
REST/WebSocket client library for programmatic access to Zeppelin.

### Interpreter Modules

Each interpreter is an independent Maven module inheriting from `zeppelin-interpreter-parent`:

| Module | Description |
|--------|-------------|
| `spark/` | Apache Spark (Scala/Python/R/SQL) — most complex interpreter |
| `python/` | IPython/Python |
| `flink/` | Apache Flink (Scala/Python/SQL) |
| `jdbc/` | JDBC (PostgreSQL, MySQL, Hive, etc.) |
| `shell/` | Bash/Shell commands |
| `markdown/` | Markdown rendering (Flexmark) |
| `java/` | Java interpreter |
| `groovy/` | Groovy |
| `neo4j/` | Neo4j Cypher |
| `mongodb/` | MongoDB |
| `elasticsearch/` | Elasticsearch |
| `bigquery/` | Google BigQuery |
| `cassandra/` | Apache Cassandra CQL |
| `hbase/` | Apache HBase |
| `rlang/` | R language |
| `livy/` | Apache Livy (remote Spark) |
| `sparql/` | SPARQL queries |
| `influxdb/` | InfluxDB |
| `file/` | HDFS/local file browser |
| `alluxio/` | Alluxio file system |

### Plugin Modules (`zeppelin-plugins/`)

**Launcher plugins** (`launcher/`) — how interpreter processes are started:
- `StandardInterpreterLauncher` (builtin) — local JVM process via `bin/interpreter.sh`
- `SparkInterpreterLauncher` (builtin) — Spark-specific launcher with `spark-submit`
- `DockerInterpreterLauncher` — Docker container
- `K8sStandardInterpreterLauncher` — Kubernetes pod
- `YarnInterpreterLauncher` — YARN container
- `FlinkInterpreterLauncher` — Flink-specific
- `ClusterInterpreterLauncher` — Zeppelin cluster mode

**NotebookRepo plugins** (`notebookrepo/`) — where notebooks are persisted:
- `VFSNotebookRepo` (builtin) — local filesystem (Apache VFS)
- `GitNotebookRepo` (builtin) — local git repo
- `GitHubNotebookRepo` — GitHub
- `S3NotebookRepo` — Amazon S3
- `GCSNotebookRepo` — Google Cloud Storage
- `AzureNotebookRepo` — Azure Blob Storage
- `MongoNotebookRepo` — MongoDB
- `OSSNotebookRepo` — Alibaba Cloud OSS

### Frontend

- `zeppelin-web-angular/` — Angular 13, Node 18 (active frontend)
- `zeppelin-web/` — Legacy AngularJS (activated with `-Pweb-classic`)

### Configuration Files

| File | Purpose |
|------|---------|
| `conf/zeppelin-site.xml` | Main server config (port, SSL, notebook storage, interpreter settings). Copy from `.template` |
| `conf/zeppelin-env.sh` | Shell environment (JAVA_OPTS, memory, Spark master). Copy from `.template` |
| `conf/shiro.ini` | Authentication/authorization (users, roles, LDAP, Kerberos, PAM). Copy from `.template` |
| `conf/interpreter.json` | Runtime interpreter settings — **auto-generated**, do not edit manually |
| `conf/log4j2.properties` | Logging configuration |
| `conf/interpreter-list` | Static list of available interpreters with Maven coordinates |
| `{interpreter}/resources/interpreter-setting.json` | Interpreter defaults (build-time, bundled in JAR) |

`conf/*.template` files are the source of truth. Actual config files (`zeppelin-site.xml`, `shiro.ini`, etc.) are `.gitignored`.

### Module Boundaries

Where new code should go:

| If the code... | Put it in |
|----------------|-----------|
| Is a base interface/class that all interpreters need | `zeppelin-interpreter` |
| Handles notebook state, interpreter lifecycle, scheduling, search, REST/WebSocket, or authentication realm | `zeppelin-server` |
| Is specific to one backend (Spark, Flink, JDBC, etc.) | That interpreter's module |
| Is a new way to launch interpreter processes | `zeppelin-plugins/launcher/` |
| Is a new notebook storage backend | `zeppelin-plugins/notebookrepo/` |

**Important**: Code added to `zeppelin-interpreter` is exposed to **every interpreter process** via the shaded JAR. Only add code there if all interpreters genuinely need it.

## Server–Interpreter Communication

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

## Plugin System & Reflection Patterns

### PluginManager — Custom Classloading

`PluginManager` (`zeppelin-server/.../plugin/PluginManager.java`) loads plugins without Java SPI:

```
Plugin loading flow:
1. Check builtin list (hardcoded class names):
   - Launchers: StandardInterpreterLauncher, SparkInterpreterLauncher
   - NotebookRepos: VFSNotebookRepo, GitNotebookRepo
   → if builtin: Class.forName(className) — direct classloading

2. If not builtin → external plugin:
   → Scan pluginsDir/{Launcher|NotebookRepo}/{pluginName}/ for JARs
   → Create URLClassLoader with those JARs
   → classLoader.loadClass(className)
   → Instantiate via reflection (constructor parameters)
```

External plugin directory structure:
```
plugins/
  Launcher/
    DockerInterpreterLauncher/
      *.jar
    K8sStandardInterpreterLauncher/
      *.jar
  NotebookRepo/
    S3NotebookRepo/
      *.jar
    GCSNotebookRepo/
      *.jar
```

### ReflectionUtils

`ReflectionUtils` (`zeppelin-server/.../util/ReflectionUtils.java`) provides generic reflection-based instantiation:

```java
// No-arg constructor
ReflectionUtils.createClazzInstance(className)

// Parameterized constructor
ReflectionUtils.createClazzInstance(className, parameterTypes, parameters)
```

Used to instantiate:
- `RecoveryStorage` — in `RemoteInterpreterServer` and `InterpreterSettingManager`
- `ConfigStorage` — in `InterpreterSettingManager`
- `LifecycleManager` — in `RemoteInterpreterServer`
- `NotebookRepo` — in `PluginManager`
- `InterpreterLauncher` — in `PluginManager`

### Interpreter Discovery

`InterpreterSettingManager` discovers interpreters at startup:

```
1. Scan interpreterDir (default: interpreter/) for subdirectories
2. For each subdirectory, look for interpreter-setting.json
3. Parse JSON → List<RegisteredInterpreter>
4. Register each interpreter's className, properties, editor settings
```

`interpreter-setting.json` format (in each interpreter module's resources):
```json
[{
  "group": "spark",
  "name": "spark",
  "className": "org.apache.zeppelin.spark.SparkInterpreter",
  "properties": {
    "spark.master": { "defaultValue": "local[*]", "description": "Spark master" }
  },
  "editor": { "language": "scala", "editOnDblClick": false }
}]
```

### ZeppelinConfiguration Priority

Configuration values are resolved in order (first match wins):
1. **Environment variables** (e.g., `ZEPPELIN_HOME`, `ZEPPELIN_PORT`)
2. **System properties** (e.g., `-Dzeppelin.server.port=8080`)
3. **zeppelin-site.xml** (`conf/zeppelin-site.xml`)
4. **Hardcoded defaults** (`ConfVars` enum in `ZeppelinConfiguration`)

### HK2 Dependency Injection (zeppelin-server)

`ZeppelinServer.startZeppelin()` sets up HK2 DI via `ServiceLocatorUtilities.bind()`:

```java
new AbstractBinder() {
    protected void configure() {
        bind(storage).to(ConfigStorage.class);
        bindAsContract(PluginManager.class).in(Singleton.class);
        bindAsContract(InterpreterFactory.class).in(Singleton.class);
        bindAsContract(NotebookRepoSync.class).to(NotebookRepo.class).in(Singleton.class);
        bindAsContract(Notebook.class).in(Singleton.class);
        // ... InterpreterSettingManager, SearchService, etc.
    }
}
```

REST API classes use `@Inject` to receive these singletons.

## Contributing Guide

### Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| JDK | 11 | Required. Not 8, not 17 |
| Maven | 3.6.3+ | Use the wrapper `./mvnw` — no separate install needed |
| Node.js | 18.x | Only for frontend (`zeppelin-web-angular/`) |

### Initial Setup

```bash
# Clone the repository
git clone https://github.com/apache/zeppelin.git
cd zeppelin

# First build — skip tests to verify environment works
./mvnw clean package -DskipTests
# This takes ~10 minutes. If it succeeds, your environment is ready.

# Frontend setup (only if working on UI)
cd zeppelin-web-angular
npm install
cd ..
```

### Development Workflow

When starting a new change, use a **git worktree** instead of switching branches in your main checkout. This keeps your primary working directory clean and allows parallel work across multiple branches:

```bash
# Create a worktree for your feature branch
git worktree add ../zeppelin-ZEPPELIN-XXXX -b ZEPPELIN-XXXX-description
cd ../zeppelin-ZEPPELIN-XXXX

# When done, clean up
git worktree remove ../zeppelin-ZEPPELIN-XXXX
```

```bash
# Build only the module you're changing (--am builds required upstream modules)
./mvnw clean package -pl zeppelin-server --am -DskipTests

# Run tests for your module
./mvnw test -pl zeppelin-server --am

# Run a specific test
./mvnw test -pl zeppelin-server --am -Dtest=NotebookServerTest#testMethod

# Start the dev frontend (proxies API to localhost:8080)
cd zeppelin-web-angular && npm start
```

For Spark or Flink work, add the version profile:
```bash
./mvnw clean package -pl spark -Pspark-3.5 -Pspark-scala-2.12 -DskipTests
```

### Before Submitting a PR

1. **Write unit tests**. Every code change must include corresponding unit tests. Bug fixes should include a test that reproduces the bug. New features should have tests covering the main paths.

2. **Run tests for affected modules**:
   ```bash
   ./mvnw test -pl <your-module>
   ```

3. **Check license headers** — all new files must have the Apache License 2.0 header:
   ```bash
   ./mvnw clean org.apache.rat:apache-rat-plugin:check -Prat
   ```

4. **Lint frontend changes** (if applicable):
   ```bash
   cd zeppelin-web-angular && npm run lint:fix
   ```

5. **Create a JIRA issue** at [issues.apache.org/jira/browse/ZEPPELIN](https://issues.apache.org/jira/browse/ZEPPELIN) and use the issue number in PR title: `[ZEPPELIN-XXXX] description`.

### REST API Pattern

All REST endpoints follow this pattern:

```java
@Path("/notebook")
@Produces("application/json")
@Singleton
public class NotebookRestApi extends AbstractRestApi {
    @Inject
    public NotebookRestApi(Notebook notebook, ...) {
        super(authenticationService);
    }

    @GET
    @Path("/{noteId}")
    @ZeppelinApi
    public Response getNote(@PathParam("noteId") String noteId) {
        // Authorization check
        checkIfUserCanRead(noteId, "Insufficient privileges");
        // Business logic via service layer
        Note note = notebook.getNote(noteId);
        // Return JsonResponse
        return new JsonResponse<>(Status.OK, "", note).build();
    }
}
```

Key conventions:
- Extend `AbstractRestApi` (provides `getServiceContext()` for auth)
- Use `@Inject` constructor for HK2 DI
- Annotate public methods with `@ZeppelinApi`
- Return `JsonResponse<T>(status, message, body).build()`
- Authorization via `checkIfUserCan{Read|Write|Run}()`

### Code Style

- **Java**: Google Java Style (2-space indent). Checkstyle enforced — no tabs, LF line endings, newline at EOF
- **Frontend**: ESLint + Prettier, auto-enforced via pre-commit hook (Husky + lint-staged)
- **Testing**: JUnit 5 (Jupiter) + Mockito (Java; a small number of legacy JUnit 4 tests still exist), Playwright (frontend E2E)
- **Logging**: SLF4J + Log4j2
- **License**: Apache License 2.0 — all new files need the ASF header

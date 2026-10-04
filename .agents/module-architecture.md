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

# Module Architecture

> Detailed architecture reference for coding agents. The repository-root
> [`AGENTS.md`](../AGENTS.md) contains the instructions that always apply.

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
| `livy/` | Apache Livy (remote Spark) |
| `sparql/` | SPARQL queries |
| `influxdb/` | InfluxDB |
| `file/` | HDFS/local file browser |

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

- `zeppelin-web-angular/` — active frontend (Angular; versions in `package.json`, Node build pin in `pom.xml` `node.version`)
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

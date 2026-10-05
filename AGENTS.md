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

- **Language**: Java, Scala — versions in root `pom.xml` (`java.version`, `scala.binary.version`)
- **Build**: Maven multi-module (wrapper: `./mvnw`)
- **Frontend**: Angular + TypeScript in `zeppelin-web-angular/` — versions in its `package.json`
- **Version**: see `<version>` in root `pom.xml`

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
#   -Pflink-1.20                       Flink version
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
zeppelin-jupyter-interpreter → zeppelin-jupyter-interpreter-shaded → python
```

## Architecture References

The detailed architecture is kept outside this automatically loaded file so the
scoped `AGENTS.md` chain remains within agent instruction budgets.

- Read [Module Architecture](.agents/module-architecture.md) before changing
  module boundaries, shared interpreter APIs, frontend placement, plugins, or
  configuration ownership.
- Read
  [Server–Interpreter Communication](.agents/server-interpreter-communication.md)
  before changing Thrift contracts, interpreter launch or lifecycle behavior,
  paragraph execution, or interpreter scoping.

## Contributing Guide

### Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| JDK | pinned in `pom.xml` (`java.version`) | Required — use exactly that major, not a newer/older JDK |
| Maven | provided by `./mvnw` (pinned in `.mvn/wrapper/maven-wrapper.properties`) | No separate install needed |
| Node.js | see `zeppelin-web-angular/package.json` (`engines.node`) | Only for frontend (`zeppelin-web-angular/`) |

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

## Security

Security model: [SECURITY.md](./SECURITY.md), which links to the project's
threat model at [THREAT_MODEL.md](./THREAT_MODEL.md).

Agents that scan this repository should consult `THREAT_MODEL.md` for the
project's in-scope / out-of-scope declarations, the security properties it
provides and disclaims, the configuration knobs whose defaults change the
security envelope, and the known non-findings (recurring false positives)
before reporting issues. In particular, Apache Zeppelin executes user-supplied
notebook code through its interpreters by design — that is the product's
function, not a vulnerability; see `THREAT_MODEL.md` §3, §9, and §11a.

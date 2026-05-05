# Spark Connect Interpreter for Apache Zeppelin

## What is Spark Connect?

Spark Connect (Spark 3.5+) is a new client-server architecture for Apache Spark that decouples the Spark client from the Spark cluster. Unlike the traditional `spark` interpreter which requires running Spark in the same JVM process, Spark Connect is a **thin gRPC client** that communicates with a remote Spark cluster via the `sc://host:port` connection string.

## Why Use Spark Connect?

- **No local Spark installation** — Zeppelin doesn't need the full Spark distribution on its host
- **Remote cluster support** — Connect to any Spark 3.5+ cluster over the network
- **Token authentication** — Support for token-based auth and SSL
- **Multi-user isolation** — Per-user session quotas prevent resource exhaustion
- **Cleaner deployments** — Simpler Docker images, reduced memory footprint

## Differences from the Legacy Spark Interpreter

| Feature | Spark Interpreter | Spark Connect Interpreter |
|---------|-------------------|---------------------------|
| **Architecture** | In-process SparkContext | Remote gRPC client |
| **Installation** | Requires full Spark on host | Only Spark Connect client JAR needed |
| **Scala support** | Yes (via embedded Scala interpreter) | No (client-only protocol) |
| **R support** | Yes (SparkR) | No (not supported) |
| **ZeppelinContext** | Full support (`z.show()`, Angular) | Returns `null` |
| **Multi-user** | Global shared SparkContext | Isolated sessions per user |
| **Session quota** | Not enforced | Per-user quota (default: 5) |

## Prerequisites

1. **Spark 3.5.x cluster** running the Spark Connect server
   ```bash
   # Start a Spark Connect server on port 15002
   spark-shell --master <cluster-url> --conf spark.connect.grpc.binding.port=15002
   ```

2. **Python 3.x** (for PySpark/IPySpark support)

## Configuration Properties

The Spark Connect interpreter supports the following configuration properties in the Zeppelin UI:

### Connection Settings

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `zeppelin.spark.connect.url` | string | `sc://localhost:15002` | Spark Connect server URL |
| `zeppelin.spark.connect.token` | string | `` | Optional token for authentication (redacted in logs) |
| `zeppelin.spark.connect.use_ssl` | checkbox | false | Enable SSL/TLS for connection |
| `zeppelin.spark.connect.user_id` | string | `` | User ID to report to Spark Connect server |

### Session Management

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `zeppelin.spark.connect.maxSessionsPerUser` | number | 5 | Maximum concurrent sessions per user |

### Execution

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `zeppelin.spark.maxResult` | number | 10000 | Maximum result rows to fetch |
| `zeppelin.spark.concurrentSQL` | checkbox | false | Allow concurrent SQL execution (within notebook) |
| `zeppelin.spark.concurrentSQL.max` | number | 10 | Max concurrent SQL threads |

### PySpark

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `zeppelin.python` | string | `python` | Python executable path |
| `zeppelin.pyspark.useIPython` | checkbox | true | Use IPython if available |

## Usage

### SQL Mode (Default)

```sql
%spark-connect

SELECT * FROM my_table LIMIT 10
```

### SQL with Concurrent Execution

```sql
%spark-connect.sql

-- This uses the concurrentSQL scheduler
SELECT COUNT(*) FROM large_table
```

### PySpark

```python
%spark-connect.pyspark

df = spark.sql("SELECT * FROM my_table")
df.show()
```

### IPython PySpark

```python
%spark-connect.ipyspark

# Full IPython REPL with Spark
df = spark.sql("SELECT COUNT(*) FROM table")
df.collect()
```

## Architecture

### SparkConnectInterpreter
- Core interpreter managing the remote Spark session
- Enforces per-user session quota via `ConcurrentHashMap<String, Integer>`
- Uses `NotebookLockManager` for per-notebook sequential execution
- Delegates SQL parsing to `SqlSplitter` for multi-statement support

### SparkConnectSqlInterpreter
- SQL-only frontend with optional concurrent scheduler
- Shares the Spark session with `SparkConnectInterpreter`

### PySparkConnectInterpreter
- Bridges Java `SparkSession` to Python via Py4j
- Uses custom Python wrapper classes (`SparkConnectDataFrame`, `SparkConnectSession`)
- Supports same Python executable resolution as Spark's own PySpark

### IPySparkConnectInterpreter
- IPython variant of PySpark
- Uses the same shared session model

### SparkConnectUtils
- Stateless utilities for:
  - Building Spark Connect URIs with token/SSL/user_id params
  - Formatting DataFrames as Zeppelin `%table` output
  - Streaming large result sets to avoid memory overflow

### NotebookLockManager
- Per-notebook `ReentrantLock` registry (fair FIFO ordering)
- Ensures sequential query execution within a single notebook
- Prevents concurrent modifications to shared notebook state

## Session Isolation and Multi-User Support

Each user gets **isolated Spark sessions** tracked in a global `ConcurrentHashMap<String, Integer>`:
- Username extracted from Zeppelin auth (falls back to `"anonymous"`)
- Per-user quota enforced (`maxSessionsPerUser`, default 5)
- Prevents runaway session proliferation

Within a notebook, a fair `ReentrantLock` ensures:
- Only one query executes at a time (even with `concurrentSQL=true`)
- FIFO ordering prevents starvation

## Testing

### Unit Tests (No Spark Server Required)

Tests for `SparkConnectUtils` utility class:
```bash
mvn test -pl spark-connect -Dtest=SparkConnectUtilsTest
```

### Integration Tests (Requires Spark Connect Server)

Full interpreter tests with a live Spark server:
```bash
SPARK_CONNECT_TEST_REMOTE=sc://localhost:15002 \
mvn test -pl spark-connect
```

Only integration tests are executed when `SPARK_CONNECT_TEST_REMOTE` is set.

## Limitations

1. **No Scala interpreter** — Spark Connect is a client-only protocol; embedded Scala REPL not supported
2. **No R support** — `%spark.r` and `%spark.ir` not available
3. **No ZeppelinContext** — `z.show()`, Angular widgets, and other Zeppelin-specific features return `null`
4. **Spark 3.5.x only** — The gRPC protocol is version-locked to Spark 3.5
5. **No progress tracking** — Job progress API always returns 0

## Dependency Shading

The module uses Maven Shade Plugin to relocate conflicting dependencies:
- `io.netty` → `org.apache.zeppelin.spark.connect.io.netty`
- `com.google` → `org.apache.zeppelin.spark.connect.com.google`
- `io.grpc` → `org.apache.zeppelin.spark.connect.io.grpc`

This prevents classpath conflicts with Zeppelin Server's own Netty and other interpreters.

## Examples

### Connect to a Remote Spark Cluster

Configure in Zeppelin UI:
- **URL**: `sc://spark-server.example.com:15002`
- **Token**: `your-auth-token` (if required)
- **Use SSL**: Enable if cluster uses TLS

### Run Multi-Statement SQL

```sql
%spark-connect

CREATE OR REPLACE TEMP VIEW my_view AS
  SELECT * FROM source_table WHERE year = 2024;

SELECT COUNT(*) FROM my_view;
```

### PySpark with Pandas

```python
%spark-connect.pyspark

# Create a Spark DataFrame and convert to Pandas
import pandas as pd
df_spark = spark.sql("SELECT * FROM my_table")
df_pandas = df_spark.toPandas()
print(df_pandas.head())
```

### Inspect DataFrame Schema

```python
%spark-connect.pyspark

df = spark.sql("SELECT * FROM events LIMIT 1")
df.explain()  # Logical and physical plan
df.printSchema()  # Column names and types
```

## Troubleshooting

### Connection Refused

- Ensure Spark Connect server is running: `spark-shell --master <url> --conf spark.connect.grpc.binding.port=15002`
- Verify network connectivity and firewall rules
- Check `zeppelin.spark.connect.url` configuration

### Authentication Failures

- Verify `zeppelin.spark.connect.token` matches server token requirements
- Enable `zeppelin.spark.connect.use_ssl` if cluster uses TLS

### Out of Memory

- Use `zeppelin.spark.maxResult` to limit rows fetched
- Use `spark.sql(...).limit(n)` in queries to reduce data transfer
- Enable `zeppelin.spark.connect.use_ssl` to stream results instead of collecting

## References

- [Apache Spark Connect Documentation](https://spark.apache.org/docs/latest/spark-connect-overview.html)
- [Spark Connect Protocol](https://spark.apache.org/docs/latest/spark-connect-introduction.html)
- [Zeppelin Interpreter Development](https://zeppelin.apache.org/docs/latest/usage/interpreter/interpreter_binding_mode.html)

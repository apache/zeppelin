---
layout: page
title: "Flink Interpreter for Apache Zeppelin"
description: "Apache Flink is an open source platform for distributed stream and batch data processing."
group: interpreter
---
<!--
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->
{% include JB/setup %}

# Flink interpreter for Apache Zeppelin

<div id="toc"></div>

## Overview
[Apache Flink](https://flink.apache.org) is an open source platform for distributed stream and batch data processing. Flink’s core is a streaming dataflow engine that provides data distribution, communication, and fault tolerance for distributed computations over data streams. Flink also builds batch processing on top of the streaming engine, overlaying native iteration support, managed memory, and program optimization.

In Zeppelin 0.9, we refactor the Flink interpreter in Zeppelin to support the latest version of Flink. **Only Flink 1.10+ is supported, old version of flink may not work.**
Apache Flink is supported in Zeppelin with Flink interpreter group which consists of below five interpreters.

<table class="table-configuration">
  <tr>
    <th>Name</th>
    <th>Class</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>%flink</td>
    <td>FlinkInterpreter</td>
    <td>Creates ExecutionEnvironment/StreamExecutionEnvironment/BatchTableEnvironment/StreamTableEnvironment and provides a Scala environment</td>
  </tr>
  <tr>
    <td>%flink.pyflink</td>
    <td>PyFlinkInterpreter</td>
    <td>Provides a python environment</td>
  </tr>
  <tr>
    <td>%flink.ipyflink</td>
    <td>IPyFlinkInterpreter</td>
    <td>Provides an ipython environment</td>
  </tr>
  <tr>
    <td>%flink.ssql</td>
    <td>FlinkStreamSqlInterpreter</td>
    <td>Provides a stream sql environment</td>
  </tr>
  <tr>
    <td>%flink.bsql</td>
    <td>FlinkBatchSqlInterpreter</td>
    <td>Provides a batch sql environment</td>
  </tr>
</table>

## Prerequisites

* Download Flink 1.10 for scala 2.11 (Only scala-2.11 is supported, scala-2.12 is not supported yet in Zeppelin)
* Download [flink-hadoop-shaded](https://repo1.maven.org/maven2/org/apache/flink/flink-shaded-hadoop-2/2.8.3-10.0/flink-shaded-hadoop-2-2.8.3-10.0.jar) and put it under lib folder of flink (flink interpreter need that to support yarn mode)

## Configuration
The Flink interpreter can be configured with properties provided by Zeppelin (as following table).
You can also set other flink properties which are not listed in the table. For a list of additional properties, refer to [Flink Available Properties](https://ci.apache.org/projects/flink/flink-docs-master/ops/config.html).
<table class="table-configuration">
  <tr>
    <th>Property</th>
    <th>Default</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>`FLINK_HOME`</td>
    <td></td>
    <td>Location of flink installation. It is must be specified, otherwise you can not use flink in Zeppelin</td>
  </tr>
  <tr>
    <td>`HADOOP_CONF_DIR`</td>
    <td></td>
    <td>Location of hadoop conf, this is must be set if running in yarn mode</td>
  </tr>
  <tr>
    <td>`HIVE_CONF_DIR`</td>
    <td></td>
    <td>Location of hive conf, this is must be set if you want to connect to hive metastore</td>
  </tr>
  <tr>
    <td>flink.execution.mode</td>
    <td>local</td>
    <td>Execution mode of flink, e.g. local | yarn | remote</td>
  </tr>
  <tr>
    <td>flink.execution.remote.host</td>
    <td></td>
    <td>Host name of running JobManager. Only used for remote mode</td>
  </tr>
  <tr>
    <td>flink.execution.remote.port</td>
    <td></td>
    <td>Port of running JobManager. Only used for remote mode</td>
  </tr>
  <tr>
    <td>flink.jm.memory</td>
    <td>1024</td>
    <td>Total number of memory(mb) of JobManager</td>
  </tr>
  <tr>
    <td>flink.tm.memory</td>
    <td>1024</td>
    <td>Total number of memory(mb) of TaskManager</td>
  </tr>
  <tr>
    <td>flink.tm.slot</td>
    <td>1</td>
    <td>Number of slot per TaskManager</td>
  </tr>
  <tr>
    <td>local.number-taskmanager</td>
    <td>4</td>
    <td>Total number of TaskManagers in local mode</td>
  </tr>
  <tr>
    <td>flink.yarn.appName</td>
    <td>Zeppelin Flink Session</td>
    <td>Yarn app name</td>
  </tr>
  <tr>
    <td>flink.yarn.queue</td>
    <td>default</td>
    <td>queue name of yarn app</td>
  </tr>
  <tr>
    <td>flink.webui.yarn.useProxy</td>
    <td>false</td>
    <td>whether use yarn proxy url as flink weburl, e.g. http://resource-manager:8088/proxy/application_1583396598068_0004</td>
  </tr>
  <tr>
    <td>flink.webui.yarn.yarnAddress</td>
    <td></td>
    <td>Set this value only when your yarn address is mapped to some other address, e.g. some cloud vender will map `http://resource-manager:8088` to `https://xxx-yarn.yy.cn/gateway/kkk/yarn`</td>
  </tr>
  <tr>
    <td>flink.udf.jars</td>
    <td></td>
    <td>Flink udf jars (comma separated), zeppelin will register udf in this jar automatically for user. The udf name is the class name.</td>
  </tr>
  <tr>
    <td>flink.udf.jars.packages</td>
    <td></td>
    <td>Packages (comma separated) that would be searched for the udf defined in `flink.udf.jars`.</td>
  </tr>
  <tr>
    <td>flink.execution.jars</td>
    <td></td>
    <td>Additional user jars (comma separated)</td>
  </tr>
  <tr>
    <td>flink.execution.packages</td>
    <td></td>
    <td>Additional user packages (comma separated), e.g. org.apache.flink:flink-connector-kafka_2.11:1.10,org.apache.flink:flink-connector-kafka-base_2.11:1.10.0,org.apache.flink:flink-json:1.10.0</td>
  </tr>
  <tr>
    <td>zeppelin.flink.concurrentBatchSql.max</td>
    <td>10</td>
    <td>Max concurrent sql of Batch Sql (`%flink.bsql`)</td>
  </tr>
  <tr>
    <td>zeppelin.flink.concurrentStreamSql.max</td>
    <td>10</td>
    <td>Max concurrent sql of Stream Sql (`%flink.ssql`)</td>
  </tr>
  <tr>
    <td>zeppelin.pyflink.python</td>
    <td>python</td>
    <td>Python binary executable for PyFlink</td>
  </tr>
  <tr>
    <td>table.exec.resource.default-parallelism</td>
    <td>1</td>
    <td>Default parallelism for flink sql job</td>
  </tr>
  <tr>
    <td>zeppelin.flink.scala.color</td>
    <td>true</td>
    <td>Whether display scala shell output in colorful format</td>
  </tr>
  <tr>
    <td>zeppelin.flink.enableHive</td>
    <td>false</td>
    <td>Whether enable hive</td>
  </tr>
  <tr>
    <td>zeppelin.flink.enableHive</td>
    <td>false</td>
    <td>Whether enable hive</td>
  </tr>
  <tr>
    <td>zeppelin.flink.hive.version</td>
    <td>2.3.4</td>
    <td>Hive version that you would like to connect</td>
  </tr>
  <tr>
    <td>zeppelin.flink.module.enableHive</td>
    <td>false</td>
    <td>Whether enable hive module, hive udf take precedence over flink udf if hive module is enabled.</td>
  </tr>
  <tr>
    <td>zeppelin.flink.maxResult</td>
    <td>1000</td>
    <td>max number of row returned by sql interpreter</td>
  </tr>
  <tr>
    <td>`flink.interpreter.close.shutdown_cluster`</td>
    <td>true</td>
    <td>Whether shutdown application when closing interpreter</td>
  </tr>
  <tr>
    <td>`zeppelin.interpreter.close.cancel_job`</td>
    <td>true</td>
    <td>Whether cancel flink job when closing interpreter</td>
  </tr>
</table>


## StreamExecutionEnvironment, ExecutionEnvironment, StreamTableEnvironment, BatchTableEnvironment

Zeppelin will create 6 variables as flink scala (`%flink`) entry point:

* `senv`    (StreamExecutionEnvironment), 
* `benv`     (ExecutionEnvironment)
* `stenv`   (StreamTableEnvironment for blink planner) 
* `btenv`   (BatchTableEnvironment for blink planner)
* `stenv_2`   (StreamTableEnvironment for flink planner) 
* `btenv_2`   (BatchTableEnvironment for flink planner)

And will create 6 variables as pyflink (`%flink.pyflink` or `%flink.ipyflink`) entry point:

* `s_env`    (StreamExecutionEnvironment), 
* `b_env`     (ExecutionEnvironment)
* `st_env`   (StreamTableEnvironment for blink planner) 
* `bt_env`   (BatchTableEnvironment for blink planner)
* `st_env_2`   (StreamTableEnvironment for flink planner) 
* `bt_env_2`   (BatchTableEnvironment for flink planner)

## Execution mode (Local/Remote/Yarn)

Flink in Zeppelin supports 3 execution modes (`flink.execution.mode`):

* Local
* Remote
* Yarn

### Run Flink in Local Mode

Running Flink in Local mode will start a MiniCluster in local JVM. By default, the local MiniCluster will use port 8081, so make sure this port is available in your machine,
otherwise you can configure `rest.port` to specify another port. You can also specify `local.number-taskmanager` and `flink.tm.slot` to customize the number of TM and number of slots per TM, 
because by default it is only 4 TM with 1 Slots which may not be enough for some cases.

### Run Flink in Remote Mode

Running Flink in remote mode will connect to a existing flink cluster which could be standalone cluster or yarn session cluster. Besides specifying `flink.execution.mode` to be `remote`. You also need to specify
`flink.execution.remote.host` and `flink.execution.remote.port` to point to flink job manager.

### Run Flink in Yarn Mode

In order to run flink in Yarn mode, you need to make the following settings:

* Set `flink.execution.mode` to `yarn`
* Set `HADOOP_CONF_DIR` in flink's interpreter setting.
* Make sure `hadoop` command is your PATH. Because internally flink will call command `hadoop classpath` and load all the hadoop related jars in the flink interpreter process


## Blink/Flink Planner

There're 2 planners supported by Flink's table api: `flink` & `blink`.

* If you want to use DataSet api, and convert it to flink table then please use flink planner (`btenv_2` and `stenv_2`).
* In other cases, we would always recommend you to use `blink` planner. This is also what flink batch/streaming sql interpreter use (`%flink.bsql` & `%flink.ssql`)


## How to use Hive

In order to use Hive in Flink, you have to make the following setting.

* Set `zeppelin.flink.enableHive` to be true
* Set `zeppelin.flink.hive.version` to be the hive version you are using.
* Set `HIVE_CONF_DIR` to be the location where `hive-site.xml` is located. Make sure hive metastore is started and you have configure `hive.metastore.uris` in `hive-site.xml`
* Copy the following dependencies to the lib folder of flink installation. 
    * flink-connector-hive_2.11–1.10.0.jar
    * flink-hadoop-compatibility_2.11–1.10.0.jar
    * hive-exec-2.x.jar (for hive 1.x, you need to copy hive-exec-1.x.jar, hive-metastore-1.x.jar, libfb303–0.9.2.jar and libthrift-0.9.2.jar)

After these settings, you will be able to query hive table via either table api `%flink` or batch sql `%flink.bsql`

## Flink Batch SQL

`%flink.bsql` is used for flink's batch sql. You just type `help` to get all the available commands.

* Use `insert into` statement for batch ETL
* Use `select` statement for exploratory data analytics 

## Flink Streaming SQL

`%flink.ssql` is used for flink's streaming sql. You just type `help` to get all the available commands. Mainlly there're 2 cases:

* Use `insert into` statement for streaming processing
* Use `select` statement for streaming data analytics

## Flink UDF

You can use Flink scala UDF or Python UDF in sql. UDF for batch and streaming sql is the same. Here's 2 examples.

* Scala UDF

```scala
%flink

class ScalaUpper extends ScalarFunction {
  def eval(str: String) = str.toUpperCase
}
btenv.registerFunction("scala_upper", new ScalaUpper())

```

* Python UDF

```python

%flink.pyflink

class PythonUpper(ScalarFunction):
  def eval(self, s):
    return s.upper()

bt_env.register_function("python_upper", udf(PythonUpper(), DataTypes.STRING(), DataTypes.STRING()))

```

Besides defining udf in Zeppelin, you can also load udfs in jars via `flink.udf.jars`. For example, you can create
udfs in intellij and then build these udfs in one jar. After that you can specify `flink.udf.jars` to this jar, and flink
interpreter will detect all the udfs in this jar and register all the udfs to TableEnvironment, the udf name is the class name.

## ZeppelinContext
Zeppelin automatically injects `ZeppelinContext` as variable `z` in your Scala/Python environment. `ZeppelinContext` provides some additional functions and utilities.
See [Zeppelin-Context](../usage/other_features/zeppelin_context.html) for more details.

## IPython Support

By default, zeppelin would use IPython in `%flink.pyflink` when IPython is available, Otherwise it would fall back to the original python implementation.
For the IPython features, you can refer doc[Python Interpreter](python.html)

## Tutorial Notes

Zeppelin is shipped with several Flink tutorial notes which may be helpful for you. Except the first one, the below 4 notes cover the 4 main scenarios of flink.

* Flink Basic
* Batch ETL
* Exploratory Data Analytics
* Streaming ETL
* Streaming Data Analytics





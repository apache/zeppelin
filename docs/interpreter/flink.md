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

## Configuration
The Flink interpreter can be configured with properties provided by Zeppelin.
You can also set other flink properties which are not listed in the table. For a list of additional properties, refer to [Flink Available Properties](https://ci.apache.org/projects/flink/flink-docs-release-1.9/ops/config.html).
<table class="table-configuration">
  <tr>
    <th>Property</th>
    <th>Default</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>FLINK_HOME</td>
    <td></td>
    <td>Location of flink installation. It is must be specified, otherwise you can not use flink in zeppelin</td>
  </tr>
  <tr>
    <td>flink.execution.mode</td>
    <td>local</td>
    <td>Execution mode of flink, e.g. local | yarn | remote</td>
  </tr>
  <tr>
    <td>flink.execution.remote.host</td>
    <td></td>
    <td>jobmanager hostname if it is remote mode</td>
  </tr>
  <tr>
    <td>flink.execution.remote.port</td>
    <td></td>
    <td>jobmanager port if it is remote mode</td>
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
    <td>flink.tm.num</td>
    <td>2</td>
    <td>Number of TaskManager</td>
  </tr>
  <tr>
    <td>flink.tm.slot</td>
    <td>1</td>
    <td>Number of slot per TaskManager</td>
  </tr>
  <tr>
    <td>flink.yarn.appName</td>
    <td>Zeppelin Flink Session</td>
    <td>Yarn app name</td>
  </tr>
  <tr>
    <td>flink.yarn.queue</td>
    <td></td>
    <td>queue name of yarn app</td>
  </tr>
  <tr>
    <td>flink.yarn.jars</td>
    <td></td>
    <td>additional user jars (comma separated)</td>
  </tr>
  <tr>
    <td>zeppelin.flink.scala.color</td>
    <td>true</td>
    <td>whether display scala shell output in colorful format</td>
  </tr>
  <tr>
    <td>zeppelin.flink.enableHive</td>
    <td>false</td>
    <td>whether enable hive</td>
  </tr>
  <tr>
    <td>zeppelin.flink.printREPLOutput</td>
    <td>true</td>
    <td>Print REPL output</td>
  </tr>
  <tr>
    <td>zeppelin.flink.maxResult</td>
    <td>1000</td>
    <td>max number of row returned by sql interpreter</td>
  </tr>
  <tr>
    <td>zeppelin.flink.planner</td>
    <td>blink</td>
    <td>planner of flink table api, blink or flink</td>
  </tr>
  <tr>
    <td>zeppelin.pyflink.python</td>
    <td>python</td>
    <td>python executable for pyflink</td>
  </tr>
  <tr>
    <td>HADOOP_CONF_DIR</td>
    <td></td>
    <td>location of hadoop conf, this is must be set if running in yarn mode</td>
  </tr>
</table>


## StreamExecutionEnvironment, ExecutionEnvironment, StreamTableEnvironment, BatchTableEnvironment

Zeppelin will create 4 variables to represent flink's entrypoint:
* `senv`    (StreamExecutionEnvironment), 
* `env`     (ExecutionEnvironment)
* `stenv`   (StreamTableEnvironment) 
* `btenv`   (BatchTableEnvironment)

## Flink Planner

Starting from Flink 1.9, there're 2 planners supported by Flink's table api: flink & blink.
* If you want to use DataSet api, then please use flink planner (specify `zeppelin.flink.planner` to `flink`).
* In other cases, we would always recommend you to use blink planner which is also the default value of `zeppelin.flink.planner`.

## How to use Hive

In order to use Hive in Flink, you have to do several setting.
* Set `zeppelin.flink.enableHive` to `true`
* Copy necessary dependencies to flink's lib folder, check this [link](https://ci.apache.org/projects/flink/flink-docs-release-1.9/dev/table/hive/#depedencies) for more details 
  * flink-connector-hive_{scala_version}-{flink.version}.jar
  * flink-hadoop-compatibility_{scala_version}-{flink.version}.jar
  * flink-shaded-hadoop-2-uber-{hadoop.version}-{flink-shaded.version}.jar
  * hive-exec-2.x.jar (for Hive 1.x, you need to copy hive-exec-1.x.jar, hive-metastore-1.x.jar, libfb303-0.9.2.jar and libthrift-0.9.2.jar)
* Specify `HIVE_CONF_DIR` either in flink interpreter setting or `zeppelin-env.sh`
* Specify `zeppelin.flink.hive.version`, by default it is 2.3.4. If you are using Hive 1.2.x, then you need to set it as `1.2.2`

After these settings, you will be able to query hive table via either table api `%flink` or batch sql `%flink.bsql`

## ZeppelinContext
Zeppelin automatically injects `ZeppelinContext` as variable `z` in your Scala/Python environment. `ZeppelinContext` provides some additional functions and utilities.
See [Zeppelin-Context](../usage/other_features/zeppelin_context.html) for more details.

## IPython support

By default, zeppelin would use IPython in `%flink.pyflink` when IPython is available, Otherwise it would fall back to the original python implementation.
If you don't want to use IPython, then you can set `zeppelin.pyflink.useIPython` as `false` in interpreter setting. For the IPython features, you can refer doc
[Python Interpreter](python.html)

## Tutorial Notes

Zeppelin is shipped with several Flink tutorial notes which may be helpful for you.




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
[Apache Flink](https://flink.apache.org) is a framework and distributed processing engine for stateful computations over unbounded and bounded data streams. 
Flink has been designed to run in all common cluster environments, perform computations at in-memory speed and at any scale.

In Zeppelin 0.9, we refactor the Flink interpreter in Zeppelin to support the latest version of Flink. **Only Flink 1.10+ is supported, old versions of flink won't work.**
Apache Flink is supported in Zeppelin with the Flink interpreter group which consists of the five interpreters listed below.

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

## Main Features

<table class="table-configuration">
  <tr>
    <th>Feature</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>Support multiple versions of Flink</td>
    <td>You can run different versions of Flink in one Zeppelin instance</td>
  </tr>
  <tr>
    <td>Support multiple versions of Scala</td>
    <td>You can run different Scala versions of Flink in on Zeppelin instance</td>
  </tr>
  <tr>
    <td>Support multiple languages</td>
    <td>Scala, Python, SQL are supported, besides that you can also collaborate across languages, e.g. you can write Scala UDF and use it in PyFlink</td>
  </tr>
  <tr>
    <td>Support multiple execution modes</td>
    <td>Local | Remote | Yarn | Yarn Application</td>
  </tr>
  <tr>
    <td>Support Hive</td>
    <td>Hive catalog is supported</td>
  </tr>
  <tr>
    <td>Interactive development</td>
    <td>Interactive development user experience increase your productivity</td>
  </tr>
  <tr>
    <td>Enhancement on Flink SQL</td>
    <td>* Support both streaming sql and batch sql in one notebook <br/>
* Support sql comment (single line comment/multiple line comment) <br/>
* Support advanced configuration (jobName, parallelism) <br/>
* Support multiple insert statements 
  </td>
  </tr>
    <td>Multi-tenancy</td>
    <td>Multiple user can work in one Zeppelin instance without affecting each other.</td>
  </tr>

  </tr>
    <td>Rest API Support</td>
    <td>You can not only submit Flink job via Zeppelin notebook UI, but also can do that via its rest api (You can use Zeppelin as Flink job server).</td>
  </tr>
</table>

## Play Flink in Zeppelin docker

For beginner, we would suggest you to play Flink in Zeppelin docker. 
First you need to download Flink, because there's no Flink binary distribution shipped with Zeppelin. 
e.g. Here we download Flink 1.12.2 to`/mnt/disk1/flink-1.12.2`,
and we mount it to Zeppelin docker container and run the following command to start Zeppelin docker.

```bash
docker run -u $(id -u) -p 8080:8080 -p 8081:8081 --rm -v /mnt/disk1/flink-1.12.2:/opt/flink -e FLINK_HOME=/opt/flink  --name zeppelin apache/zeppelin:0.10.0
```

After running the above command, you can open `http://localhost:8080` to play Flink in Zeppelin. We only verify the flink local mode in Zeppelin docker, other modes may not due to network issues.
`-p 8081:8081` is to expose Flink web ui, so that you can access Flink web ui via `http://localhost:8081`.

Here's screenshot of running note `Flink Tutorial/5. Streaming Data Analytics`

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/flink_docker_tutorial.gif">


You can also mount notebook folder to replace the built-in zeppelin tutorial notebook. 
e.g. Here's a repo of Flink sql cookbook on Zeppelin: [https://github.com/zjffdu/flink-sql-cookbook-on-zeppelin/](https://github.com/zjffdu/flink-sql-cookbook-on-zeppelin/)

You can clone this repo and mount it to docker,

```
docker run -u $(id -u) -p 8080:8080 --rm -v /mnt/disk1/flink-sql-cookbook-on-zeppelin:/notebook -v /mnt/disk1/flink-1.12.2:/opt/flink -e FLINK_HOME=/opt/flink  -e ZEPPELIN_NOTEBOOK_DIR='/notebook' --name zeppelin apache/zeppelin:0.10.0
```

## Prerequisites

Download Flink 1.10 or afterwards (Scala 2.11 & 2.12 are both supported)


## Flink on Zeppelin Architecture

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/flink_architecture.png">

The above diagram is the architecture of Flink on Zeppelin. Flink interpreter on the left side is actually a Flink client 
which is responsible for compiling and managing Flink job lifecycle, such as submit, cancel job, 
monitoring job progress and so on. The Flink cluster on the right side is the place where executing Flink job. 
It could be a MiniCluster (local mode), Standalone cluster (remote mode), 
Yarn session cluster (yarn mode) or Yarn application session cluster (yarn-application mode)

There are 2 important components in Flink interpreter: Scala shell & Python shell

* Scala shell is the entry point of Flink interpreter, it would create all the entry points of Flink program, such as ExecutionEnvironment，StreamExecutionEnvironment and TableEnvironment. Scala shell is responsible for compiling and running Scala code and sql.
* Python shell is the entry point of PyFlink, it is responsible for compiling and running Python code.

## Configuration

The Flink interpreter can be configured with properties provided by Zeppelin (as following table).
You can also add and set other Flink properties which are not listed in the table. For a list of additional properties, refer to [Flink Available Properties](https://ci.apache.org/projects/flink/flink-docs-master/ops/config.html).
<table class="table-configuration">
  <tr>
    <th>Property</th>
    <th>Default</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>`FLINK_HOME`</td>
    <td></td>
    <td>Location of Flink installation. It is must be specified, otherwise you can not use Flink in Zeppelin</td>
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
    <td>Execution mode of Flink, e.g. local | remote | yarn | yarn-application</td>
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
    <td>jobmanager.memory.process.size</td>
    <td>1024m</td>
    <td>Total memory size of JobManager, e.g. 1024m. It is official [Flink property](https://ci.apache.org/projects/flink/flink-docs-release-1.13/docs/deployment/config/)</td>
  </tr>
  <tr>
    <td>taskmanager.memory.process.size</td>
    <td>1024m</td>
    <td>Total memory size of TaskManager, e.g. 1024m. It is official [Flink property](https://ci.apache.org/projects/flink/flink-docs-release-1.13/docs/deployment/config/)</td>
  </tr>
  <tr>
    <td>taskmanager.numberOfTaskSlots</td>
    <td>1</td>
    <td>Number of slot per TaskManager</td>
  </tr>
  <tr>
    <td>local.number-taskmanager</td>
    <td>4</td>
    <td>Total number of TaskManagers in local mode</td>
  </tr>
  <tr>
    <td>yarn.application.name</td>
    <td>Zeppelin Flink Session</td>
    <td>Yarn app name</td>
  </tr>
  <tr>
    <td>yarn.application.queue</td>
    <td>default</td>
    <td>queue name of yarn app</td>
  </tr>
  <tr>
    <td>zeppelin.flink.uiWebUrl</td>
    <td></td>
    <td>User specified Flink JobManager url, it could be used in remote mode where Flink cluster is already started, or could be used as url template, e.g. https://knox-server:8443/gateway/cluster-topo/yarn/proxy/{% raw %}{{applicationId}}{% endraw %}/ where {% raw %}{{applicationId}}{% endraw %} is placeholder of yarn app id</td>
  </tr>
  <tr>
    <td>zeppelin.flink.run.asLoginUser</td>
    <td>true</td>
    <td>Whether run Flink job as the Zeppelin login user, it is only applied when running Flink job in hadoop yarn cluster and shiro is enabled</td>
  </tr> 
  <tr>
    <td>flink.udf.jars</td>
    <td></td>
    <td>Flink udf jars (comma separated), Zeppelin will register udf in these jars automatically for user. These udf jars could be either local files or hdfs files if you have hadoop installed. The udf name is the class name.</td>
  </tr>
  <tr>
    <td>flink.udf.jars.packages</td>
    <td></td>
    <td>Packages (comma separated) that would be searched for the udf defined in `flink.udf.jars`. Specifying this can reduce the number of classes to scan, otherwise all the classes in udf jar will be scanned.</td>
  </tr>
  <tr>
    <td>flink.execution.jars</td>
    <td></td>
    <td>Additional user jars (comma separated), these jars could be either local files or hdfs files if you have hadoop installed. It can be used to specify Flink connector jars or udf jars (no udf class auto-registration like `flink.udf.jars`)</td>
  </tr>
  <tr>
    <td>flink.execution.packages</td>
    <td></td>
    <td>Additional user packages (comma separated), e.g. `org.apache.flink:flink-json:1.10.0`</td>
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
    <td>Default parallelism for Flink sql job</td>
  </tr>
  <tr>
    <td>zeppelin.flink.scala.color</td>
    <td>true</td>
    <td>Whether display Scala shell output in colorful format</td>
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
    <td>Whether enable hive module, hive udf take precedence over Flink udf if hive module is enabled.</td>
  </tr>
  <tr>
    <td>zeppelin.flink.maxResult</td>
    <td>1000</td>
    <td>max number of row returned by sql interpreter</td>
  </tr>
  <tr>
    <td>`zeppelin.flink.job.check_interval`</td>
    <td>1000</td>
    <td>Check interval (in milliseconds) to check Flink job progress</td>
  </tr>
  <tr>
    <td>`flink.interpreter.close.shutdown_cluster`</td>
    <td>true</td>
    <td>Whether shutdown Flink cluster when closing interpreter</td>
  </tr>
  <tr>
    <td>`zeppelin.interpreter.close.cancel_job`</td>
    <td>true</td>
    <td>Whether cancel Flink job when closing interpreter</td>
  </tr>
</table>


## Interpreter Binding Mode

The default [interpreter binding mode](../usage/interpreter/interpreter_binding_mode.html) is `globally shared`. That means all notes share the same Flink interpreter which means they share the same Flink cluster.
In practice, we would recommend you to use `isolated per note` which means each note has own Flink interpreter without affecting each other (Each one has his own Flink cluster). 


## Execution Mode

Flink in Zeppelin supports 4 execution modes (`flink.execution.mode`):

* Local
* Remote
* Yarn
* Yarn Application

### Local Mode

Running Flink in local mode will start a MiniCluster in local JVM. By default, the local MiniCluster use port 8081, so make sure this port is available in your machine,
otherwise you can configure `rest.port` to specify another port. You can also specify `local.number-taskmanager` and `flink.tm.slot` to customize the number of TM and number of slots per TM.
Because by default it is only 4 TM with 1 slot in this MiniCluster which may not be enough for some cases.

### Remote Mode

Running Flink in remote mode will connect to an existing Flink cluster which could be standalone cluster or yarn session cluster. Besides specifying `flink.execution.mode` to be `remote`, you also need to specify
`flink.execution.remote.host` and `flink.execution.remote.port` to point to Flink job manager's rest api address.

### Yarn Mode

In order to run Flink in Yarn mode, you need to make the following settings:

* Set `flink.execution.mode` to be `yarn`
* Set `HADOOP_CONF_DIR` in Flink's interpreter setting or `zeppelin-env.sh`.
* Make sure `hadoop` command is on your `PATH`. Because internally Flink will call command `hadoop classpath` and load all the hadoop related jars in the Flink interpreter process

In this mode, Zeppelin would launch a Flink yarn session cluster for you and destroy it when you shutdown your Flink interpreter.

### Yarn Application Mode

In the above yarn mode, there will be a separated Flink interpreter process on the Zeppelin server host. However, this may run out of resources when there are too many interpreter processes.
So in practise, we would recommend you to use yarn application mode if you are using Flink 1.11 or afterwards (yarn application mode is only supported after Flink 1.11). 
In this mode Flink interpreter runs in the JobManager which is in yarn container.
In order to run Flink in yarn application mode, you need to make the following settings:

* Set `flink.execution.mode` to be `yarn-application`
* Set `HADOOP_CONF_DIR` in Flink's interpreter setting or `zeppelin-env.sh`.
* Make sure `hadoop` command is on your `PATH`. Because internally flink will call command `hadoop classpath` and load all the hadoop related jars in Flink interpreter process


## Flink Scala

Scala is the default language of Flink on Zeppelin（`%flink`), and it is also the entry point of Flink interpreter. Underneath Flink interpreter will create Scala shell 
which would create several built-in variables, including ExecutionEnvironment，StreamExecutionEnvironment and so on. 
So don't create these Flink environment variables again, otherwise you might hit weird issues. The Scala code you write in Zeppelin will be submitted to this Scala shell.  
Here are the builtin variables created in Flink Scala shell.

* senv (StreamExecutionEnvironment),
* benv (ExecutionEnvironment)
* stenv (StreamTableEnvironment for blink planner)
* btenv (BatchTableEnvironment for blink planner)
* stenv_2 (StreamTableEnvironment for flink planner)
* btenv_2 (BatchTableEnvironment for flink planner)
* z  (ZeppelinContext)

### Blink/Flink Planner

There are 2 planners supported by Flink SQL: `flink` & `blink`.

* If you want to use DataSet api, and convert it to Flink table then please use `flink` planner (`btenv_2` and `stenv_2`).
* In other cases, we would always recommend you to use `blink` planner. This is also what Flink batch/streaming sql interpreter use (`%flink.bsql` & `%flink.ssql`)

Check this [page](https://ci.apache.org/projects/flink/flink-docs-release-1.10/dev/table/common.html#main-differences-between-the-two-planners) for the difference between flink planner and blink planner.


### Stream WordCount Example

You can write whatever Scala code in Zeppelin. 

e.g. in the following example, we write a classical streaming wordcount example.


<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/flink_streaming_wordcount.png" width="80%">


### Code Completion

You can type tab for code completion.

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/flink_scala_codecompletion.png" width="80%">

### ZeppelinContext

`ZeppelinContext` provides some additional functions and utilities.
See [Zeppelin-Context](../usage/other_features/zeppelin_context.html) for more details. 
For Flink interpreter, you can use `z` to display Flink `Dataset/Table`. 

e.g. you can use `z.show` to display DataSet, Batch Table, Stream Table.

* z.show(DataSet)

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/flink_z_dataset.png">


* z.show(Batch Table)

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/flink_z_batch_table.png">


* z.show(Stream Table)

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/flink_z_stream_table.gif">


## Flink SQL

In Zeppelin, there are 2 kinds of Flink sql interpreter you can use

* `%flink.ssql`
Streaming Sql interpreter which launch Flink streaming job via `StreamTableEnvironment`
* `%flink.bsql`
Batch Sql interpreter which launch Flink batch job via `BatchTableEnvironment`

Flink Sql interpreter in Zeppelin is equal to Flink Sql-client + many other enhancement features.

### Enhancement SQL Features

#### Support batch SQL and streaming sql together.

In Flink Sql-client, either you run streaming sql or run batch sql in one session. You can not run them together. 
But in Zeppelin, you can do that. `%flink.ssql` is used for running streaming sql, while `%flink.bsql` is used for running batch sql. 
Batch/Streaming Flink jobs run in the same Flink session cluster.

#### Support multiple statements

You can write multiple sql statements in one paragraph, each sql statement is separated by semicolon. 

#### Comment support

2 kinds of sql comments are supported in Zeppelin:

* Single line comment start with `--`
* Multiple line comment around with `/* */`

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/flink_sql_comment.png">


#### Job parallelism setting

You can set the sql parallelism via paragraph local property: `parallelism`

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/flink_sql_parallelism.png">

#### Support multiple insert

Sometimes you have multiple insert statements which read the same source, 
but write to different sinks. By default, each insert statement would launch a separated Flink job, 
but you can set paragraph local property: `runAsOne` to be `true` to run them in one single Flink job.

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/flink_sql_multiple_insert.png">

#### Set job name

You can set Flink job name for insert statement via setting paragraph local property: `jobName`. To be noticed, 
you can only set job name for insert statement. Select statement is not supported yet. 
And this kind of setting only works for single insert statement. It doesn't work for multiple insert we mentioned above.

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/flink_sql_jobname.png">

### Streaming Data Visualization

Zeppelin can visualize the select sql result of Flink streaming job. Overall it supports 3 modes:

* Single
* Update
* Append

#### Single Mode

Single mode is for the case when the result of sql statement is always one row, 
such as the following example. The output format is HTML, 
and you can specify paragraph local property `template` for the final output content template. 
You can use `{i}` as placeholder for the `ith` column of result.

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/flink_single_mode.gif">


#### Update Mode

Update mode is suitable for the case when the output is more than one rows, and will always be updated continuously. 
Here’s one example where we use group by.

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/flink_update_mode.gif">

#### Append Mode

Append mode is suitable for the scenario where output data is always appended. 
E.g. the following example which use tumble window.

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/flink_append_mode.gif">

## PyFlink

PyFlink is Python entry point of  Flink on Zeppelin, internally Flink interpreter will create Python shell which
would create Flink's environment variables (including ExecutionEnvironment, StreamExecutionEnvironment and so on).
To be noticed, the java environment behind Pyflink is created in Scala shell.
That means underneath Scala shell and Python shell share the same environment.
These are variables created in Python shell.

* `s_env`    (StreamExecutionEnvironment),
* `b_env`     (ExecutionEnvironment)
* `st_env`   (StreamTableEnvironment for blink planner)
* `bt_env`   (BatchTableEnvironment for blink planner)
* `st_env_2`   (StreamTableEnvironment for flink planner)
* `bt_env_2`   (BatchTableEnvironment for flink planner)


### Configure PyFlink

There are 3 things you need to configure to make Pyflink work in Zeppelin.

* Install pyflink
  e.g. ( pip install apache-flink==1.11.1 ).
  If you need to use Pyflink udf, then you to install pyflink on all the task manager nodes. That means if you are using yarn, then all the yarn nodes need to install pyflink.
* Copy `python` folder under `${FLINK_HOME}/opt` to `${FLINK_HOME/lib`.
* Set `zeppelin.pyflink.python` as the python executable path. By default, it is the python in `PATH`. In case you have multiple versions of python installed, you need to configure `zeppelin.pyflink.python` as the python version you want to use.

### How to use PyFlink

There are 2 ways to use PyFlink in Zeppelin

* `%flink.pyflink`
* `%flink.ipyflink`

`%flink.pyflink` is much simple and easy,  you don't need to do anything except the above setting,
but its function is also limited. We suggest you to use `%flink.ipyflink` which provides almost the same user experience like jupyter.

### Configure IPyFlink

If you don't have anaconda installed, then you need to install the following 3 libraries.

```
pip install jupyter
pip install grpcio
pip install protobuf
```

If you have anaconda installed, then you only need to install following 2 libraries.

```
pip install grpcio
pip install protobuf
```

`ZeppelinContext` is also available in PyFlink, you can use it almost the same as in Flink Scala.

Check the [Python doc](python.html) for more features of IPython.


## Third party dependencies

It is very common to have third party dependencies when you write Flink job in whatever languages (Scala, Python, Sql).
It is very easy to add dependencies in IDE (e.g. add dependency in pom.xml),
but how can you do that in Zeppelin ? Mainly there are 2 settings you can use to add third party dependencies

* flink.execution.packages
* flink.execution.jars

### flink.execution.packages

This is the recommended way of adding dependencies. Its implementation is the same as adding
dependencies in `pom.xml`. Underneath it would download all the packages and its transitive dependencies
from maven repository, then put them on the classpath. Here's one example of how to add kafka connector of Flink 1.10 via [inline configuration](../usage/interpreter/overview.html#inline-generic-configuration).

```
%flink.conf

flink.execution.packages  org.apache.flink:flink-connector-kafka_2.11:1.10.0,org.apache.flink:flink-connector-kafka-base_2.11:1.10.0,org.apache.flink:flink-json:1.10.0
```

The format is `artifactGroup:artifactId:version`, if you have multiple packages,
then separate them with comma. `flink.execution.packages` requires internet accessible.
So if you can not access internet, you need to use `flink.execution.jars` instead.

### flink.execution.jars

If your Zeppelin machine can not access internet or your dependencies are not deployed to maven repository,
then you can use `flink.execution.jars` to specify the jar files you depend on (each jar file is separated with comma)

Here's one example of how to add kafka dependencies(including kafka connector and its transitive dependencies) via `flink.execution.jars`

```
%flink.conf

flink.execution.jars /usr/lib/flink-kafka/target/flink-kafka-1.0-SNAPSHOT.jar
```


## Flink UDF

There are 4 ways you can define UDF in Zeppelin.

* Write Scala UDF
* Write PyFlink UDF
* Create UDF via SQL
* Configure udf jar via flink.udf.jars

### Scala UDF

```scala
%flink

class ScalaUpper extends ScalarFunction {
  def eval(str: String) = str.toUpperCase
}

btenv.registerFunction("scala_upper", new ScalaUpper())
```

It is very straightforward to define scala udf almost the same as what you do in IDE.
After creating udf class, you need to register it via `btenv`.
You can also register it via `stenv` which share the same Catalog with `btenv`.


### Python UDF

```python

%flink.pyflink

class PythonUpper(ScalarFunction):
  def eval(self, s):
    return s.upper()

bt_env.register_function("python_upper", udf(PythonUpper(), DataTypes.STRING(), DataTypes.STRING()))

```
It is also very straightforward to define Python udf almost the same as what you do in IDE. 
After creating udf class, you need to register it via `bt_env`. 
You can also register it via `st_env` which share the same Catalog with `bt_env`.

### UDF via SQL

Some simple udf can be written in Zeppelin. But if the udf logic is very complicated, 
then it is better to write it in IDE, then register it in Zeppelin as following

```sql
%flink.ssql

CREATE FUNCTION myupper AS 'org.apache.zeppelin.flink.udf.JavaUpper';
```

But this kind of approach requires the udf jar must be on `CLASSPATH`, 
so you need to configure `flink.execution.jars` to include this udf jar on `CLASSPATH`, such as following:

```
%flink.conf

flink.execution.jars /usr/lib/flink-udf-1.0-SNAPSHOT.jar
```

### flink.udf.jars

The above 3 approaches all have some limitations:

* It is suitable to write simple Scala udf or Python udf in Zeppelin, but not suitable to write very complicated udf in Zeppelin. Because notebook doesn't provide advanced features compared to IDE, such as package management, code navigation and etc.
* It is not easy to share the udf between notes or users, you have to run the paragraph of defining udf in each flink interpreter.

So when you have many udfs or udf logic is very complicated and you don't want to register them by yourself every time, then you can use `flink.udf.jars`

* Step 1. Create a udf project in your IDE, write your udf there.
* Step 2. Set `flink.udf.jars` to point to the udf jar you build from your udf project

For example,

```
%flink.conf

flink.execution.jars /usr/lib/flink-udf-1.0-SNAPSHOT.jar
```

Zeppelin would scan this jar, find out all the udf classes and then register them automatically for you. 
The udf name is the class name. For example, here's the output of show functions after specifing the above udf jars in `flink.udf.jars`

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/flink_udf_jars.png">

By default, Zeppelin would scan all the classes in this jar, 
so it would be pretty slow if your jar is very big specially when your udf jar has other dependencies. 
So in this case we would recommend you to specify `flink.udf.jars.packages` to specify the package to scan,
this can reduce the number of classes to scan and make the udf detection much faster.


## How to use Hive

In order to use Hive in Flink, you have to make the following settings.

* Set `zeppelin.flink.enableHive` to be true
* Set `zeppelin.flink.hive.version` to be the hive version you are using.
* Set `HIVE_CONF_DIR` to be the location where `hive-site.xml` is located. Make sure hive metastore is started and you have configured `hive.metastore.uris` in `hive-site.xml`
* Copy the following dependencies to the lib folder of flink installation.
    * flink-connector-hive_2.11–*.jar
    * flink-hadoop-compatibility_2.11–*.jar
    * hive-exec-2.x.jar (for hive 1.x, you need to copy hive-exec-1.x.jar, hive-metastore-1.x.jar, libfb303–0.9.2.jar and libthrift-0.9.2.jar)


## Paragraph local properties

In the section of `Streaming Data Visualization`, we demonstrate the different visualization type via paragraph local properties: `type`. 
In this section, we will list and explain all the supported local properties in Flink interpreter.

<table class="table-configuration">
  <tr>
    <th>Property</th>
    <th>Default</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>type</td>
    <td></td>
    <td>Used in %flink.ssql to specify the streaming visualization type (single, update, append)</td>
  </tr>
  <tr>
    <td>refreshInterval</td>
    <td>3000</td>
    <td>Used in `%flink.ssql` to specify frontend refresh interval for streaming data visualization.</td>
  </tr>
  <tr>
    <td>template</td>
    <td>{0}</td>
    <td>Used in `%flink.ssql` to specify html template for `single` type of streaming data visualization, And you can use `{i}` as placeholder for the {i}th column of the result.</td>
  </tr>
  <tr>
    <td>parallelism</td>
    <td></td>
    <td>Used in %flink.ssql & %flink.bsql to specify the flink sql job parallelism</td>
  </tr>
  <tr>
    <td>maxParallelism</td>
    <td></td>
    <td>Used in %flink.ssql & %flink.bsql to specify the flink sql job max parallelism in case you want to change parallelism later. For more details, refer this [link](https://ci.apache.org/projects/flink/flink-docs-release-1.10/dev/parallel.html#setting-the-maximum-parallelism) </td>
  </tr>
  <tr>
    <td>savepointDir</td>
    <td></td>
    <td>If you specify it, then when you cancel your flink job in Zeppelin, it would also do savepoint and store state in this directory. And when you resume your job, it would resume from this savepoint.</td>
  </tr>
  <tr>
    <td>execution.savepoint.path</td>
    <td></td>
    <td>When you resume your job, it would resume from this savepoint path.</td>
  </tr>
  <tr>
    <td>resumeFromSavepoint</td>
    <td></td>
    <td>Resume flink job from savepoint if you specify savepointDir.</td>
  </tr>
  <tr>
    <td>resumeFromLatestCheckpoint</td>
    <td></td>
    <td>Resume flink job from latest checkpoint if you enable checkpoint.</td>
  </tr>
  <tr>
    <td>runAsOne</td>
    <td>false</td>
    <td>All the insert into sql will run in a single flink job if this is true.</td>
  </tr>
</table>

## PerJob Mode

The flink cluster launched in Zeppelin is session cluster, but you can simulate Flink's per job mode via this approach.
* Set `zeppelin.notebook.run_all.isolated` to be `true` in `zeppelin-site.xml`
* Run Flink note by clicking `Run all paragraphs` button in note menu.

In this way, when all the paragraphs are finished, the flink interpreter will be shutdown and its associated flink cluster will be shutdown as well.

## Tutorial Notes

Zeppelin is shipped with several Flink tutorial notes which may be helpful for you. You can check for more features in the tutorial notes.

## Community

[Join our community](http://zeppelin.apache.org/community.html) to discuss with others.



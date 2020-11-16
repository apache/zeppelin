---
layout: page
title: "Interpreter in Apache Zeppelin"
description: "This document explains the role of interpreters, interpreter groups and interpreter settings in Apache Zeppelin. The concept of Zeppelin interpreters allows any language or data-processing backend to be plugged into Zeppelin."
group: usage/interpreter 
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

# Interpreter in Apache Zeppelin

<div id="toc"></div>

## Overview

In this section, we will explain the role of interpreters, interpreter groups and interpreter settings in Zeppelin.
The concept of Zeppelin interpreters allows any language or data-processing backend to be plugged into Zeppelin.
Currently, Zeppelin supports many interpreters such as Scala (with Apache Spark), Python (with Apache Spark), Spark SQL, Hive, JDBC, Markdown, Shell and so on.

## What are Zeppelin Interpreters ?
A Zeppelin interpreter is a plug-in which enables Zeppelin users to use a specific language/data-processing-backend. For example, to use Scala code in Zeppelin, you would use the `%spark` interpreter.

When you click the ```+Create``` button on the interpreter page, the interpreter drop-down list box will show all the available interpreters on your server.

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/screenshots/interpreter_create.png" width="280px">

You can create multiple interpreters for the same engine with different interpreter setting. e.g. You can create `spark2` for Spark 2.x and create `spark1` for Spark 1.x.

For each paragraph you write in Zeppelin, you need to specify its interpreter first via `%interpreter_group.interpreter_name`. e.g. `%spark.pyspark`, `%spark.r`.

If you specify interpreter, you can also pass local properties to it (if it needs them).  This is done by providing a set of key/value pairs, separated by comma, inside the round brackets right after the interpreter name. If key or value contain characters like `=`, or `,`, then you can either escape them with `\` character, or wrap the whole value inside the double quotes For example:

```
%cassandra(outputFormat=cql, dateFormat="E, d MMM yy", timeFormat=E\, d MMM yy)
```

## What are the Interpreter Settings?
The interpreter settings are the configuration of a given interpreter on the Zeppelin server. For example, certain properties need to be set for the Apache Hive JDBC interpreter to connect to the Hive server.

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/screenshots/interpreter_setting.png" width="500px">

Properties are exported as environment variables on the system if the property name consists of upper-case characters, numbers or underscores ([A-Z_0-9]). Otherwise, the property is set as a common interpreter property. 
e.g. You can define `SPARK_HOME` and `HADOOP_CONF_DIR` in spark's interpreter setting, they are be passed to Spark interpreter process as environment variable which is used by Spark. 

You may use parameters from the context of the interpreter by adding #{contextParameterName} in the interpreter property value. The parameter can be of the following types: string, number, boolean.

### Context Parameters
<table class="table-configuration">
  <tr>
    <th>Name</th>
    <th>Type</th>
  </tr>
  <tr>
    <td>user</td>
    <td>string</td>
  </tr>
  <tr>
    <td>noteId</td>
    <td>string</td>
  </tr>
  <tr>
    <td>replName</td>
    <td>string</td>
  </tr>
  <tr>
    <td>className</td>
    <td>string</td>
  </tr>
</table>

If the context parameter is null, then it is replaced by an empty string. The following screenshot is one example where we make the user name as the property value of `default.user`.

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/screenshots/interpreter_setting_with_context_parameters.png" width="800px">


## What are Interpreter Groups ?
Every interpreter belongs to an **Interpreter Group**. Interpreter Groups are units of interpreters that run in one single JVM process and can be started/stopped together.
By default, every interpreter belongs to a separate group, but the group might contain more interpreters. For example, the Spark interpreter group includes Scala Spark, PySpark, IPySpark, SparkR and Spark SQL.

Technically, Zeppelin interpreters from the same group run within the same JVM. For more information about this, please consult [the documentation on writing interpreters](../development/writing_zeppelin_interpreter.html).

Each interpreter belongs to a single group and is registered together. All relevant properties are listed in the interpreter setting as in the below example.

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/screenshots/interpreter_setting_spark.png" width="500px">


## Interpreter Binding Mode

In the Interpreter Settings, one can choose one of the `shared`, `scoped`, or `isolated` interpreter binding modes.
In `shared` mode, every note/user using this interpreter will share a single interpreter instance. 
`scoped` and `isolated` mode can be used under 2 dimensions: `per user` or `per note`.
e.g. In `scoped per note` mode, each note will create a new interpreter instance in the same interpreter process. In `isolated per note` mode, each note will create a new interpreter process.

For more information, please consult [Interpreter Binding Mode](./interpreter_binding_mode.html).

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/screenshots/interpreter_persession.png" width="400px">
 
## Interpreter Lifecycle Management

Before 0.8.0, Zeppelin doesn't have lifecycle management for interpreters. Users had to shut down interpreters explicitly via the UI. Starting from 0.8.0, Zeppelin provides a new interface
`LifecycleManager` to control the lifecycle of interpreters. For now, there are two implementations: `NullLifecycleManager` and `TimeoutLifecycleManager`. 

`NullLifecycleManager` will do nothing, i.e., the user needs to control the lifecycle of interpreter by themselves as before. `TimeoutLifecycleManager` will shut down interpreters after an interpreter remains idle for a while. By default, the idle threshold is 1 hour.
Users can change this threshold via the `zeppelin.interpreter.lifecyclemanager.timeout.threshold` setting. `NullLifecycleManager` is the default lifecycle manager, and users can change it via `zeppelin.interpreter.lifecyclemanager.class`.


## Inline Generic Configuration

Zeppelin's interpreter setting is shared by all users and notes, if you want to have different settings, you have to create a new interpreter, e.g. you can create `spark_jar1` for running Spark with dependency `jar1` and `spark_jar2` for running Spark with dependency `jar2`.
This approach works, but is not convenient. Inline generic configuration can provide more fine-grained control on interpreter settings and more flexibility. 

`ConfInterpreter` is a generic interpreter that can be used by any interpreter. You can use it just like defining a java property file.
It can be used to make custom settings for any interpreter. However, `ConfInterpreter` needs to run before that interpreter process is launched. When that interpreter process is launched is determined by the interpreter binding mode setting.
So users need to understand the [interpreter binding mode setting](../usage/interpreter/interpreter_bindings_mode.html) of Zeppelin and be aware of when the interpreter process is launched. E.g., if we set the Spark interpreter setting as isolated per note, then under this setting, each note will launch one interpreter process. 
In this scenario, users need to put `ConfInterpreter` as the first paragraph as in the below example. Otherwise, the customized setting cannot be applied (actually it would report `ERROR`).

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/screenshots/conf_interpreter.png" width="600px">


## Precode

Snippet of code (language of interpreter) that executes after initialization of the interpreter depends on [Binding mode](#interpreter-binding-mode). To configure, add a parameter with the class of the interpreter (`zeppelin.<ClassName>.precode`) except JDBCInterpreter ([JDBC precode](../../interpreter/jdbc.html#usage-precode)). 

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/screenshots/interpreter_precode.png" width="800px">

## Credential Injection

Credentials from the credential manager can be injected into Notebooks. Credential injection works by replacing the following patterns in Notebooks with matching credentials for the Credential Manager: `{CREDENTIAL_ENTITY.user}` and `{CREDENTIAL_ENTITY.password}`. However, credential injection must be enabled per Interpreter, by adding a boolean `injectCredentials` setting in the Interpreters configuration. Injected passwords are removed from Notebook output to prevent accidentally leaking passwords.

**Credential Injection Setting**
<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/screenshots/credential_injection_setting.png" width="500px">

**Credential Entry Example**
<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/screenshots/credential_entry.png" width="500px">

**Credential Injection Example**

```scala
val password = "{SOME_CREDENTIAL_ENTITY.password}"

val username = "{SOME_CREDENTIAL_ENTITY.user}"
```

## Interpreter Process Recovery (Experimental)

Before 0.8.0, shutting down Zeppelin also meant to shutdown all the running interpreter processes. Usually, an administrator will shutdown the Zeppelin server for maintenance or upgrades, but would not want to shut down the running interpreter processes.
In such cases, interpreter process recovery is necessary. Starting from 0.8.0, users can enable interpreter process recovery via the setting `zeppelin.recovery.storage.class` as 
`org.apache.zeppelin.interpreter.recovery.FileSystemRecoveryStorage` or other implementations if available in the future. By default it is `org.apache.zeppelin.interpreter.recovery.NullRecoveryStorage`,
 which means recovery is not enabled. `zeppelin.recovery.dir` is used for specify where to store the recovery metadata. 
Enabling recovery means shutting down Zeppelin would not terminate interpreter processes, and when Zeppelin is restarted, it would try to reconnect to the existing running interpreter processes. If you want to kill all the interpreter processes after terminating Zeppelin even when recovery is enabled, you can run `bin/stop-interpreter.sh`.
 
In 0.8.x, Zeppelin server would reconnect to the running interpreter process only when you run paragraph again, but it won't recover the running paragraph. E.g. if you restart zeppelin server when some paragraph is still running,
then when you restart Zeppelin, although the interpreter process is still running, you won't see the paragraph is running in frontend. In 0.9.x, we fix it by recovering the running paragraphs.
Here's one screenshot of how one running paragraph of flink interpreter works.


<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/flink_recovery.gif" width="800px">

## Choose Interpreters

By default, Zeppelin will register and display all the interpreters under folder `$ZEPPELIN_HOME/interpreters`.
But you can configure property `zeppelin.interpreter.include` to specify what interpreters you want to include or `zeppelin.interpreter.exclude` to specify what interpreters you want to exclude.
Only one of them can be specified, you can not specify them together.

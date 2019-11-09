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
Currently, Zeppelin supports many interpreters such as Scala (with Apache Spark), Python (with Apache Spark), Spark SQL, JDBC, Markdown, Shell and so on.

## What are Zeppelin interpreters?
A Zeppelin interpreter is a plug-in which enables Zeppelin users to use a specific language/data-processing-backend. For example, to use Scala code in Zeppelin, you would use the `%spark` interpreter.

When you click the ```+Create``` button on the interpreter page, the interpreter drop-down list box will show all the available interpreters on your server.

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/screenshots/interpreter_create.png" width="280px">

## What are the interpreter settings?
The interpreter settings are the configuration of a given interpreter on the Zeppelin server. For example, certain properties need to be set for the Apache Hive JDBC interpreter to connect to the Hive server.

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/screenshots/interpreter_setting.png" width="500px">

Properties are exported as environment variables on the system if the property name consists of upper-case characters, numbers or underscores ([A-Z_0-9]). Otherwise, the property is set as a JVM property. 

You may use parameters from the context of the interpreter by adding #{contextParameterName} in the value. The parameter can be of the following types: string, number, boolean.

###### Context parameters
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

If the context parameter is null, then it is replaced by an empty string.

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/screenshots/interpreter_setting_with_context_parameters.png" width="800px">

<br>
Each notebook can be bound to multiple Interpreter Settings using the setting icon in the upper right corner of the notebook.

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/screenshots/interpreter_binding.png" width="800px">



## What are interpreter groups?
Every interpreter belongs to an **Interpreter Group**. Interpreter Groups are units of interpreters that can be started/stopped together.
By default, every interpreter belongs to a separate group, but the group might contain more interpreters. For example, the Spark interpreter group includes Spark support, pySpark, Spark SQL and the dependency loader.

Technically, Zeppelin interpreters from the same group run within the same JVM. For more information about this, please consult [the documentation on writing interpreters](../development/writing_zeppelin_interpreter.html).

Each interpreter belongs to a single group and is registered together. All relevant properties are listed in the interpreter setting as in the below example.

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/screenshots/interpreter_setting_spark.png" width="500px">


## Interpreter binding mode

In the Interpreter Settings, one can choose one of the 'shared', 'scoped', or 'isolated' interpreter binding modes.
In 'shared' mode, every notebook bound to the Interpreter Setting will share a single Interpreter instance. In 'scoped' mode, each notebook will create a new interpreter instance in the same interpreter process. In 'isolated' mode, each notebook will create new a interpreter process.

For more information, please consult [Interpreter Binding Mode](./interpreter_binding_mode.html).

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/screenshots/interpreter_persession.png" width="400px">


## Connecting to the existing remote interpreter

Zeppelin users can start interpreter threads embedded in their service. This provides users with the flexibility of starting interpreters on a remote host. To start an interpreter along with your service you have to create an instance of ``RemoteInterpreterServer`` and start it as follows:

```java
RemoteInterpreterServer interpreter=new RemoteInterpreterServer(3678); 
// Here, 3678 is the port on which interpreter will listen.    
interpreter.start();

```

The above code will start an interpreter thread inside your process. Once the interpreter has started, you can configure Zeppelin to connect to RemoteInterpreter by checking the **Connect to existing process** checkbox and then provide the **Host** and **Port** on which interpreter process is listening, as shown in the image below:

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/screenshots/existing_interpreter.png" width="450px">

## Precode

Snippet of code (language of interpreter) that executes after initialization of the interpreter depends on [Binding mode](#interpreter-binding-mode). To configure, add a parameter with the class of the interpreter (`zeppelin.<ClassName>.precode`) except JDBCInterpreter ([JDBC precode](../../interpreter/jdbc.html#usage-precode)). 

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/screenshots/interpreter_precode.png" width="800px">

 
## Interpreter Lifecycle Management

Before 0.8.0, Zeppelin didn't have lifecycle management for interpreters. Users had to shut down interpreters explicitly via the UI. Starting from 0.8.0, Zeppelin provides a new interface
`LifecycleManager` to control the lifecycle of interpreters. For now, there are two implementations: `NullLifecycleManager` and `TimeoutLifecycleManager`, which is the default. 

`NullLifecycleManager` will do nothing, i.e., the user needs to control the lifecycle of interpreter by themselves as before. `TimeoutLifecycleManager` will shut down interpreters after an interpreter remains idle for a while. By default, the idle threshold is 1 hour.
Users can change this threshold via the `zeppelin.interpreter.lifecyclemanager.timeout.threshold` setting. `TimeoutLifecycleManager` is the default lifecycle manager, and users can change it via `zeppelin.interpreter.lifecyclemanager.class`.


## Inline Generic ConfInterpreter

Zeppelin's interpreter setting is shared by all users and notes, if you want to have different settings, you have to create a new interpreter, e.g. you can create `spark_jar1` for running Spark with dependency jar1 and `spark_jar2` for running Spark with dependency jar2.
This approach works, but is not particularly convenient. `ConfInterpreter` can provide more fine-grained control on interpreter settings and more flexibility. 

`ConfInterpreter` is a generic interpreter that can be used by any interpreter. The input format should be the property file format.
It can be used to make custom settings for any interpreter. However, `ConfInterpreter` needs to be run before that interpreter process is launched. When that interpreter process is launched is determined by the interpreter mode setting.
So users need to understand the [interpreter mode setting](../usage/interpreter/interpreter_bindings_mode.html) of Zeppelin and be aware of when the interpreter process is launched. E.g., if we set the Spark interpreter setting as isolated per note, then, under this setting, each note will launch one interpreter process. 
In this scenario, users need to put `ConfInterpreter` as the first paragraph as in the below example. Otherwise, the customized setting cannot be applied (actually it would report ERROR).
<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/screenshots/conf_interpreter.png" width="700px">


## Interpreter Process Recovery

Before 0.8.0, shutting down Zeppelin also meant to shutdown all the running interpreter processes. Usually, an administrator will shutdown the Zeppelin server for maintenance or upgrades, but would not want to shut down the running interpreter processes.
In such cases, interpreter process recovery is necessary. Starting from 0.8.0, users can enable interpreter process recovery via the setting `zeppelin.recovery.storage.class` as 
`org.apache.zeppelin.interpreter.recovery.FileSystemRecoveryStorage` or other implementations if available in the future. By default it is `org.apache.zeppelin.interpreter.recovery.NullRecoveryStorage`,
 which means recovery is not enabled. Enabling recovery means shutting down Zeppelin would not terminate interpreter processes, and when Zeppelin is restarted, it would try to reconnect to the existing running interpreter processes. If you want to kill all the interpreter processes after terminating Zeppelin even when recovery is enabled, you can run `bin/stop-interpreter.sh` 

## Credential Injection

Credentials from the credential manager can be injected into Notebooks. Credential injection works by replacing the following patterns in Notebooks with matching credentials for the Credential Manager: `{user.CREDENTIAL_ENTITY}` and `{password.CREDENTIAL_ENTITY}`. However, credential injection must be enabled per Interpreter, by adding a boolean `injectCredentials` setting in the Interpreters configuration. Injected passwords are removed from Notebook output to prevent accidentally leaking passwords.

**Credential Injection Setting**
<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/screenshots/credential_injection_setting.png" width="500px">

**Credential Entry Example**
<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/screenshots/credential_entry.png" width="500px">

**Credential Injection Example**
```
val password = "{password.SOME_CREDENTIAL_ENTITY}"
val username = "{user.SOME_CREDENTIAL_ENTITY}"
```

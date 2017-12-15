---
layout: page
title: "Interpreter in Apache Zeppelin"
description: "This document explains about the role of interpreters, interpreters group and interpreter settings in Apache Zeppelin. The concept of Zeppelin interpreter allows any language/data-processing-backend to be plugged into Zeppelin."
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

In this section, we will explain about the role of interpreters, interpreters group and interpreter settings in Zeppelin.
The concept of Zeppelin interpreter allows any language/data-processing-backend to be plugged into Zeppelin.
Currently, Zeppelin supports many interpreters such as Scala ( with Apache Spark ), Python ( with Apache Spark ), Spark SQL, JDBC, Markdown, Shell and so on.

## What is Zeppelin interpreter?
Zeppelin Interpreter is a plug-in which enables Zeppelin users to use a specific language/data-processing-backend. For example, to use Scala code in Zeppelin, you need `%spark` interpreter.

When you click the ```+Create``` button in the interpreter page, the interpreter drop-down list box will show all the available interpreters on your server.

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/screenshots/interpreter_create.png" width="280px">

## What is interpreter setting?
Zeppelin interpreter setting is the configuration of a given interpreter on Zeppelin server. For example, the properties are required for hive JDBC interpreter to connect to the Hive server.

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/screenshots/interpreter_setting.png" width="500px">

Properties are exported as environment variables when property name is consisted of upper characters, numbers and underscore ([A-Z_0-9]). Otherwise set properties as JVM property. 

You may use parameters from the context of interpreter by add #{contextParameterName} in value, parameter can be of the following types: string, number, boolean.

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

If context parameter is null then replaced by empty string.

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/screenshots/interpreter_setting_with_context_parameters.png" width="800px">

<br>
Each notebook can be bound to multiple Interpreter Settings using setting icon on upper right corner of the notebook.

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/screenshots/interpreter_binding.png" width="800px">



## What is interpreter group?
Every Interpreter is belonged to an **Interpreter Group**. Interpreter Group is a unit of start/stop interpreter.
By default, every interpreter is belonged to a single group, but the group might contain more interpreters. For example, Spark interpreter group is including Spark support, pySpark, Spark SQL and the dependency loader.

Technically, Zeppelin interpreters from the same group are running in the same JVM. For more information about this, please checkout [here](../development/writing_zeppelin_interpreter.html).

Each interpreters is belonged to a single group and registered together. All of their properties are listed in the interpreter setting like below image.

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/screenshots/interpreter_setting_spark.png" width="500px">


## Interpreter binding mode

Each Interpreter Setting can choose one of 'shared', 'scoped', 'isolated' interpreter binding mode.
In 'shared' mode, every notebook bound to the Interpreter Setting will share the single Interpreter instance. In 'scoped' mode, each notebook will create new Interpreter instance in the same interpreter process. In 'isolated' mode, each notebook will create new Interpreter process.

For more information, check [Interpreter Binding Mode](./interpreter_binding_mode.html).

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/screenshots/interpreter_persession.png" width="400px">


## Connecting to the existing remote interpreter

Zeppelin users can start interpreter thread embedded in their service. This will provide flexibility to user to start interpreter on remote host. To start interpreter along with your service you have to create an instance of ``RemoteInterpreterServer`` and start it as follows:

```
RemoteInterpreterServer interpreter=new RemoteInterpreterServer(3678); 
// Here, 3678 is the port on which interpreter will listen.    
interpreter.start()  

```

The above code will start interpreter thread inside your process. Once the interpreter is started you can configure zeppelin to connect to RemoteInterpreter by checking **Connect to existing process** checkbox and then provide **Host** and **Port** on which interpreter process is listening as shown in the image below:

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/screenshots/existing_interpreter.png" width="450px">

## Precode

Snippet of code (language of interpreter) that executes after initialization of the interpreter depends on [Binding mode](#interpreter-binding-mode). To configure add parameter with class of interpreter (`zeppelin.<ClassName>.precode`) except JDBCInterpreter ([JDBC precode](../../interpreter/jdbc.html#usage-precode)). 

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/screenshots/interpreter_precode.png" width="800px">

 
## Interpreter Lifecycle Management

Before 0.8.0, Zeppelin don't have lifecycle management on interpreter. User have to shutdown interpreters explicitly via UI. Starting from 0.8.0, Zeppelin provides a new interface
`LifecycleManager` to control the lifecycle of interpreters. For now, there're 2 implementations: `NullLifecycleManager` and `TimeoutLifecycleManager` which is default. 

`NullLifecycleManager` will do nothing,
user need to control the lifecycle of interpreter by themselves as before. `TimeoutLifecycleManager` will shutdown interpreters after interpreter idle for a while. By default, the idle threshold is 1 hour.
User can change it via `zeppelin.interpreter.lifecyclemanager.timeout.threshold`. `TimeoutLifecycleManager` is the default lifecycle manager, user can change it via `zeppelin.interpreter.lifecyclemanager.class`.

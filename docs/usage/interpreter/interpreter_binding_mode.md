---
layout: page
title: "Interpreter Binding Mode"
description: ""
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

# Interpreter Binding Mode 

<div id="toc"></div>

## Overview

<center><img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/interpreter_binding_per_note_user.png" height="70%" width="70%"></center>
<center><img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/interpreter_binding_scoped_isolated.png" height="70%" width="70%"></center>

<br/>

Interpreter Process is a JVM process that communicates with Zeppelin daemon using thrift. 
Each interpreter process has a single interpreter group, and this interpreter group can have one or more instances of an interpreter.
(See [here](../../development/writing_zeppelin_interpreter.html) to understand more about its internal structure.) 

Zeppelin provides 3 different modes to run interpreter process: **shared**, **scoped** and **isolated**.   
Also, the user can specify the scope of these modes: **per** user or **per note**.
These 3 modes give flexibility to fit Zeppelin into any type of use cases.

In this documentation, we mainly discuss the **per note** scope in combination with the **shared**, **scoped** and **isolated** modes.

## Shared Mode

<div class="text-center">
    <img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/interpreter_binding_mode-shared.png" height="40%" width="40%">
</div>
<br/>

In **Shared** mode, single JVM process and a single session serves all notes. As a result, `note A` can access variables (e.g python, scala, ..) directly created from other notes.. 

## Scoped Mode

<div class="text-center">
    <img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/interpreter_binding_mode-scoped.png">
</div>
<br/>

In **Scoped** mode, Zeppelin still runs a single interpreter JVM process but, in the case of per note scope, each note runs in its own dedicated session. 
(Note it is still possible to share objects between these notes via [ResourcePool](../../interpreter/spark.html#object-exchange)) 

## Isolated Mode

<div class="text-center">
    <img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/interpreter_binding_mode-isolated.png">
</div>
<br/>

**Isolated** mode runs a separate interpreter process for each note in the case of **per note** scope. 
So, each note has an absolutely isolated session. (But it is still possible to share objects via [ResourcePool](../../interpreter/spark.html#object-exchange)) 

## Which mode should I use?

<br/>

Mode | Each notebook...	| Benefits | Disadvantages | Sharing objects
--- | --- | --- | --- | ---
**shared** |  Shares a single session in a single interpreter process (JVM) |  Low resource utilization and it's easy to share data between notebooks |  All notebooks are affected if the interpreter process dies | Can share directly
**scoped** | Has its own session in the same interpreter process (JVM) | Less resource utilization than isolated mode |  All notebooks are affected if the interpreter process dies | Can't share directly, but it's possible to share objects via [ResourcePool](../../interpreter/spark.html#object-exchange)) 
**isolated** | Has its own Interpreter Process | One notebook is not affected directly by other notebooks (**per note**) | Can't share data between notebooks easily (**per note**) | Can't share directly, but it's possible to share objects via [ResourcePool](../../interpreter/spark.html#object-exchange)) 

In the case of the **per user** scope (available in a multi-user environment), Zeppelin manages interpreter sessions on a per user basis rather than a per note basis. For example:
 
- In **scoped + per user** mode, `User A`'s notes **might** be affected by `User B`'s notes. (e.g JVM dies, ...) Because all notes are running on the same JVM
- On the other hand, **isolated + per user** mode, `User A`'s notes will not be affected by others' notes which running on separated JVMs

<br/>

Each Interpreter implementation may have different characteristics depending on the back end system that they integrate. And 3 interpreter modes can be used differently.
Let’s take a look how Spark Interpreter implementation uses these 3 interpreter modes with **per note** scope, as an example.
Spark Interpreter implementation includes 4 different interpreters in the group: Spark, SparkSQL, Pyspark and SparkR. 
SparkInterpreter instance embeds Scala REPL for interactive Spark API execution.

<br/>

<div class="text-center">
    <img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/interpreter_binding_mode-example-spark-shared.png" height="40%" width="40%">
</div>
<br/>

In Shared mode, a SparkContext and a Scala REPL is being shared among all interpreters in the group. 
So every note will be sharing single SparkContext and single Scala REPL. 
In this mode, if `Note A` defines variable ‘a’ then `Note B` not only able to read variable ‘a’ but also able to override the variable.

<div class="text-center">
    <img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/interpreter_binding_mode-example-spark-scoped.png">
</div>
<br/>

In Scoped mode, each note has its own Scala REPL. 
So variable defined in a note can not be read or overridden in another note. 
However, a single SparkContext still serves all the sessions.
And all the jobs are submitted to this SparkContext and the fair scheduler schedules the jobs.
This could be useful when user does not want to share Scala session, but want to keep single Spark application and leverage its fair scheduler.

In Isolated mode, each note has its own SparkContext and Scala REPL.

<div class="text-center">
    <img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/interpreter_binding_mode-example-spark-isolated.png">
</div>
<br/>

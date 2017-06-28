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

Interpreter is a JVM process that communicates to Zeppelin daemon using thrift. 
Each Interpreter process can have Interpreter Groups, and each interpreter instance belongs to this Interpreter Group.
(See [here](../../development/writing_zeppelin_interpreter.html) to understand more about its internal structure.) 

Zeppelin provides 3 different modes to run interpreter process: **shared**, **scoped** and **isolated**.   
Also, user can specify the scope of this mode as well: **per user** or **per note**.  
These 3 modes give flexibility to fit Zeppelin into any type of use cases.

In this documentation, we mainly discuss the combination of **per note** mode with **shared**, **scoped** and **isolated** modes for explanation. 

## Shared Mode

<div class="text-center">
    <img src="/assets/themes/zeppelin/img/docs-img/interpreter_binding_mode-shared.png">
</div>
<br/>

In **Shared** mode, single JVM process and single Interpreter Group serves all notes.

## Scoped Mode

<div class="text-center">
    <img src="/assets/themes/zeppelin/img/docs-img/interpreter_binding_mode-scoped.png">
</div>
<br/>

In Scoped mode, Zeppelin still runs single interpreter JVM process but multiple Interpreter Group serve each note. (in case of **per note**) 
So, each note have their own dedicated session but still it’s possible to share objects between different Interpreter Groups while they’re in the same JVM process.

## Isolated Mode

<div class="text-center">
    <img src="/assets/themes/zeppelin/img/docs-img/interpreter_binding_mode-shared.png">
</div>
<br/>

**Isolated** mode runs separate interpreter process for each note. (in case of **per note**) So, each note have absolutely isolated session.

## Which mode should I use?

<br/>

Mode | Each notebook...	| Benefits | Disadvantages
--- | --- | --- | ---
**shared** | Shares a single Interpreter Group in a single Interpreter Process (JVM) | Low resource utilization and Easy to share data between notebooks | All notebooks are affected if Interpreter Process dies
**scoped** | Has its own Interpreter Group in the same Interpreter Process (JVM) | Less resource utilization than isolated mode | All notebooks are affected if Interpreter Process dies
**isolated** | Has its own Interpreter Process | One notebook not affected directly by other notebooks (**per note**) | Can't share data between notebooks easily (**per note**)

<br/>

Each Interpreter implementation may have different characteristics depending on the back end system that they integrate. And 3 interpreter modes can be used differently.
Let’s take a look how Spark Interpreter implementation uses these 3 interpreter modes, as an example. 
Spark Interpreter implementation includes 4 different interpreters in the group: Spark, SparkSQL, Pyspark and SparkR. 
SparkInterpreter instance embeds Scala REPL for interactive Spark API execution.

<br/>

In Shared mode, a SparkContext and a Scala REPL is being shared among all interpreters in the group. 
So every note will be sharing single SparkContext and single Scala REPL. 
In this mode, if `Note A` defines variable ‘a’ then `Note B` not only able to read variable ‘a’ but also able to override the variable.

<div class="text-center">
    <img src="/assets/themes/zeppelin/img/docs-img/interpreter_binding_mode-example-spark-shared.png">
</div>
<br/>

In Scoped mode, each note has its own Scala REPL. 
So variable defined in a note can not be read or overridden in another note. 
However, still single SparkContext serves all the Interpreter Groups. 
And all the jobs are submitted to this SparkContext and fair scheduler schedules the job. 
This could be useful when user does not want to share Scala session, but want to keep single Spark application and leverage its fair scheduler.

<div class="text-center">
    <img src="/assets/themes/zeppelin/img/docs-img/interpreter_binding_mode-example-spark-scoped.png">
</div>
<br/>

In Isolated mode, each note has its own SparkContext and Scala REPL.

<div class="text-center">
    <img src="/assets/themes/zeppelin/img/docs-img/interpreter_binding_mode-example-spark-isolated.png">
</div>
<br/>
